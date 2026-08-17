package com.ecom.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Adds security headers to every response and secures inline scripts/styles
 * with a dynamic cryptographic per-request CSP Nonce.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

        private static final SecureRandom SECURE_RANDOM = new SecureRandom();

        /**
         * Sent only over HTTPS; announcing HSTS on a plain-HTTP dev run is meaningless.
         */
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

                // Clickjacking. This filter is the single owner of X-Frame-Options;
                // Spring Security's own writer is switched off in SecurityConfig so the
                // two cannot disagree (they used to: SAMEORIGIN here, DENY from Spring
                // Security on any request that reached it). The filter owns it rather
                // than Spring Security because only the filter runs on *every* path —
                // Spring Security never sees the 405 short-circuit below, so leaving it
                // in charge would emit the header on normal responses and omit it on
                // rejected ones. SAMEORIGIN matches the frame-ancestors 'self' in the
                // CSP further down.
                httpRes.setHeader("X-Frame-Options", "SAMEORIGIN");

                // Legacy XSS protection
                httpRes.setHeader("X-XSS-Protection", "1; mode=block");

                // Limit referrer information
                httpRes.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // Restrict browser feature access
                httpRes.setHeader("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()");

                // ── Proxy Disclosure Prevention (ZAP Alert 40025 / CWE-204) ──
                // Block HTTP methods used for proxy fingerprinting.
                // Return a uniform 405 with security headers already set above
                // so no observable response discrepancy exists.
                //
                // Max-Forwards is deliberately ignored rather than blocked. It is only
                // meaningful on TRACE and OPTIONS, both already refused here. A previous
                // version rejected *any* request carrying the header, which meant
                // `GET /signin` returned 200 but `GET /signin` + `Max-Forwards: 0`
                // returned an empty 405 — the exact hop-was-consumed signal alert 40025
                // looks for. The code written to suppress the alert was raising it.
                // Uniformity is what clears this finding, not blocking.
                String method = httpReq.getMethod();
                if ("TRACE".equalsIgnoreCase(method)
                                || "TRACK".equalsIgnoreCase(method)
                                || "OPTIONS".equalsIgnoreCase(method)) {
                        httpRes.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                        httpRes.setHeader("Allow", "GET, POST, HEAD");
                        httpRes.setContentLength(0);
                        return; // do NOT continue the filter chain
                }

                // Generate a cryptographically secure 128-bit random nonce for CSP
                byte[] nonceBytes = new byte[16];
                SECURE_RANDOM.nextBytes(nonceBytes);
                String nonce = Base64.getEncoder().encodeToString(nonceBytes);
                httpReq.setAttribute("cspNonce", nonce);

                httpRes.setHeader("Content-Security-Policy", contentSecurityPolicy(nonce));

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

                // For static assets, pass through directly
                if (isStaticResource) {
                        chain.doFilter(request, response);
                        return;
                }

                // Wrap HTML responses to inject nonce into server-rendered <script> and <style>
                // tags
                HtmlNonceResponseWrapper responseWrapper = new HtmlNonceResponseWrapper(httpRes, nonce);
                chain.doFilter(request, responseWrapper);

                byte[] processed = responseWrapper.getProcessedBytes();
                if (!httpRes.isCommitted()) {
                        httpRes.setContentLength(processed.length);
                }
                ServletOutputStream out = httpRes.getOutputStream();
                out.write(processed);
                out.flush();
        }

        private String contentSecurityPolicy(String nonce) {
                return "default-src 'self'; "
                                + "script-src 'self' 'nonce-" + nonce + "' https://translate.google.com "
                                + "https://translate.googleapis.com https://translate-pa.googleapis.com; "
                                + "style-src 'self' 'nonce-" + nonce + "' https://translate.googleapis.com; "
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

        /**
         * Response wrapper that captures HTML output and auto-injects CSP nonce
         * into {@code <script>} and {@code <style>} elements rendered by templates.
         */
        private static class HtmlNonceResponseWrapper extends HttpServletResponseWrapper {

                private final ByteArrayOutputStream capture = new ByteArrayOutputStream();
                private ServletOutputStream output;
                private PrintWriter writer;
                private final String nonce;

                private static final Pattern SCRIPT_PATTERN = Pattern
                                .compile("(?i)<script\\b(?![^>]*\\bnonce=)([^>]*)>");
                private static final Pattern STYLE_PATTERN = Pattern.compile("(?i)<style\\b(?![^>]*\\bnonce=)([^>]*)>");

                public HtmlNonceResponseWrapper(HttpServletResponse response, String nonce) {
                        super(response);
                        this.nonce = nonce;
                }

                @Override
                public ServletOutputStream getOutputStream() throws IOException {
                        if (writer != null) {
                                throw new IllegalStateException(
                                                "getWriter() has already been called on this response.");
                        }
                        if (output == null) {
                                output = new ServletOutputStream() {
                                        @Override
                                        public boolean isReady() {
                                                return true;
                                        }

                                        @Override
                                        public void setWriteListener(WriteListener writeListener) {
                                        }

                                        @Override
                                        public void write(int b) {
                                                capture.write(b);
                                        }

                                        @Override
                                        public void write(byte[] b, int off, int len) {
                                                capture.write(b, off, len);
                                        }
                                };
                        }
                        return output;
                }

                @Override
                public PrintWriter getWriter() throws IOException {
                        if (output != null) {
                                throw new IllegalStateException(
                                                "getOutputStream() has already been called on this response.");
                        }
                        if (writer == null) {
                                writer = new PrintWriter(
                                                new OutputStreamWriter(capture, getCharacterEncodingOrDefault()));
                        }
                        return writer;
                }

                @Override
                public void flushBuffer() throws IOException {
                        if (writer != null) {
                                writer.flush();
                        } else if (output != null) {
                                output.flush();
                        }
                }

                public byte[] getProcessedBytes() throws IOException {
                        if (writer != null) {
                                writer.flush();
                        } else if (output != null) {
                                output.flush();
                        }

                        byte[] rawBytes = capture.toByteArray();
                        String contentType = getContentType();

                        if (contentType != null && contentType.toLowerCase().contains("text/html")
                                        && rawBytes.length > 0) {
                                String charset = getCharacterEncodingOrDefault();
                                String html = new String(rawBytes, charset);

                                // Inject nonce into <script> and <style> tags lacking one
                                String modifiedHtml = SCRIPT_PATTERN.matcher(html)
                                                .replaceAll("<script nonce=\"" + Matcher.quoteReplacement(nonce)
                                                                + "\"$1>");
                                modifiedHtml = STYLE_PATTERN.matcher(modifiedHtml)
                                                .replaceAll("<style nonce=\"" + Matcher.quoteReplacement(nonce)
                                                                + "\"$1>");

                                return modifiedHtml.getBytes(charset);
                        }

                        return rawBytes;
                }

                private String getCharacterEncodingOrDefault() {
                        String enc = getCharacterEncoding();
                        return (enc != null && !enc.isBlank()) ? enc : StandardCharsets.UTF_8.name();
                }
        }
}
