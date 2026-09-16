package com.aiquote.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple in-memory fixed-window limiter for the two anonymous, JWT-free surfaces —
 * /api/public/** (the client quote flow) and /api/auth/** (login/register, exposed to
 * credential-stuffing/brute-force and registration-spam respectively; Etap 21). The
 * latter gets a tighter window since brute-forcing a password is a per-IP low-volume
 * activity by nature, unlike normal public browsing traffic. Deliberately not
 * distributed/Redis-backed: fine for a single-instance MVP deployment, and the per-IP
 * counter map is never evicted, so it grows slowly with distinct visitor IPs over the
 * process lifetime — acceptable at MVP traffic, worth revisiting (e.g. Bucket4j +
 * Redis, or a scheduled sweep) if the public link sees real volume or the app runs on
 * multiple instances (see Etap 21 audit notes on this filter's multi-instance limits).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PUBLIC_PREFIX = "/api/public/";
    private static final int PUBLIC_MAX_REQUESTS_PER_WINDOW = 30;

    private static final String AUTH_PREFIX = "/api/auth/";
    private static final int AUTH_MAX_REQUESTS_PER_WINDOW = 10;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, RequestCounter> publicCountersByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequestCounter> authCountersByKey = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        ConcurrentHashMap<String, RequestCounter> counters;
        int maxRequestsPerWindow;
        if (uri.startsWith(PUBLIC_PREFIX)) {
            counters = publicCountersByKey;
            maxRequestsPerWindow = PUBLIC_MAX_REQUESTS_PER_WINDOW;
        } else if (uri.startsWith(AUTH_PREFIX)) {
            counters = authCountersByKey;
            maxRequestsPerWindow = AUTH_MAX_REQUESTS_PER_WINDOW;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        RequestCounter counter = counters.computeIfAbsent(key, k -> new RequestCounter());

        if (!counter.tryConsume(maxRequestsPerWindow)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Zbyt wiele żądań. Spróbuj ponownie za chwilę.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class RequestCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile Instant windowStart = Instant.now();

        synchronized boolean tryConsume(int maxRequestsPerWindow) {
            Instant now = Instant.now();
            if (Duration.between(windowStart, now).compareTo(WINDOW) > 0) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= maxRequestsPerWindow;
        }
    }
}
