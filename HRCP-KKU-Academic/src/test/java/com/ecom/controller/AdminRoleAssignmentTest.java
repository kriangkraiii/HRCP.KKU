package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * role ที่แอดมินตั้งให้บัญชีได้ (docs/PLAN-bughunt-decisions.md ข้อ 3)
 *
 * <p>เดิมรับค่า role จากฟอร์มตรง ๆ: POST {@code ROLE_STAFF} ได้บัญชีที่หน้าลงนามถือเป็นแอดมิน
 * ทั้งที่ไม่มีฟอร์มไหนให้เลือก ค่าอื่นทำให้บัญชีเข้าใช้งานไม่ได้ และแอดมินลดสิทธิ์ตัวเองได้
 */
@DisplayName("แอดมินตั้ง role ได้เฉพาะค่าที่ระบบรู้จัก และเปลี่ยน role ตัวเองไม่ได้")
class AdminRoleAssignmentTest extends AbstractFlowTest {

    @Autowired
    private UserRepository users;

    private MockMultipartHttpServletRequestBuilder form(String path, UserDtls target, String role) {
        MockMultipartHttpServletRequestBuilder req = multipart(path);
        req.file(new MockMultipartFile("img", new byte[0]));
        if (target != null) {
            req.param("id", String.valueOf(target.getId()))
                    .param("email", target.getEmail())
                    .param("firstName", "ชื่อ")
                    .param("lastName", "นามสกุล");
        }
        return (MockMultipartHttpServletRequestBuilder) req.param("role", role).param("type", "1").with(csrf());
    }

    @Test
    @DisplayName("role ที่ระบบไม่รู้จัก (ROLE_STAFF) — ถูกปฏิเสธ role เดิมอยู่ต่อ")
    void anUnknownRoleIsRefused() throws Exception {
        UserDtls admin = data.admin();
        UserDtls applicant = data.applicant();

        mvc.perform(form("/admin/update-user", applicant, "ROLE_STAFF").with(user(admin.getEmail()).roles("ADMIN")));

        assertThat(users.findById(applicant.getId()).orElseThrow().getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("แอดมินลดสิทธิ์ตัวเองเป็นผู้ใช้ทั่วไป — ถูกปฏิเสธ")
    void anAdminCannotChangeTheirOwnRole() throws Exception {
        UserDtls admin = data.admin();

        mvc.perform(form("/admin/update-admin", admin, "ROLE_USER").with(user(admin.getEmail()).roles("ADMIN")));

        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("เปลี่ยน role ของคนอื่นเป็นค่าที่ระบบรู้จัก — ยังทำได้ตามปกติ")
    void changingSomeoneElsesRoleStillWorks() throws Exception {
        UserDtls admin = data.admin();
        UserDtls applicant = data.applicant();

        mvc.perform(form("/admin/update-user", applicant, UserDtls.ROLE_EXTERNAL)
                .with(user(admin.getEmail()).roles("ADMIN")));

        assertThat(users.findById(applicant.getId()).orElseThrow().getRole()).isEqualTo(UserDtls.ROLE_EXTERNAL);
    }

    @Test
    @DisplayName("สร้างบัญชีด้วย role ที่ระบบไม่รู้จัก — ไม่ถูกสร้าง")
    void anAccountWithAnUnknownRoleIsNotCreated() throws Exception {
        UserDtls admin = data.admin();

        mvc.perform(form("/admin/save-admin", null, "ROLE_STAFF")
                .param("email", "new.staff@kku.ac.th").param("firstName", "ใหม่").param("lastName", "ทดสอบ")
                .param("password", "Passw0rd!x")
                .with(user(admin.getEmail()).roles("ADMIN")));

        assertThat(users.findByEmailIgnoreCase("new.staff@kku.ac.th")).isNull();
    }
}
