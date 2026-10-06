package com.ecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.model.UserDtls;
import com.ecom.repository.LoginAnnouncementRepository;
import com.ecom.service.LoginAnnouncementService;
import com.ecom.support.AbstractFlowTest;

@DisplayName("ประกาศหน้าเข้าสู่ระบบ — แอดมินพิมพ์เอง เลือกแสดง/ไม่แสดง")
class LoginAnnouncementTest extends AbstractFlowTest {

    private static final String URL = "/admin/academic/settings/login-announcement";
    private static final String NOTICE = "ระบบจะปิดปรับปรุงวันเสาร์ที่ 10 ตุลาคม 2569\nเวลา 18.00–22.00 น.";

    @Autowired
    private LoginAnnouncementRepository repository;

    @Autowired
    private LoginAnnouncementService service;

    private UserDtls admin;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        admin = data.admin();
    }

    private String loginPage() throws Exception {
        return mvc.perform(get("/signin")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void adminSaves(boolean enabled, String message) throws Exception {
        mvc.perform(post(URL).with(user(admin.getEmail()).roles("ADMIN")).with(csrf())
                .param("enabled", String.valueOf(enabled))
                .param("message", message))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("ยังไม่เคยตั้ง — หน้าเข้าสู่ระบบไม่มีประกาศ")
    void nothingByDefault() throws Exception {
        assertThat(loginPage()).doesNotContain("login-announcement");
    }

    @Test
    @DisplayName("เปิดแสดง — ข้อความขึ้นบนหน้าเข้าสู่ระบบ ปิดแล้วหาย แต่ข้อความยังเก็บไว้")
    void toggleShowsAndHides() throws Exception {
        adminSaves(true, NOTICE);
        assertThat(loginPage()).contains("login-announcement").contains("ระบบจะปิดปรับปรุงวันเสาร์ที่ 10 ตุลาคม 2569");

        adminSaves(false, NOTICE);
        assertThat(loginPage()).doesNotContain("login-announcement");
        assertThat(service.current().getMessage()).isEqualTo(NOTICE);
        assertThat(mvc.perform(get(URL).with(user(admin.getEmail()).roles("ADMIN")))
                .andReturn().getResponse().getContentAsString())
                .contains("ระบบจะปิดปรับปรุงวันเสาร์ที่ 10 ตุลาคม 2569");
    }

    @Test
    @DisplayName("เปิดแสดงแต่ไม่มีข้อความ — ไม่บันทึก")
    void enablingAnEmptyNoticeIsRefused() throws Exception {
        adminSaves(true, "   ");
        assertThat(service.current().isEnabled()).isFalse();
        assertThat(loginPage()).doesNotContain("login-announcement");
    }

    @Test
    @DisplayName("ข้อความเป็นข้อความล้วน — แท็ก HTML ไม่ถูกตีความ")
    void htmlIsEscaped() throws Exception {
        adminSaves(true, "<script>alert(1)</script>");
        assertThat(loginPage()).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>alert(1)");
    }

    @Test
    @DisplayName("ผู้ใช้ทั่วไปแก้ประกาศไม่ได้")
    void onlyAdminsMayChangeIt() throws Exception {
        UserDtls applicant = data.applicant();
        mvc.perform(post(URL).with(user(applicant.getEmail()).roles("USER")).with(csrf())
                .param("enabled", "true").param("message", NOTICE));
        assertThat(service.current().isEnabled()).isFalse();
    }
}
