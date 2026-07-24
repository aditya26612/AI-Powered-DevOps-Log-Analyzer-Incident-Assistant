package com.project.apigateway.config;

import com.project.apigateway.security.JwtAuthenticationWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final JwtAuthenticationWebFilter jwtAuthenticationFilter;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {

        return builder.routes()

                .route("user-service", route -> route

                        .path("/api/v1/auth/**",
                                "/api/v1/users/**")

                        .uri("http://localhost:8081"))

                .route("log-service", route -> route

                        .path("/api/v1/logs/**")


                        .uri("http://localhost:8082"))

                .build();
    }
}