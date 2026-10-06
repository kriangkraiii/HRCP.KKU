package com.ecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * Comments stay in the source but never reach the browser (ZAP "Suspicious
 * Comments"). Each check pairs a comment that must be gone with code beside it
 * that must still be there, so an empty response cannot pass.
 */
@DisplayName("คอมเมนต์ JS ไม่ถูกส่งไปถึงเบราว์เซอร์")
class ServedScriptsHaveNoCommentsTest extends AbstractFlowTest {

    @Test
    @DisplayName("ไฟล์ /js — ตัดคอมเมนต์ โค้ดยังอยู่")
    void staticScripts() throws Exception {
        String js = mvc.perform(get("/js/theme-switcher.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(js).doesNotContain("Get the user's preference").doesNotContain("Persists preference per-user")
                .contains("function");
    }

    @Test
    @DisplayName("สคริปต์ในหน้า — ตัดคอมเมนต์ โค้ดยังอยู่")
    void inlineScripts() throws Exception {
        UserDtls admin = data.admin();
        String html = mvc.perform(get("/admin/academic/dashboard").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("Anti-flash: apply user's server-side theme")
                .doesNotContain("Detect active language from cookie")
                .contains("<script");
    }

    @Test
    @DisplayName("ค่า type แปลก ๆ ใน URL ไม่ถูกเก็บไว้ใช้ในหน้า")
    void unknownRequestTypeFallsBackToDefaultTab() throws Exception {
        UserDtls admin = data.admin();
        String html = mvc.perform(get("/admin/academic/requests").param("type", "x\"onmouseover=alert(1)")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("onmouseover=alert(1)");
    }
}
