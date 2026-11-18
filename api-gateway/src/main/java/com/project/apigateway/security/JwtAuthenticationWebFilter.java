package com.project.apigateway.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.project.apigateway.util.JwtUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String APP_NAME_HEADER = "X-Application-Name";
    private static final long CACHE_TTL_MS = 300_000L; // 5 minutes cache for valid keys
    private static final long NEGATIVE_CACHE_TTL_MS = 30_000L; // 30 seconds cache for invalid keys

    private final JwtUtil jwtUtil;
    private final String internalApiKey;
    private final WebClient webClient;

    // High-performance thread-safe cache to avoid hitting log-service on every ingestion packet
    private final Map<String, CachedApiKey> apiKeyCache = new ConcurrentHashMap<>();

    public JwtAuthenticationWebFilter(
            JwtUtil jwtUtil,
            @Value("${internal.api-key:devinsight_internal_ml_key_secret_12345}") String internalApiKey,
            @Value("${gateway.log-service-url:http://localhost:8082}") String logServiceUrl) {
        this.jwtUtil = jwtUtil;
        this.internalApiKey = internalApiKey;
        this.webClient = WebClient.builder()
                .baseUrl(logServiceUrl)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");

        if (apiKey != null && !apiKey.isBlank()) {
            return handleApiKeyAuthentication(apiKey, exchange, chain);
        }

        return handleJwtAuthentication(exchange, chain);
    }

    private Mono<Void> handleApiKeyAuthentication(String apiKey, ServerWebExchange exchange, WebFilterChain chain) {
        // Fast-path 1: Internal Master / Service Key
        if (apiKey.equals(internalApiKey)) {
            String clientEmail = exchange.getRequest().getHeaders().getFirst(USER_EMAIL_HEADER);
            String email = (clientEmail != null && !clientEmail.isBlank()) ? clientEmail : "system@devinsight.io";
            return authenticateAndContinue(exchange, chain, email, "internal-service");
        }

        // Fast-path 2: In-Memory TTL Cache for per-app dynamic API keys (e.g. dvi_...)
        long now = System.currentTimeMillis();
        CachedApiKey cached = apiKeyCache.get(apiKey);
        if (cached != null && cached.getExpiresAt() > now) {
            if (cached.isValid()) {
                return authenticateAndContinue(exchange, chain, cached.getUserEmail(), cached.getApplicationName());
            } else {
                // Key was recently verified as invalid; proceed unauthenticated so Security will reject
                return chain.filter(exchange);
            }
        }

        // Slow-path: Query log-service to verify dynamic integration key via secure header
        return webClient.get()
                .uri("/api/v1/integrations/verify-key")
                .header("X-API-Key", apiKey)
                .retrieve()
                .bodyToMono(ApiKeyVerificationDto.class)
                .timeout(Duration.ofSeconds(3))
                .flatMap(response -> {
                    if (response != null && response.isValid()) {
                        String email = (response.getUserEmail() != null && !response.getUserEmail().isBlank())
                                ? response.getUserEmail()
                                : "integration@devinsight.io";
                        apiKeyCache.put(apiKey, new CachedApiKey(true, email, response.getApplicationName(), now + CACHE_TTL_MS));
                        return authenticateAndContinue(exchange, chain, email, response.getApplicationName());
                    } else {
                        apiKeyCache.put(apiKey, new CachedApiKey(false, null, null, now + NEGATIVE_CACHE_TTL_MS));
                        return chain.filter(exchange);
                    }
                })
                .onErrorResume(ex -> {
                    log.warn("Failed to verify API key with log-service: {}", ex.getMessage());
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> authenticateAndContinue(ServerWebExchange exchange, WebFilterChain chain, String email, String appName) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))
        );

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove(USER_EMAIL_HEADER);
                            headers.add(USER_EMAIL_HEADER, email);
                            if (appName != null && !appName.isBlank()) {
                                headers.remove(APP_NAME_HEADER);
                                headers.add(APP_NAME_HEADER, appName);
                            }
                        })
                        .build())
                .build();

        return chain.filter(mutatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private Mono<Void> handleJwtAuthentication(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = header.substring(7);

        if (!jwtUtil.isTokenValid(token)) {
            return chain.filter(exchange);
        }

        String email = jwtUtil.extractEmail(token);
        String role = jwtUtil.extractRole(token);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove(USER_EMAIL_HEADER);
                            headers.add(USER_EMAIL_HEADER, email);
                        })
                        .build())
                .build();

        return chain.filter(mutatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiKeyVerificationDto {
        private boolean valid;
        private String applicationName;
        private String userEmail;
    }

    @Data
    @AllArgsConstructor
    private static class CachedApiKey {
        private final boolean valid;
        private final String userEmail;
        private final String applicationName;
        private final long expiresAt;
    }
}
