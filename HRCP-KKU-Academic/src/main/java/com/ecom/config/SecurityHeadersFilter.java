package com.ecom.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds security headers to all responses:
 * - X-Content-Type-Options: prevent MIME sniffing
 * - X-Frame-Options: prevent clickjacking
 * - X-XSS-Protection: legacy XSS filter
 * - Referrer-Policy: limit referrer info
 * - Permissions-Policy: restrict browser features
 * - Content-Security-Policy: restrict content sources
 * - Strict-Transport-Security: force HTTPS
 * - Cache-Control: prevent sensitive page caching
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                        throws IOException, ServletException {

                HttpServletResponse httpRes = (HttpServletResponse) response;

                // Prevent MIME type sniffing
                httpRes.setHeader("X-Content-Type-Options", "nosniff");

                // Prevent clickjacking
                httpRes.setHeader("X-Frame-Options", "SAMEORIGIN");

                // Legacy XSS protection
                httpRes.setHeader("X-XSS-Protection", "1; mode=block");

                // Limit referrer information
                httpRes.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // Restrict browser feature access
                httpRes.setHeader("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()");

                // Content Security Policy (includes Google Translate domains)
                httpRes.setHeader("Content-Security-Policy",
                                "default-src 'self'; "
                                                + "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://translate.google.com https://translate.googleapis.com https://translate-pa.googleapis.com; "
                                                + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com https://translate.googleapis.com; "
                                                + "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; "
                                                + "img-src 'self' data: blob: https://translate.google.com https://www.google.com https://*.gstatic.com; "
                                                + "connect-src 'self' https://translate.googleapis.com https://translate-pa.googleapis.com; "
                                                + "frame-src 'self' https://translate.google.com; "
                                                + "frame-ancestors 'self'");

                // Force HTTPS (will be active when behind HTTPS proxy)
                httpRes.setHeader("Strict-Transport-Security",
                                "max-age=31536000; includeSubDomains");

                // Prevent caching of sensitive pages
                httpRes.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                httpRes.setHeader("Pragma", "no-cache");

                chain.doFilter(request, response);
        }
}
