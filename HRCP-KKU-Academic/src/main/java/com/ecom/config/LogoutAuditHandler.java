package com.ecom.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * บันทึกการออกจากระบบลงประวัติกิจกรรม
 *
 * <p>{@code /logout} ถูก Spring Security จัดการทั้งหมดก่อนถึง controller จึงไม่ผ่าน
 * {@link RequestLoggingInterceptor} — ไม่มีตัวนี้ ประวัติจะมีแต่เข้าระบบ ไม่มีออก
 */
@Component
public class LogoutAuditHandler implements LogoutHandler {

    private final AdminLogService adminLogService;
    private final UserRepository userRepository;

    public LogoutAuditHandler(AdminLogService adminLogService, UserRepository userRepository) {
        this.adminLogService = adminLogService;
        this.userRepository = userRepository;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null) {
            // หมดอายุไปแล้ว ไม่มีใครให้บันทึก
            return;
        }
        try {
            String email = authentication.getName();
            UserDtls user = userRepository.findByEmail(email);
            String name = user != null && user.getName() != null ? user.getName() : email;
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 500) {
                userAgent = userAgent.substring(0, 500);
            }
            adminLogService.logWithDetails(email, name, "LOGOUT", "ออกจากระบบ",
                    ClientIpUtils.resolveClientIp(request), request.getRequestURI(), userAgent);
        } catch (Exception ignored) {
            // การบันทึกล้มเหลวต้องไม่ทำให้ออกจากระบบไม่ได้
        }
    }
}
