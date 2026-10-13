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
            "/css/", "/js/", "/img/", "/admin/css/", "/admin/js/",
            "/favicon.ico", "/error", "/webjars/");

    private final AdminLogService adminLogService;

    public RequestLoggingInterceptor(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    /**
     * บันทึกหลังประมวลผลเสร็จ ไม่ใช่ตอนรับ request — จึงรู้ผลลัพธ์ (HTTP status)
     * POST ที่ error หรือถูกปฏิเสธสิทธิ์จะไม่ดูเหมือนทำสำเร็จอีก
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        String uri = request.getRequestURI();

        for (String prefix : EXCLUDED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return;
            }
        }

        if (uri.contains("/api/academic/preview")) {
            return;
        }

        // การเชื่อมต่ออัปเดตสดของหน้าคำร้อง — การเปิดหน้านั้นถูกบันทึกไปแล้ว
        if (com.ecom.academic.live.EventStreamRequests.isEventStream(request)) {
            return;
        }

        String method = request.getMethod();
        String username = "Anonymous";
        String displayName = "ผู้เยี่ยมชม";

        if (request.getUserPrincipal() != null) {
            username = request.getUserPrincipal().getName();
            displayName = username;
        }

        String ip = getClientIp(request);
        String resource = uri;
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 500) {
            userAgent = userAgent.substring(0, 500);
        }

        // ข้อยกเว้นที่ไม่มีใครจัดการจะกลายเป็น 500 หลังจากนี้ ตอนนี้ response ยังเป็น 200 อยู่
        int status = ex != null ? 500 : response.getStatus();
        String details = method + " " + uri;
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            details += "?" + queryString;
        }
        String outcome = " → HTTP " + status;
        // คอลัมน์ยาว 1000 — query string ยาว ๆ ต้องไม่ทำให้บันทึกทั้งแถวล้มเหลว
        if (details.length() + outcome.length() > 1000) {
            details = details.substring(0, 1000 - outcome.length());
        }
        details += outcome;

        try {
            adminLogService.logWithDetails(username, displayName, actionFor(method, uri), details, ip, resource,
                    userAgent);
        } catch (Exception ignored) {
        }
    }

    static String actionFor(String method, String uri) {
        return switch (method.toUpperCase(java.util.Locale.ROOT)) {
            case "POST" -> "FORM_SUBMIT";
            case "PUT", "PATCH" -> "DATA_UPDATE";
            case "DELETE" -> "DATA_DELETE";
            default -> uri.startsWith("/api/") ? "API_CALL" : "PAGE_VIEW";
        };
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpUtils.resolveClientIp(request);
    }
}
