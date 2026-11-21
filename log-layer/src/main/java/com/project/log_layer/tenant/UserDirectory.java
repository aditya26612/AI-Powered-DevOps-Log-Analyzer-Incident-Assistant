package com.project.log_layer.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Component
public class UserDirectory {

    private final WebClient client;
    private final String apiKey;

    public UserDirectory(WebClient.Builder builder,
                         @Value("${tenant.user-service-url:http://localhost:8081}") String url,
                         @Value("${internal.api-key:}") String apiKey) {
        this.client = builder.clone().baseUrl(url).build();
        this.apiKey = apiKey;
    }

    public void requireUser(long id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user ID");
        }
        if (apiKey.length() < 16) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User directory unavailable");
        }
        Boolean exists;
        try {
            exists = client.get().uri("/internal/v1/users/{id}/exists", id)
                    .header("X-Internal-Api-Key", apiKey)
                    .retrieve().bodyToMono(Boolean.class).block(Duration.ofSeconds(3));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User directory unavailable");
        }
        if (!Boolean.TRUE.equals(exists)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }
}
