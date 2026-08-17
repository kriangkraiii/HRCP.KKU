package com.ecom.config;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * IP-based rate limiter to protect against DDoS and brute-force attacks.
 * - General requests: configurable per IP (default 200 req/min)
 * - Login attempts: configurable per IP (default 30 req/min)
 *
 * Uses Caffeine cache to auto-evict stale entries and prevent memory exhaustion.
 * Relies on server.forward-headers-strategy=native for trusted proxy IP resolution.
 */
@Component
public class RateLimitFilter implements Filter {

    @Value("${app.rate-limit.general:200}")
    private int generalLimit = 200;

    @Value("${app.rate-limit.login:30}")
    private int loginLimit = 30;

    private static final long WINDOW_MS = 60_000; // 1 minute

    private final Cache<String, RateBucket> generalBuckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final Cache<String, RateBucket> loginBuckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;
        String uri = httpReq.getRequestURI();

        // Skip rate limiting for static resources (checked first for performance)
        if (isStaticResource(uri)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = httpReq.getRemoteAddr();

        // Credential entry: stricter limit. The OTP screen counts — it is the
        // second factor for both password login and KKU SSO, and a code submitted
        // there is a credential like any other.
        if (isCredentialSubmission(uri, httpReq.getMethod())) {
            RateBucket bucket = loginBuckets.get(clientIp, k -> new RateBucket());
            if (!bucket.tryConsume(loginLimit)) {
                sendRateLimitResponse(httpRes, "เข้าสู่ระบบบ่อยเกินไป กรุณารอ 1 นาทีแล้วลองใหม่อีกครั้ง");
                return;
            }
        } else {
            RateBucket bucket = generalBuckets.get(clientIp, k -> new RateBucket());
            if (!bucket.tryConsume(generalLimit)) {
                sendRateLimitResponse(httpRes, "คุณส่งคำขอมากเกินไป กรุณารอสักครู่");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(
                "<html><body><h2>Too Many Requests</h2><p>" + message + "</p></body></html>");
    }

    private boolean isCredentialSubmission(String uri, String method) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return "/login".equals(uri)
                || "/2fa/verify".equals(uri)
                || "/2fa/resend".equals(uri);
    }

    private boolean isStaticResource(String uri) {
        return uri.equals("/css") || uri.startsWith("/css/")
                || uri.equals("/js") || uri.startsWith("/js/")
                || uri.equals("/img") || uri.startsWith("/img/")
                || uri.equals("/static") || uri.startsWith("/static/")
                || uri.equals("/vendor") || uri.startsWith("/vendor/")
                || uri.startsWith("/admin/css")
                || uri.startsWith("/admin/js")
                || uri.startsWith("/admin/img")
                || uri.equals("/favicon.ico")
                || uri.equals("/robots.txt")
                || uri.equals("/sitemap.xml");
    }

    /**
     * Thread-safe sliding-window rate bucket.
     * All state mutations are synchronized to prevent race conditions
     * between window reset and counter increment.
     */
    private static class RateBucket {
        private int count = 0;
        private long windowStart = System.currentTimeMillis();

        synchronized boolean tryConsume(int limit) {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MS) {
                count = 0;
                windowStart = now;
            }
            count++;
            return count <= limit;
        }
    }
}
