package com.project.apigateway.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Validates identity claims only after verifying the original signed token. */
public record SignedIdentity(String email, String role, Long userId) {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public static SecretKey signingKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static MacAlgorithm signingAlgorithm(SecretKey key) {
        return switch (key.getAlgorithm()) {
            case "HmacSHA256" -> Jwts.SIG.HS256;
            case "HmacSHA384" -> Jwts.SIG.HS384;
            case "HmacSHA512" -> Jwts.SIG.HS512;
            default -> throw new IllegalStateException("Unsupported configured JWT signing key");
        };
    }

    public static Claims verify(String token, SecretKey key) {
        if (token == null || token.length() > 8192) {
            throw new MalformedJwtException("Invalid access token");
        }

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .sig().clear().add(signingAlgorithm(key)).and()
                .zip().clear().and()
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getExpiration() == null || claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new MalformedJwtException("Missing required identity claims");
        }
        validateRawIdentity(token);
        from(claims);
        return claims;
    }

    private static void validateRawIdentity(String token) {
        try {
            int first = token.indexOf('.');
            int second = token.indexOf('.', first + 1);
            var payload = JSON.readTree(Base64.getUrlDecoder().decode(token.substring(first + 1, second)));
            var id = payload.get("user_id");
            if (id != null && (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0)) {
                throw new MalformedJwtException("Invalid user identity claim");
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new MalformedJwtException("Invalid access token");
        }
    }

    public static SignedIdentity from(Claims claims) {
        return new SignedIdentity(claims.getSubject(), claims.get("role", String.class), userId(claims));
    }

    public static Long userId(Claims claims) {
        if (!claims.containsKey("user_id")) {
            return null;
        }

        Object value = claims.get("user_id");
        long parsed;
        try {
            parsed = switch (value) {
                case Integer small -> small.longValue();
                case Long numeric -> numeric;
                case BigInteger integer -> integer.longValueExact();
                case null, default -> throw new IllegalArgumentException();
            };
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw new MalformedJwtException("Invalid user identity claim");
        }
        if (parsed <= 0) {
            throw new MalformedJwtException("Invalid user identity claim");
        }
        return parsed;
    }
}
