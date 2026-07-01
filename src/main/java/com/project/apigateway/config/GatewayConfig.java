package com.project.apigateway.config;

import com.project.apigateway.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {

        return builder.routes()

                .route("user-service", route -> route

                        .path("/api/auth/**",
                                "/api/users/**")

                        .uri("http://localhost:8081"))

                .route("log-service", route -> route

                        .path("/api/v1/logs/**")

                        .filters(filter ->
                                filter.filter(jwtAuthenticationFilter))

                        .uri("http://localhost:8082"))

                .build();
    }
}