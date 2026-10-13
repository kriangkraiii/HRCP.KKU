package com.ecom.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.model.AdminLog;
import com.ecom.repository.AdminLogRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * {@code /logout} จบใน filter ของ Spring Security ไม่ถึง interceptor — ต้องมีตัวบันทึกของตัวเอง
 */
class LogoutIsAuditedTest extends AbstractFlowTest {

    @Autowired
    private AdminLogRepository logs;

    @Test
    @DisplayName("ออกจากระบบแล้วมีแถว LOGOUT ในประวัติกิจกรรม")
    void logoutLeavesARecord() throws Exception {
        String email = "logout-audit-" + System.nanoTime() + "@example.invalid";

        mvc.perform(post("/logout").with(user(email).roles("USER")).with(csrf()));

        // การบันทึกเป็น @Async — รอสั้น ๆ แทนการเปลี่ยน executor ของทั้งชุดทดสอบ
        List<AdminLog> mine = List.of();
        for (int i = 0; i < 50 && mine.isEmpty(); i++) {
            mine = logs.findByAdminEmailOrderByTimestampDesc(email);
            if (mine.isEmpty()) {
                Thread.sleep(100);
            }
        }
        assertThat(mine).extracting(AdminLog::getAction).contains("LOGOUT");
    }
}
