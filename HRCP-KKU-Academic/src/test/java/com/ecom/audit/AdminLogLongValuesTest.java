package com.ecom.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.model.AdminLog;
import com.ecom.repository.AdminLogRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * ค่ายาวเกินคอลัมน์ต้องไม่ทำให้บันทึกกิจกรรมหายทั้งแถว
 *
 * <p>อีเมลที่พิมพ์ตอนเข้าสู่ระบบไม่สำเร็จ, URL และ User-Agent มาจากผู้ใช้ทั้งหมด บันทึกทำบนเธรด async
 * เกินความยาวคอลัมน์แล้วการบันทึกล้มเงียบ ๆ — คนที่ส่งค่ายาว ๆ มาจึงไม่ทิ้งร่องรอยในบันทึกกิจกรรม
 */
@DisplayName("บันทึกกิจกรรม: ค่ายาวถูกตัด ไม่ทำให้แถวหาย")
class AdminLogLongValuesTest extends AbstractFlowTest {

    @Autowired
    private AdminLogRepository adminLogRepository;

    @Test
    @DisplayName("อีเมล URL และ User-Agent ที่ยาวเกิน — ยังบันทึกได้")
    void overlongValuesAreClippedNotLost() {
        String email = "a".repeat(400) + "@kku.ac.th";
        AdminLog saved = adminLogRepository.saveAndFlush(new AdminLog(email, "ผู้พยายามเข้าสู่ระบบ", "LOGIN_FAILED",
                "ร".repeat(1500), "10.0.0.1", "/" + "x".repeat(800), "UA".repeat(400)));

        AdminLog read = adminLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(read.getAdminEmail()).hasSize(255).startsWith("aaa");
        assertThat(read.getDetails()).hasSize(1000);
        assertThat(read.getResource()).hasSize(500);
        assertThat(read.getUserAgent()).hasSize(500);
    }
}
