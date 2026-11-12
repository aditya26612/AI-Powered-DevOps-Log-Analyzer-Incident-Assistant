package com.project.log_layer.util;

import com.project.log_layer.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

public final class CurrentUser {

    private static final String USER_EMAIL_HEADER =
            "X-User-Email";

    private CurrentUser() {
    }

    public static String getEmail(
            HttpServletRequest request) {

        String email =
                request.getHeader(USER_EMAIL_HEADER);

        if (email != null && !email.isBlank()) {
            return email;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "system@devinsight.io";
        }

        throw new UnauthorizedException(
                "Authenticated user information is missing."
        );
    }
}