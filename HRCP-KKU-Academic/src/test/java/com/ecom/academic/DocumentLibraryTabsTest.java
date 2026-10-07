package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.external.model.KkuRegulationDoc;
import com.ecom.external.repository.KkuRegulationDocRepository;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/** คลังเอกสารของผู้ยื่นแยกแท็บพนักงานมหาวิทยาลัย (หน้า 5546) / ข้าราชการ (หน้า 5532) */
@DisplayName("คลังเอกสาร: แท็บพนักงาน / ข้าราชการ")
class DocumentLibraryTabsTest extends AbstractFlowTest {

    @Autowired
    private KkuRegulationDocRepository docs;

    @AfterEach
    void removeDocs() {
        docs.deleteAll();
    }

    @Test
    @DisplayName("มีเอกสารทั้งสองกลุ่ม — สองแท็บ และหมวดในแท็บข้าราชการไม่มีคำนำหน้าซ้ำ")
    void bothAudiencesGetATab() throws Exception {
        docs.deleteAll();
        docs.save(new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "ข้อบังคับ 2569", "https://hr2.kku.ac.th/a.pdf",
                "a.pdf", 1));
        docs.save(new KkuRegulationDoc("ข้าราชการ · ประกาศ ก.พ.อ.", "ประกาศ ก.พ.อ. ฉบับที่ 3 พ.ศ. 2568",
                "https://hr2.kku.ac.th/b.pdf", "b.pdf", 1001));
        UserDtls applicant = data.applicant();

        String html = mvc.perform(get("/user/academic/documents").with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("doc-audience-tab").contains(">พนักงานมหาวิทยาลัย<").contains(">ข้าราชการ<")
                .contains(">ประกาศ ก.พ.อ.<")
                .doesNotContain("ข้าราชการ · ประกาศ ก.พ.อ.");
    }

    @Test
    @DisplayName("เปิดเอกสารผ่านเซิร์ฟเวอร์ของเรา ไม่ผ่าน Google Docs Viewer — ไฟล์นอก hr2 ไม่ส่งต่อ")
    void documentsOpenThroughOurOwnEndpoint() throws Exception {
        docs.deleteAll();
        KkuRegulationDoc onHr = docs.save(new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "ข้อบังคับ 2569",
                "https://hr2.kku.ac.th/a.pdf", "a.pdf", 1));
        KkuRegulationDoc elsewhere = docs.save(new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "ไฟล์ภายนอก",
                "https://evil.example.com/x.pdf", "x.pdf", 2));
        UserDtls applicant = data.applicant();

        String html = mvc.perform(get("/user/academic/documents").with(user(applicant.getEmail()).roles("USER")))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-pdf-url=\"/user/academic/documents/" + onHr.getId() + "/file\"")
                .doesNotContain("docs.google.com/viewer");

        mvc.perform(get("/user/academic/documents/" + elsewhere.getId() + "/file")
                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
    }
}
