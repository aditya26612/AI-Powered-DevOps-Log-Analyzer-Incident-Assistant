package com.project.apigateway.config;

import com.project.apigateway.security.JwtAuthenticationWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@RequiredArgsConstructor
@EnableReactiveMethodSecurity
public class SecurityConfig {


    private final JwtAuthenticationWebFilter jwtAuthenticationWebFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        return http

                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                .addFilterAt(
                        jwtAuthenticationWebFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )

                .authorizeExchange(exchange -> exchange

                        .pathMatchers("/api/v1/auth/**")
                        .permitAll()

                        .anyExchange()
                        .authenticated()

                )

                .build();
    }
}