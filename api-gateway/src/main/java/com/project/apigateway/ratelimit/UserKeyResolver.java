//package com.project.apigateway.ratelimit;
//
//import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
//import org.springframework.security.core.Authentication;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//
//@Component("userKeyResolver")
//public class UserKeyResolver implements KeyResolver {
//
//    @Override
//    public Mono<String> resolve(
//            org.springframework.web.server.ServerWebExchange exchange) {
//
//        return exchange.getPrincipal()
//                .cast(Authentication.class)
//                .map(Authentication::getName)
//                .switchIfEmpty(
//                        Mono.just("anonymous")
//                );
//    }
//}