package com.project.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    @Value("${gateway.user-service-url}")
    private String userServiceUrl;

    @Value("${gateway.log-service-url}")
    private String logServiceUrl;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", route -> route
                        .path("/api/v1/auth/**",
                                "/api/v1/users/**")
                        .uri(userServiceUrl))

                .route("log-service", route -> route
                        .path("/api/v1/logs/**",
                                "/api/v2/logs/batch",
                                "/api/v1/analyzer/**",
                                "/api/v1/integrations/**",
                                "/api/v1/organizations/**")
                        .uri(logServiceUrl))
                .build();
    }
}
