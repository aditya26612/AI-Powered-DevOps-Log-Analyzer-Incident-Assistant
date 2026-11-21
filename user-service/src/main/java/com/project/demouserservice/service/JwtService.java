package com.project.demouserservice.service;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.demouserservice.entity.UserEntity;
import com.project.demouserservice.security.SignedIdentity;
import io.jsonwebtoken.JwtException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        return SignedIdentity.signingKey(secret);
    }

    public String generateToken(UserEntity user) {
        if (user.getId() == null || user.getId() <= 0) {
            throw new IllegalStateException("Cannot issue an access token without a persisted user ID");
        }

        SecretKey key = getSigningKey();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("user_id", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignedIdentity.signingAlgorithm(key))
                .compact();
    }

    public String extractEmail(String token) {

        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {

        return extractClaims(token)
                .get("role", String.class);
    }

    public Date extractExpiration(String token) {

        return extractClaims(token).getExpiration();
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return SignedIdentity.verify(token, getSigningKey());
    }
}
