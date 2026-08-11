package com.ecom.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared utility for resolving client IP addresses consistently
 * across authentication handlers.
 *
 * <p>Proxy headers are deliberately <em>not</em> read here. {@code X-Forwarded-For}
 * and {@code X-Real-IP} are attacker-controlled on any request that does not
 * pass through a proxy we own, and this value keys the brute-force counters —
 * trusting it let an attacker reset their counter on every attempt by rotating
 * the header.
 *
 * <p>Behind a reverse proxy, set {@code server.forward-headers-strategy=native}
 * (see application.properties) and configure Tomcat's trusted proxy list.
 * Tomcat then validates the headers and rewrites {@code getRemoteAddr()} itself,
 * so this method returns the real client IP without trusting anything the
 * client sent.
 */
public final class ClientIpUtils {

    private ClientIpUtils() {}

    public static String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
