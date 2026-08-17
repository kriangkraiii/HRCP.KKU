package com.ecom.config;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

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
 * - General requests: 100 req/min per IP
 * - Login attempts: 20 req/min per IP (stricter)
 *
 * Uses Caffeine cache to auto-evict stale entries and prevent memory exhaustion.
 * Relies on server.forward-headers-strategy=native for trusted proxy IP resolution.
 */
@Component
public class RateLimitFilter implements Filter {

    private static final int GENERAL_LIMIT = 100;
    private static final int LOGIN_LIMIT = 20;
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
            if (!bucket.tryConsume(LOGIN_LIMIT)) {
                String tooMany = "คุณพยายามเข้าสู่ระบบบ่อยเกินไป กรุณารอ 1 นาที แล้วลองใหม่อีกครั้ง";
                if (uri.startsWith("/2fa/")) {
                    // Back to the screen they were on. Bouncing an OTP attempt to
                    // /signin would look like the sign-in had been thrown away, when
                    // the pending challenge is in fact still waiting.
                    httpReq.getSession().setAttribute("errorMsg", tooMany);
                    httpRes.sendRedirect("/2fa/verify");
                    return;
                }
                httpReq.getSession().setAttribute("errorMessage", tooMany);
                httpRes.sendRedirect("/signin?error");
                return;
            }
        }

        // General rate limit
        RateBucket bucket = generalBuckets.get(clientIp, k -> new RateBucket());
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

    private boolean isCredentialSubmission(String uri, String method) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return "/login".equals(uri)
                || "/2fa/verify".equals(uri)
                || "/2fa/resend".equals(uri);
    }

    private boolean isStaticResource(String uri) {
        return uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/img/")
                || uri.startsWith("/static/") || uri.startsWith("/admin/css/")
                || uri.startsWith("/admin/js/") || uri.startsWith("/admin/img/");
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
