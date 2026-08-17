package com.ecom.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds security headers to every response.
 *
 * <p>The Content-Security-Policy names only this origin. Bootstrap, Font
 * Awesome and Sarabun used to load from public CDNs; they are now served from
 * {@code /vendor/**}, which removed four third-party origins from the policy and
 * with them the subresource-integrity exposure a CDN implies.
 *
 * <p>{@code 'unsafe-inline'} is still present on {@code script-src} and
 * {@code style-src}. Removing it means eliminating every inline event handler
 * and {@code style="…"} attribute in the templates — real work, tracked
 * separately. Everything that does not depend on that refactor is tightened
 * here: {@code form-action}, {@code base-uri} and {@code object-src} have no
 * fallback to {@code default-src}, so leaving them out left login forms free to
 * post anywhere and {@code <base>} free to rewrite every relative URL.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

        /** Sent only over HTTPS; announcing HSTS on a plain-HTTP dev run is meaningless. */
        private final boolean hstsEnabled;

        public SecurityHeadersFilter(
                        @Value("${app.security.hsts-enabled:true}") boolean hstsEnabled) {
                this.hstsEnabled = hstsEnabled;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                        throws IOException, ServletException {

                HttpServletRequest httpReq = (HttpServletRequest) request;
                HttpServletResponse httpRes = (HttpServletResponse) response;
                String uri = httpReq.getRequestURI();

                // Prevent MIME type sniffing
                httpRes.setHeader("X-Content-Type-Options", "nosniff");

                // Prevent clickjacking (frame-ancestors below is the modern equivalent)
                httpRes.setHeader("X-Frame-Options", "SAMEORIGIN");

                // Legacy XSS protection
                httpRes.setHeader("X-XSS-Protection", "1; mode=block");

                // Limit referrer information
                httpRes.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // Restrict browser feature access
                httpRes.setHeader("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()");

                httpRes.setHeader("Content-Security-Policy", contentSecurityPolicy());

                if (hstsEnabled) {
                        httpRes.setHeader("Strict-Transport-Security",
                                        "max-age=31536000; includeSubDomains");
                }

                // Prevent caching of sensitive pages (skip for static resources)
                boolean isStaticResource = uri.startsWith("/css/") || uri.startsWith("/js/")
                                || uri.startsWith("/img/") || uri.startsWith("/static/")
                                || uri.startsWith("/vendor/")
                                || uri.startsWith("/admin/css/") || uri.startsWith("/admin/js/")
                                || uri.startsWith("/admin/img/");
                if (!isStaticResource) {
                        httpRes.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                        httpRes.setHeader("Pragma", "no-cache");
                }

                chain.doFilter(request, response);
        }

        private String contentSecurityPolicy() {
                return "default-src 'self'; "
                                + "script-src 'self' 'unsafe-inline' https://translate.google.com "
                                + "https://translate.googleapis.com https://translate-pa.googleapis.com; "
                                + "style-src 'self' 'unsafe-inline' https://translate.googleapis.com; "
                                + "font-src 'self' data:; "
                                + "img-src 'self' data: blob: https://translate.google.com "
                                + "https://www.google.com https://*.gstatic.com; "
                                + "connect-src 'self' https://translate.googleapis.com "
                                + "https://translate-pa.googleapis.com; "
                                // blob: is required by the PDF preview iframe (doc_preview.js)
                                + "frame-src 'self' blob: https://translate.google.com "
                                + "https://docs.google.com https://hr2.kku.ac.th; "
                                + "frame-ancestors 'self'; "
                                // The three directives below do not inherit from default-src.
                                // Without form-action a reflected-injection bug could retarget
                                // the sign-in POST at an attacker's host.
                                + "form-action 'self'; "
                                + "base-uri 'self'; "
                                + "object-src 'none'";
        }
}
