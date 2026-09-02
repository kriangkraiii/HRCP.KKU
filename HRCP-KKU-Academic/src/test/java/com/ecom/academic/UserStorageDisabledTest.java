package com.ecom.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * คลังไฟล์ส่วนตัวของผู้ยื่นถูกยกเลิกทั้งฟีเจอร์ เหลือใช้เฉพาะฝั่งแอดมิน
 *
 * <p>โค้ดทั้งชุดถูกคอมเมนต์ไว้ ตาราง user_file/user_folder ถูก drop ด้วย
 * V16__drop_user_storage.sql และไฟล์ใน uploads/user-storage/ ถูกลบทิ้ง
 *
 * <p>สิ่งที่ชุดนี้กันไว้คือการปลดคอมเมนต์กลับมาโดยไม่ตั้งใจ ถ้าใครเผลอเปิด
 * controller กลับ เส้นทางจะกลับมาตอบ 200 แทน 404 แล้วเทสต์จะดังทันที
 * ก่อนที่แอปจะไปพังตอน start เพราะ ddl-auto=validate หาตารางไม่เจอ
 *
 * <p>อีกครึ่งหนึ่งคือกันการปิดเกินขอบเขต เมนูอื่นใต้ /user/academic และ
 * คลังไฟล์ฝั่งแอดมินต้องไม่ได้รับผลกระทบ
 */
@DisplayName("คลังไฟล์ผู้ยื่นถูกยกเลิก เหลือใช้แค่แอดมิน")
class UserStorageDisabledTest extends AbstractFlowTest {

    private static final String APPLICANT = "applicant@kku.ac.th";

    @Test
    @DisplayName("หน้าคลังไฟล์ต้องไม่มีอยู่แล้ว")
    void theStoragePageIsGone() throws Exception {
        mvc.perform(get("/user/academic/storage").with(user(APPLICANT).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ถังขยะและดาวน์โหลดของคลังไฟล์ต้องไม่มีอยู่แล้ว")
    void theTrashAndDownloadRoutesAreGone() throws Exception {
        mvc.perform(get("/user/academic/storage/trash").with(user(APPLICANT).roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/user/academic/storage/download/1").with(user(APPLICANT).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API อัปโหลดแบบแบ่งชิ้นเหลือให้แอดมินใช้ ผู้ยื่นต้องถูกปฏิเสธ")
    void theChunkedUploadApiIsAdminOnly() throws Exception {
        mvc.perform(post("/api/upload/init")
                .param("filename", "x.pdf")
                .param("fileSize", "1024")
                .param("totalChunks", "1")
                .with(csrf())
                .with(user(APPLICANT).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("เมนูซ้ายต้องไม่มีลิงก์ที่เก็บไฟล์ของฉันเหลืออยู่")
    void theSidebarLinkIsGone() throws Exception {
        UserDtls applicant = data.applicant();

        String page = mvc.perform(get("/user/academic/dashboard")
                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(page)
                .as("ลิงก์คลังไฟล์ส่วนตัวต้องไม่ถูกเรนเดอร์ออกมา")
                .doesNotContain("/user/academic/storage");
    }

    @Test
    @DisplayName("เมนูอื่นใต้ /user/academic ต้องไม่โดนปิดตามไปด้วย")
    void theRestOfTheApplicantAreaStillWorks() throws Exception {
        mvc.perform(get("/user/academic/documents").with(user(APPLICANT).roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("คลังไฟล์ฝั่งแอดมินต้องยังใช้งานได้ตามปกติ")
    void theAdminFileManagerStillWorks() throws Exception {
        UserDtls admin = data.admin();

        mvc.perform(get("/admin/file-manager")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("จัดการไฟล์")));
    }
}
