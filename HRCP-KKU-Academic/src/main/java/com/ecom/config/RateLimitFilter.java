package com.ecom.config;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * IP-based rate limiter to protect against DDoS and brute-force attacks.
 * - General requests: 100 req/min per IP
 * - Login attempts: 5 req/min per IP (stricter)
 */
@Component
public class RateLimitFilter implements Filter {

    private static final int GENERAL_LIMIT = 100;
    private static final int LOGIN_LIMIT = 5;
    private static final long WINDOW_MS = 60_000; // 1 minute

    private final Map<String, RateBucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, RateBucket> loginBuckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;
        String clientIp = getClientIp(httpReq);
        String uri = httpReq.getRequestURI();

        // Login endpoint: stricter limit
        if ("/login".equals(uri) && "POST".equalsIgnoreCase(httpReq.getMethod())) {
            RateBucket bucket = loginBuckets.computeIfAbsent(clientIp, k -> new RateBucket());
            if (!bucket.tryConsume(LOGIN_LIMIT)) {
                httpRes.setStatus(429);
                httpRes.setContentType("text/html;charset=UTF-8");
                httpRes.getWriter().write(
                        "<html><body><h2>คุณพยายามเข้าสู่ระบบบ่อยเกินไป</h2>"
                                + "<p>กรุณารอ 1 นาที แล้วลองใหม่อีกครั้ง</p></body></html>");
                return;
            }
        }

        // Skip rate limiting for static resources
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/img/")
                || uri.startsWith("/static/") || uri.startsWith("/admin/css/")
                || uri.startsWith("/admin/js/") || uri.startsWith("/admin/img/")) {
            chain.doFilter(request, response);
            return;
        }

        // General rate limit
        RateBucket bucket = generalBuckets.computeIfAbsent(clientIp, k -> new RateBucket());
        if (!bucket.tryConsume(GENERAL_LIMIT)) {
            httpRes.setStatus(429);
            httpRes.setContentType("text/html;charset=UTF-8");
            httpRes.getWriter().write(
                    "<html><body><h2>Too Many Requests</h2>"
                            + "<p>คุณส่งคำขอมากเกินไป กรุณารอสักครู่</p></body></html>");
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Simple sliding-window rate bucket.
     */
    private static class RateBucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        boolean tryConsume(int limit) {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MS) {
                synchronized (this) {
                    if (now - windowStart > WINDOW_MS) {
                        count.set(0);
                        windowStart = now;
                    }
                }
            }
            return count.incrementAndGet() <= limit;
        }
    }
}
