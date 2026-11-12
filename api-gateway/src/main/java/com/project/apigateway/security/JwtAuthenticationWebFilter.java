package com.project.apigateway.security;

import com.project.apigateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final String USER_EMAIL_HEADER =
            "X-User-Email";

    private final JwtUtil jwtUtil;
    private final String internalApiKey;

    public JwtAuthenticationWebFilter(
            JwtUtil jwtUtil,
            @Value("${internal.api-key:devinsight_internal_ml_key_secret_12345}") String internalApiKey) {
        this.jwtUtil = jwtUtil;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        String apiKey = exchange.getRequest()
                .getHeaders()
                .getFirst("X-API-Key");

        if (apiKey != null && !apiKey.isBlank() && apiKey.equals(internalApiKey)) {
            String clientEmail = exchange.getRequest().getHeaders().getFirst(USER_EMAIL_HEADER);
            String email = (clientEmail != null && !clientEmail.isBlank()) ? clientEmail : "system@devinsight.io";

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))
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

        String header = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (header == null ||
                !header.startsWith("Bearer ")) {

            return chain.filter(exchange);
        }

        String token = header.substring(7);

        if (!jwtUtil.isTokenValid(token)) {
            return chain.filter(exchange);
        }

        String email = jwtUtil.extractEmail(token);
        String role = jwtUtil.extractRole(token);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(role)
                        )
                );

        /*
         * Remove any client-supplied X-User-Email header
         * before adding the trusted value extracted from JWT.
         */
        ServerWebExchange mutatedExchange =
                exchange.mutate()
                        .request(
                                exchange.getRequest()
                                        .mutate()
                                        .headers(headers -> {
                                            headers.remove(
                                                    USER_EMAIL_HEADER
                                            );

                                            headers.add(
                                                    USER_EMAIL_HEADER,
                                                    email
                                            );
                                        })
                                        .build()
                        )
                        .build();

        return chain
                .filter(mutatedExchange)
                .contextWrite(
                        ReactiveSecurityContextHolder
                                .withAuthentication(authentication)
                );
    }
}