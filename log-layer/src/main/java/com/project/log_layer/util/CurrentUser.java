package com.project.log_layer.util;

import com.project.log_layer.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

public final class CurrentUser {
    public static final String VERIFIED_EMAIL = CurrentUser.class.getName() + ".verifiedEmail";
    public static final String SERVICE_EMAIL = "system@devinsight.io";

    private CurrentUser() {}

    public static String getEmail(HttpServletRequest request) {
        if (request.getAttribute(VERIFIED_EMAIL) instanceof String email && !email.isBlank()) {
            return email;
        }
        throw new UnauthorizedException("Authenticated user information is missing.");
    }
}
