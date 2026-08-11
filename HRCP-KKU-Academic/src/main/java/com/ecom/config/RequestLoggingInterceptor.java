package com.ecom.config;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/css/", "/js/", "/img/", "/uploads/", "/admin/css/", "/admin/js/",
            "/favicon.ico", "/error", "/webjars/");

    private final AdminLogService adminLogService;

    public RequestLoggingInterceptor(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        for (String prefix : EXCLUDED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }

        if (uri.contains("/api/academic/preview")) {
            return true;
        }

        String method = request.getMethod();
        String username = "Anonymous";
        String displayName = "ผู้เยี่ยมชม";

        if (request.getUserPrincipal() != null) {
            username = request.getUserPrincipal().getName();
            displayName = username;
        }

        String action;
        if ("POST".equalsIgnoreCase(method)) {
            action = "FORM_SUBMIT";
        } else if (uri.startsWith("/api/")) {
            action = "API_CALL";
        } else {
            action = "PAGE_VIEW";
        }

        String ip = getClientIp(request);
        String resource = uri;
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 500) {
            userAgent = userAgent.substring(0, 500);
        }

        String details = method + " " + uri;
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            details += "?" + queryString;
        }

        try {
            adminLogService.logWithDetails(username, displayName, action, details, ip, resource, userAgent);
        } catch (Exception ignored) {
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpUtils.resolveClientIp(request);
    }
}
