package com.ecom.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared utility for resolving client IP addresses consistently
 * across authentication handlers. Checks proxy headers in order:
 * X-Forwarded-For → X-Real-IP → getRemoteAddr().
 */
public final class ClientIpUtils {

    private static final java.util.regex.Pattern IP_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-fA-F.:]+$");

    private ClientIpUtils() {}

    public static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String firstIp = xff.split(",")[0].trim();
            if (isValidIpFormat(firstIp)) {
                return firstIp;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && isValidIpFormat(realIp.trim())) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isValidIpFormat(String ip) {
        return ip != null && ip.length() <= 45 && IP_PATTERN.matcher(ip).matches();
    }
}
