package com.project.apigateway.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Secret key used for signing and validating JWT tokens.
     * Must be identical to the User Service secret.
     */
    private String secret;

    /**
     * JWT expiration time in milliseconds.
     */
    private long expiration;

}