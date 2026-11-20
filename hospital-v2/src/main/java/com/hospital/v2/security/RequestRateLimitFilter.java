package com.hospital.v2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-process fixed-window limiter for the bundled deployment.
 * A multi-instance deployment should move the same key/window contract to Redis.
 */
@Component
public final class RequestRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RequestRateLimitFilter() { this(Clock.systemUTC()); }

    RequestRateLimitFilter(Clock clock) { this.clock = clock; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Policy policy = policy(request.getRequestURI());
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }
        long now = clock.millis();
        long bucket = now / 60_000L;
        String key = request.getRemoteAddr() + "|" + policy.name + "|" + bucket;
        Window window = windows.computeIfAbsent(key, ignored -> new Window(bucket));
        int count = window.count.incrementAndGet();
        if (count > policy.limit) {
            response.setStatus(429);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(60 - ((now / 1000L) % 60L)));
            response.getWriter().write("{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\"}");
            if (windows.size() > 10_000) cleanup(bucket);
            return;
        }
        if (windows.size() > 10_000) cleanup(bucket);
        chain.doFilter(request, response);
    }

    private Policy policy(String uri) {
        if (uri.endsWith("/api/auth/login")) return new Policy("login", 10);
        if (uri.endsWith("/api/auth/register")) return new Policy("register", 5);
        if (uri.endsWith("/api/auth/refresh")) return new Policy("refresh", 30);
        if (uri.endsWith("/api/patient/chat") || uri.endsWith("/api/patient/triage")) {
            return new Policy("ai", 20);
        }
        if (uri.startsWith("/v2/api/")) return new Policy("api", 120);
        return null;
    }

    private void cleanup(long currentBucket) {
        windows.entrySet().removeIf(entry -> entry.getValue().bucket < currentBucket - 1);
    }

    private record Policy(String name, int limit) {}
    private static final class Window {
        private final long bucket;
        private final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();

        private Window(long bucket) { this.bucket = bucket; }
    }
}
