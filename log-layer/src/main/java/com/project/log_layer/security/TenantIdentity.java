package com.project.log_layer.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public final class TenantIdentity {
    public static final String HUMAN = TenantIdentity.class.getName() + ".human";
    public static final String ORGANIZATION = TenantIdentity.class.getName() + ".organization";

    public static final String INGESTION = TenantIdentity.class.getName() + ".ingestion";

    private TenantIdentity() {}

    public static long requireUserId(HttpServletRequest request) {
        if (request.getAttribute(HUMAN) instanceof SignedIdentity identity && identity.userId() != null) {
            return identity.userId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again to access organizations");
    }

    public static UUID organization(HttpServletRequest request) {
        return (UUID) request.getAttribute(ORGANIZATION);
    }
}
