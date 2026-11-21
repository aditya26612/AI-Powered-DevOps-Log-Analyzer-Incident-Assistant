package com.project.apigateway.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class SignedIdentityTest {

    private static final String SUBJECT = "developer@example.test";
    private static final javax.crypto.SecretKey KEY = SignedIdentity.signingKey("x".repeat(64));

    private String signedClaims(String extra) {
        String body = "{\"sub\":\"" + SUBJECT + "\",\"exp\":" + Instant.now().plusSeconds(60).getEpochSecond()
                + extra + "}";
        return Jwts.builder().content(body.getBytes(StandardCharsets.UTF_8))
                .signWith(KEY, SignedIdentity.signingAlgorithm(KEY)).compact();
    }

    @Test
    void acceptsLegacyAndMaximumIntegralIdentity() {
        SignedIdentity legacy = SignedIdentity.from(SignedIdentity.verify(signedClaims(""), KEY));
        assertEquals(SUBJECT, legacy.email());
        assertNull(legacy.userId());

        SignedIdentity numeric = SignedIdentity.from(SignedIdentity.verify(
                signedClaims(",\"user_id\":9223372036854775807"), KEY));
        assertEquals(Long.MAX_VALUE, numeric.userId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "\"7\"", "7.25", "0", "-1", "9223372036854775808"})
    void rejectsPresentMalformedIdentity(String value) {
        assertThrows(JwtException.class, () -> SignedIdentity.verify(signedClaims(",\"user_id\":" + value), KEY));
    }

    @Test
    void rejectsDuplicateClaims() {
        assertThrows(JwtException.class,
                () -> SignedIdentity.verify(signedClaims(",\"user_id\":7,\"user_id\":8"), KEY));
    }

    @Test
    void rejectsSignatureAlgorithmOutsideIssuerConfiguration() {
        String token = Jwts.builder().subject(SUBJECT)
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(KEY, Jwts.SIG.HS256).compact();
        assertThrows(JwtException.class, () -> SignedIdentity.verify(token, KEY));
    }

    @Test
    void rejectsExpiredOrInvalidSignature() {
        String expired = Jwts.builder().subject(SUBJECT).expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(KEY, SignedIdentity.signingAlgorithm(KEY)).compact();
        assertThrows(JwtException.class, () -> SignedIdentity.verify(expired, KEY));

        String signed = signedClaims(",\"user_id\":7");
        var differentKey = SignedIdentity.signingKey("y".repeat(64));
        assertThrows(JwtException.class, () -> SignedIdentity.verify(signed, differentKey));
    }
}
