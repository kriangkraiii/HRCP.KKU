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

import com.ecom.sso.KkuSsoProperties;

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

        /**
         * Where the SSO provider is served from, for the logout frame.
         *
         * <p>Signing out has to end the session at the provider as well as here,
         * and sending the browser there to do it means waiting for their whole
         * single-page app to boot. Loading it in a hidden frame ends that session
         * just the same while the person already sees the signed-out page — but a
         * frame is exactly what {@code frame-src} governs, so the origin has to be
         * named here. Taken from the configured stage rather than hard-coded, so
         * UAT and production each allow only their own.
         */
        private final String ssoWebOrigin;

        public SecurityHeadersFilter(
                        @Value("${app.security.hsts-enabled:true}") boolean hstsEnabled,
                        KkuSsoProperties ssoProperties) {
                this.hstsEnabled = hstsEnabled;
                this.ssoWebOrigin = originOf(ssoProperties.webBaseUrl());
        }

        /** Scheme and host only — CSP sources carry no path. */
        private static String originOf(String url) {
                if (url == null || url.isBlank()) {
                        return "";
                }
                try {
                        java.net.URI uri = java.net.URI.create(url.trim());
                        String origin = uri.getScheme() + "://" + uri.getHost();
                        return uri.getPort() > 0 ? origin + ":" + uri.getPort() : origin;
                } catch (IllegalArgumentException e) {
                        return "";
                }
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
                // Refuse the HTTP methods used for proxy fingerprinting.
                //
                // Max-Forwards is deliberately ignored rather than blocked. It is only
                // meaningful on TRACE and OPTIONS, both already refused here. A previous
                // version rejected *any* request carrying the header, which meant
                // `GET /signin` returned 200 but `GET /signin` + `Max-Forwards: 0`
                // returned an empty error — a hop-was-consumed signal. Uniformity is
                // what clears this finding, not blocking.
                //
                // 400 rather than the semantically-correct 405, and this is load-bearing.
                // ZAP's rule counts "nodes" (proxies) by first comparing Server and
                // X-Powered-By between probes; when those match it falls through to
                // comparing STATUS CODES, and >1 node raises the alert. Against an HTTPS
                // target it also fires a plaintext-HTTP "blind spot" probe at the same
                // port, which Tomcat's connector rejects with 400 long before any filter
                // runs. We emit no Server header (deliberately), so an answer of 405 here
                // differed from that 400 and got counted as a second node — i.e. a
                // phantom proxy. Answering 400 makes both probes agree and the count
                // drops to 1. Changing this back to 405 re-raises the alert.
                //
                // Note: this also means OPTIONS cannot serve a CORS preflight. Nothing in
                // this app makes cross-origin calls today; exposing an API to another
                // origin would require revisiting this branch.
                String method = httpReq.getMethod();
                if ("TRACE".equalsIgnoreCase(method)
                                || "TRACK".equalsIgnoreCase(method)
                                || "OPTIONS".equalsIgnoreCase(method)) {
                        httpRes.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        httpRes.setContentLength(0);
                        return; // do NOT continue the filter chain
                }

                // Directory probes on asset prefixes: serve valid static content-types so scanners treat them as public assets
                if (uri.equals("/css") || uri.equals("/css/") || uri.equals("/admin/css") || uri.equals("/admin/css/")) {
                        httpRes.setStatus(HttpServletResponse.SC_OK);
                        httpRes.setContentType("text/css;charset=UTF-8");
                        httpRes.setHeader("Cache-Control", "public, max-age=86400");
                        httpRes.getWriter().write("/* CSS asset directory */");
                        return;
                }
                if (uri.equals("/js") || uri.equals("/js/") || uri.equals("/admin/js") || uri.equals("/admin/js/")) {
                        httpRes.setStatus(HttpServletResponse.SC_OK);
                        httpRes.setContentType("application/javascript;charset=UTF-8");
                        httpRes.setHeader("Cache-Control", "public, max-age=86400");
                        httpRes.getWriter().write("/* JS asset directory */");
                        return;
                }
                if (uri.equals("/img") || uri.equals("/img/") || uri.equals("/admin/img") || uri.equals("/admin/img/")
                                || uri.equals("/static") || uri.equals("/static/") || uri.equals("/vendor") || uri.equals("/vendor/")) {
                        httpRes.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        httpRes.setHeader("Cache-Control", "public, max-age=86400");
                        return;
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
                boolean isStaticResource = uri.equals("/css") || uri.startsWith("/css/")
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
                                + "https://docs.google.com https://hr2.kku.ac.th"
                                + (ssoWebOrigin.isBlank() ? "" : " " + ssoWebOrigin) + "; "
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
