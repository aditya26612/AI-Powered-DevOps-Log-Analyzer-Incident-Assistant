package com.project.log_layer.util;

import com.project.log_layer.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserTest {
    @Test
    void acceptsVerifiedRequestAttribute() {
        var request = new MockHttpServletRequest();
        request.setAttribute(CurrentUser.VERIFIED_EMAIL, "verified@example.test");
        assertEquals("verified@example.test", CurrentUser.getEmail(request));
    }

    @Test
    void ignoresIdentityAndCredentialHeadersWithoutFilterAuthentication() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Email", "supplied@example.test");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-API-Key", "synthetic-request-value");
        assertThrows(UnauthorizedException.class, () -> CurrentUser.getEmail(request));
    }
}
