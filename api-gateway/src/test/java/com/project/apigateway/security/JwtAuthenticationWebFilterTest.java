package com.project.apigateway.security;

import com.project.apigateway.properties.JwtProperties;
import com.project.apigateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationWebFilterTest {

    private static final String TEST_JWT_SECRET = "super_secure_test_jwt_secret_key_at_least_32_bytes_long_12345";
    private static final String VALID_INTERNAL_KEY = "internal_service_key_at_least_16_chars";
    private static final String WEAK_INTERNAL_KEY = "short_key";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_JWT_SECRET);
        jwtProperties.setExpiration(3600000L);
        jwtUtil = new JwtUtil(jwtProperties);
    }

    private String generateTestJwt(String email, String role, long expirationOffsetMs) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationOffsetMs))
                .signWith(key)
                .compact();
    }

    @Test
    void filter_internalApiKey_doesNotAuthorizeApplicationTraffic() {
        JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter(
                jwtUtil,
                VALID_INTERNAL_KEY,
                "http://localhost:8082"
        );

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/logs")
                .header("X-API-Key", VALID_INTERNAL_KEY)
                .header("X-User-Email", "attacker@evil.com")
                .header("X-Application-Name", "evil-app")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

        WebFilterChain chain = downstreamExchange -> {
            capturedExchange.set(downstreamExchange);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange mutated = capturedExchange.get();
        assertNotNull(mutated, "Downstream exchange must be provided");
        assertNull(mutated.getRequest().getHeaders().getFirst("X-User-Email"));
        assertNull(mutated.getRequest().getHeaders().getFirst("X-Application-Name"));
        assertNull(capturedContext.get(), "Internal directory key cannot authenticate application ingestion");
    }

    @Test
    void filter_missingOrWeakInternalKey_failsClosed() {
        // Filter configured with a weak key (< 16 chars) must fail closed
        JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter(
                jwtUtil,
                WEAK_INTERNAL_KEY,
                "http://localhost:8082"
        );

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/logs")
                .header("X-API-Key", WEAK_INTERNAL_KEY)
                .header("X-User-Email", "attacker@evil.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

        WebFilterChain chain = downstreamExchange -> {
            capturedExchange.set(downstreamExchange);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();
        };

        // When weak internal key fails closed, it falls back to dynamic key lookup (which fails or returns unauthenticated)
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange mutated = capturedExchange.get();
        assertNotNull(mutated);
        // Ensure the attacker's supplied email was stripped and not trusted
        assertNull(mutated.getRequest().getHeaders().getFirst("X-User-Email"));
        assertNull(capturedContext.get(), "Weak key must not authenticate as internal service");
    }

    @Test
    void filter_unauthenticatedRequest_stripsInboundIdentityHeaders() {
        JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter(
                jwtUtil,
                VALID_INTERNAL_KEY,
                "http://localhost:8082"
        );

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login")
                .header("X-User-Email", "spoofed@victim.com")
                .header("X-Application-Name", "spoofed-app")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        WebFilterChain chain = downstreamExchange -> {
            capturedExchange.set(downstreamExchange);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange mutated = capturedExchange.get();
        assertNotNull(mutated);
        assertNull(mutated.getRequest().getHeaders().getFirst("X-User-Email"),
                "Inbound X-User-Email must be stripped at gateway boundary");
        assertNull(mutated.getRequest().getHeaders().getFirst("X-Application-Name"),
                "Inbound X-Application-Name must be stripped at gateway boundary");
    }

    @Test
    void filter_validJwt_extractsClaimsAndSetsVerifiedIdentity() {
        JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter(
                jwtUtil,
                VALID_INTERNAL_KEY,
                "http://localhost:8082"
        );

        String token = generateTestJwt("alice@company.com", "DEVELOPER", 3600000L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/logs")
                .header("Authorization", "Bearer " + token)
                .header("X-User-Email", "forged@evil.com")
                .header("X-Application-Name", "forged-app")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

        WebFilterChain chain = downstreamExchange -> {
            capturedExchange.set(downstreamExchange);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange mutated = capturedExchange.get();
        assertNotNull(mutated);
        assertEquals("alice@company.com", mutated.getRequest().getHeaders().getFirst("X-User-Email"),
                "Identity must be derived from verified JWT subject");
        assertNull(mutated.getRequest().getHeaders().getFirst("X-Application-Name"),
                "Forged application header must be stripped");

        SecurityContext ctx = capturedContext.get();
        assertNotNull(ctx);
        assertEquals("alice@company.com", ctx.getAuthentication().getName());
        assertTrue(ctx.getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DEVELOPER")));
    }

    @Test
    void filter_expiredOrInvalidJwt_isNotAuthenticatedAndHeadersStripped() {
        JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter(
                jwtUtil,
                VALID_INTERNAL_KEY,
                "http://localhost:8082"
        );

        String expiredToken = generateTestJwt("alice@company.com", "DEVELOPER", -10000L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/logs")
                .header("Authorization", "Bearer " + expiredToken)
                .header("X-User-Email", "forged@evil.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

        WebFilterChain chain = downstreamExchange -> {
            capturedExchange.set(downstreamExchange);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange mutated = capturedExchange.get();
        assertNotNull(mutated);
        assertNull(mutated.getRequest().getHeaders().getFirst("X-User-Email"));
        assertNull(capturedContext.get(), "Expired JWT must not produce authentication");
    }
}
