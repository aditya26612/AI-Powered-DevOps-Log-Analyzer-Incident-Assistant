package com.project.log_layer.security;

import com.project.log_layer.tenant.ApplicationCredentials;
import com.project.log_layer.tenant.OrganizationService;
import com.project.log_layer.util.CurrentUser;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.UUID;

@Component
public class RequestIdentityFilter extends OncePerRequestFilter {

    private final ApplicationCredentials credentials;
    private final OrganizationService organizations;
    private final String jwtSecret;
    private final IngestionAdmission admission;
    private final byte[] internalKey;

    public RequestIdentityFilter(ApplicationCredentials credentials, OrganizationService organizations,
                                 IngestionAdmission admission,
                                 @Value("${JWT_SECRET:}") String jwtSecret,
                                 @Value("${internal.api-key:}") String internalKey) {
        this.credentials = credentials;
        this.organizations = organizations;
        this.jwtSecret = jwtSecret;
        this.admission = admission;
        this.internalKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String route = path(request);
        return !route.startsWith("/api/v1/") && !route.startsWith("/api/v2/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String route = path(request);
        if (route.contains("%") || route.contains(";") || route.contains("//") || route.contains("\\")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (route.equals("/api/v1/integrations/verify-key")) {
            if ("GET".equals(request.getMethod()) && internal(singleHeader(request, "X-Internal-Api-Key"))) {
                chain.doFilter(request, response);
            } else {
                unauthorized(response);
            }
            return;
        }

        String apiKey = singleHeader(request, "X-API-Key");
        String authorization = singleHeader(request, HttpHeaders.AUTHORIZATION);
        boolean post = "POST".equals(request.getMethod());
        boolean batch = post && (route.equals("/api/v1/logs/batch") || route.equals("/api/v2/logs/batch"));
        boolean ingestion = post && (batch || route.equals("/api/v1/logs"));
        if (ingestion && request.getContentLengthLong() > IngestionAdmission.MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }

        if (ingestion && !admission.allowAuthentication(request.getRemoteAddr())) {
            response.sendError(429);
            return;
        }
        if (apiKey != null && request.getHeader(HttpHeaders.AUTHORIZATION) == null && ingestion) {
            ApplicationCredentials.Binding binding = credentials.authenticate(apiKey).orElse(null);
            if (binding != null && !internal(apiKey)) {
                request.setAttribute(CurrentUser.VERIFIED_EMAIL, CurrentUser.SERVICE_EMAIL);
                request.setAttribute(TenantIdentity.ORGANIZATION, binding.organizationId());
                request.setAttribute(TenantIdentity.INGESTION, binding);
                if (batch && !supportsBatch(request, response)) {
                    return;
                }
                ingest(request, response, chain, batch || admission.allowApplication(binding.applicationId()));
                return;
            }
        } else if (!batch && route.startsWith("/api/v1/") && authorization != null && request.getHeader("X-API-Key") == null
                && authorization.startsWith("Bearer ")) {
            SignedIdentity identity = authenticate(authorization.substring(7));
            if (identity != null && !CurrentUser.SERVICE_EMAIL.equalsIgnoreCase(identity.email().strip())) {
                request.setAttribute(TenantIdentity.HUMAN, identity);
                request.setAttribute(CurrentUser.VERIFIED_EMAIL, identity.email());
                if (route.startsWith("/api/v1/integrations")) {
                    response.sendError(HttpServletResponse.SC_GONE);
                    return;
                }
                if (route.equals("/api/v1/logs") || route.startsWith("/api/v1/logs/")
                        || route.startsWith("/api/v1/analyzer/")) {
                    if (!selectOrganization(request, response, identity, route)) {
                        return;
                    }
                }
                if (ingestion) {
                    ingest(request, response, chain, admission.allowManual(TenantIdentity.organization(request).toString()));
                } else {
                    chain.doFilter(request, response);
                }
                return;
            }
        }
        unauthorized(response);
    }

    private boolean supportsBatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var encodings = Collections.list(request.getHeaders(HttpHeaders.CONTENT_ENCODING));
        var types = Collections.list(request.getHeaders(HttpHeaders.CONTENT_TYPE));
        try {
            if ((encodings.isEmpty() || (encodings.size() == 1 && encodings.getFirst().equalsIgnoreCase("identity")))
                    && types.size() == 1) {
                var type = MediaType.parseMediaType(types.getFirst());
                if ("application".equalsIgnoreCase(type.getType()) && "json".equalsIgnoreCase(type.getSubtype())
                        && (type.getCharset() == null || StandardCharsets.UTF_8.equals(type.getCharset()))) {
                    return true;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed media types also receive an unsupported media type response.
        }
        response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
        return false;
    }

    private void ingest(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                        boolean allowed) throws IOException, ServletException {
        if (!allowed) {
            response.sendError(429);
            return;
        }
        var bounded = IngestionAdmission.boundedBody(request);
        if (bounded == null) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(bounded, response);
    }

    private boolean selectOrganization(HttpServletRequest request, HttpServletResponse response, SignedIdentity identity,
                                       String route) throws IOException {
        if (identity.userId() == null) {
            unauthorized(response);
            return false;
        }
        String selected = singleHeader(request, "X-DevInsight-Organization");
        if (selected == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        try {
            UUID id = UUID.fromString(selected);
            if (!id.toString().equalsIgnoreCase(selected)) {
                throw new IllegalArgumentException();
            }
            var membership = organizations.get(id, identity.userId());
            boolean write = "POST".equals(request.getMethod()) && !route.equals("/api/v1/logs/search");
            if (write && !membership.role().managesApplications()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
            if ("DELETE".equals(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return false;
            }
            request.setAttribute(TenantIdentity.ORGANIZATION, id);
            return true;
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (ResponseStatusException ex) {
            response.sendError(ex.getStatusCode().value());
        }
        return false;
    }

    private SignedIdentity authenticate(String token) {
        if (jwtSecret.isBlank()) {
            return null;
        }
        try {
            return SignedIdentity.from(SignedIdentity.verify(token, SignedIdentity.signingKey(jwtSecret)));
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean internal(String candidate) {
        return internalKey.length >= 16 && candidate != null
                && MessageDigest.isEqual(internalKey, candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : null;
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
