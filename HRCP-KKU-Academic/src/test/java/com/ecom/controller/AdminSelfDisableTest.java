package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * แอดมินระงับบัญชีของตัวเองไม่ได้ — เหมือนที่ลบบัญชีตัวเองไม่ได้
 *
 * <p>หน้ารายชื่อแอดมินแสดงสวิตช์ให้ทุกแถวรวมแถวของตัวเอง แอดมินคนสุดท้ายที่เผลอกดจะล็อกทุกคนออกจากงานแอดมิน
 */
@DisplayName("แอดมินระงับบัญชีตัวเองไม่ได้")
class AdminSelfDisableTest extends AbstractFlowTest {

    @Autowired
    private UserRepository users;

    @Test
    @DisplayName("ระงับบัญชีตัวเอง — ถูกปฏิเสธ บัญชียังใช้งานได้")
    void anAdminCannotDisableThemselves() throws Exception {
        UserDtls admin = data.admin();

        mvc.perform(post("/admin/updateSts").param("status", "false").param("id", String.valueOf(admin.getId()))
                .param("type", "2").with(csrf()).with(user(admin.getEmail()).roles("ADMIN")));

        assertThat(users.findById(admin.getId()).orElseThrow().getIsEnable()).isTrue();
    }

    @Test
    @DisplayName("ระงับบัญชีผู้ใช้อื่น — ยังทำได้ตามปกติ")
    void anAdminCanStillDisableSomeoneElse() throws Exception {
        UserDtls admin = data.admin();
        UserDtls applicant = data.applicant();

        mvc.perform(post("/admin/updateSts").param("status", "false").param("id", String.valueOf(applicant.getId()))
                .param("type", "1").with(csrf()).with(user(admin.getEmail()).roles("ADMIN")));

        assertThat(users.findById(applicant.getId()).orElseThrow().getIsEnable()).isFalse();
    }
}
