package com.project.llmservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

@Component
public class InternalAnalysisFilter extends OncePerRequestFilter {
    private final byte[] internalKey;

    public InternalAnalysisFilter(@Value("${INTERNAL_API_KEY:}") String configured) {
        this.internalKey = configured.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "GET".equals(request.getMethod())
                && (path.equals("/actuator/health") || path.equals("/api/v1/system/health") || path.equals("/test"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var provided = Collections.list(request.getHeaders("X-Internal-Api-Key"));
        if (internalKey.length < 16 || provided.size() != 1
                || !MessageDigest.isEqual(internalKey, provided.getFirst().getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
