package com.project.demouserservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class PublicAuthRateLimit extends OncePerRequestFilter {
    private final Map<String, Integer> counts = new HashMap<>();
    private long minute;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String route = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) ||
                !(route.equals("/api/v1/auth/login") || route.equals("/api/v1/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!allow(request.getRemoteAddr())) {
            response.sendError(429);
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String remote) {
        long window = System.currentTimeMillis() / 60000;
        if (window != minute) {
            minute = window;
            counts.clear();
        }
        int total = counts.getOrDefault(remote, 0);
        if (total >= 60 || (total == 0 && counts.size() >= 10000)) {
            return false;
        }
        counts.put(remote, total + 1);
        return true;
    }
}
