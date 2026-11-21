package com.project.apigateway.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.project.apigateway.util.JwtUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String APP_NAME_HEADER = "X-Application-Name";
    private static final int MIN_INTERNAL_KEY_LENGTH = 16;

    private final JwtUtil jwtUtil;
    private final String internalApiKey;
    private final WebClient webClient;


    public JwtAuthenticationWebFilter(
            JwtUtil jwtUtil,
            @Value("${internal.api-key:}") String internalApiKey,
            @Value("${gateway.log-service-url:http://localhost:8082}") String logServiceUrl) {
        this.jwtUtil = jwtUtil;
        this.internalApiKey = (internalApiKey != null) ? internalApiKey.trim() : "";
        this.webClient = WebClient.builder()
                .baseUrl(logServiceUrl)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Strip caller-supplied identity headers at the gateway boundary to eliminate header impersonation
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove(USER_EMAIL_HEADER);
                            headers.remove(APP_NAME_HEADER);
                            headers.remove("X-User-Id");
                            headers.remove("X-Organization-Id");
                            headers.remove("X-Application-Id");
                            headers.remove("X-Internal-Api-Key");
                        })
                        .build())
                .build();

        var headers = sanitizedExchange.getRequest().getHeaders();
        List<String> appKeys = headers.get("X-API-Key");
        if (appKeys != null) {
            String route = sanitizedExchange.getRequest().getPath().value();
            if (appKeys.size() != 1 || headers.containsKey(HttpHeaders.AUTHORIZATION)
                    || !"POST".equals(sanitizedExchange.getRequest().getMethod().name())
                    || !(route.equals("/api/v1/logs") || route.equals("/api/v1/logs/batch")
                    || route.equals("/api/v2/logs/batch"))) {
                return chain.filter(sanitizedExchange);
            }
            return handleApiKeyAuthentication(appKeys.getFirst(), sanitizedExchange, chain);
        }

        return handleJwtAuthentication(sanitizedExchange, chain);
    }

    private boolean isInternalApiKeyValid(String candidateKey) {
        if (internalApiKey.isEmpty() || internalApiKey.length() < MIN_INTERNAL_KEY_LENGTH) {
            // Fail closed: internal master key authentication is unconfigured or insufficiently secure
            return false;
        }
        if (candidateKey == null || candidateKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                candidateKey.getBytes(StandardCharsets.UTF_8),
                internalApiKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Mono<Void> handleApiKeyAuthentication(String apiKey, ServerWebExchange exchange, WebFilterChain chain) {
        if (internalApiKey.length() < MIN_INTERNAL_KEY_LENGTH || isInternalApiKeyValid(apiKey)
                || apiKey.length() < 16 || apiKey.length() > 160) {
            return chain.filter(exchange);
        }
        return webClient.get().uri("/api/v1/integrations/verify-key")
                .header("X-Internal-Api-Key", internalApiKey)
                .header("X-API-Key", apiKey)
                .retrieve()
                .bodyToMono(ApiKeyVerificationDto.class)
                .timeout(Duration.ofSeconds(3))
                .map(ApiKeyVerificationDto::isValid)
                .defaultIfEmpty(false)
                .onErrorReturn(false)
                .flatMap(valid -> valid
                        ? authenticateAndContinue(exchange, chain, "integration@devinsight.io", null, "ROLE_APPLICATION")
                        : chain.filter(exchange));
    }

    private Mono<Void> authenticateAndContinue(
            ServerWebExchange exchange,
            WebFilterChain chain,
            String email,
            String appName,
            String role) {

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority(role))
        );

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.set(USER_EMAIL_HEADER, email);
                            if (appName != null && !appName.isBlank()) {
                                headers.set(APP_NAME_HEADER, appName);
                            } else {
                                headers.remove(APP_NAME_HEADER);
                            }
                        })
                        .build())
                .build();

        return chain.filter(mutatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private Mono<Void> handleJwtAuthentication(ServerWebExchange exchange, WebFilterChain chain) {
        List<String> headers = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        String header = headers != null && headers.size() == 1 ? headers.getFirst() : null;

        if (header == null || !header.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = header.substring(7);

        if (!jwtUtil.isTokenValid(token)) {
            return chain.filter(exchange);
        }

        String email = jwtUtil.extractEmail(token);
        String role = jwtUtil.extractRole(token);
        if (role == null || role.isBlank()) {
            role = "ROLE_DEVELOPER";
        } else if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        return authenticateAndContinue(exchange, chain, email, null, role);
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

}
