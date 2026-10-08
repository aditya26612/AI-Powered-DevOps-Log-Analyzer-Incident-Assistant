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

        if (email == null || email.isBlank()) {
            throw new UnauthorizedException(
                    "Authenticated user information is missing."
            );
        }

        return email;
    }
}