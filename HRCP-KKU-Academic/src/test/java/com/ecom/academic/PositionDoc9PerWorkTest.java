package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionDocTypes;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เอกสารที่ 9 เฟส 2 แยกฉบับตามงานวิจัยในเอกสารที่ 1 — เอกสารแนบท้ายข้อบังคับ มข. พ.ศ. 2569 ข้อ 3.3–3.4
 * ให้ยื่นแบบแสดงหลักฐานการมีส่วนร่วมทุกผลงาน และผู้ประพันธ์ของแต่ละเรื่องลงนามรับรองฉบับของเรื่องนั้น
 */
@DisplayName("เฟส 2 เอกสารที่ 9: หนึ่งฉบับต่องานวิจัย")
class PositionDoc9PerWorkTest extends AbstractFlowTest {

    private static final String DOC1 = """
            {"assoc_research_working_1":"งานวิจัยเรื่องแรก",
             "assoc_research_working_3":"งานวิจัยเรื่องที่สาม",
             "assoc_other_working_1":"ผลงานลักษณะอื่น",
             "asst_research_working_1":"งานของตำแหน่งอื่น"}""";

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private com.ecom.academic.repository.PositionDocumentRepository positionDocuments;

    private UserDtls applicant;
    private PositionRequest request;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null, "รองศาสตราจารย์");
        data.positionDocument(request, 1, DOC1);
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor asApplicant(UserDtls who) {
        return user(who.getEmail()).roles("USER");
    }

    @Test
    @DisplayName("หนึ่งฉบับต่องานวิจัยของตำแหน่งที่ขอ เลขฉบับตามเลขแถวในเอกสารที่ 1")
    void oneCopyPerResearchWorkOfTheTargetRank() {
        assertThat(positionService.workCopies(request))
                .containsExactly(Map.entry(901, "งานวิจัยเรื่องแรก"), Map.entry(903, "งานวิจัยเรื่องที่สาม"));
        assertThat(positionService.applicantDocTypes(request)).containsExactly(1, 2, 3, 4, 6, 901, 903);
    }

    @Test
    @DisplayName("ทุกฉบับใช้ช่องลงนามของเอกสารที่ 9 และต้องลงนามแยกกัน")
    void eachCopyIsSignedSeparately() {
        assertThat(SignatureAnchorRegistry.slotsOf(SignatureModule.POSITION, 903))
                .isEqualTo(SignatureAnchorRegistry.slotsOf(SignatureModule.POSITION, 9));
        assertThat(signatureWorkflow.getUnsignedApplicantDocTypes(SignatureModule.POSITION, request.getId(),
                positionService.applicantDocTypes(request))).contains(901, 903);
    }

    @Test
    @DisplayName("เปิดเอกสารที่ 9 → ไปฉบับแรก เปิดฉบับที่ไม่มีงานวิจัย → ไปฉบับแรกเช่นกัน")
    void bareDocumentNineOpensTheFirstCopy() throws Exception {
        String base = "/user/position/request/" + request.getId() + "/document/";
        mvc.perform(get(base + "9").with(asApplicant(applicant)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(base + "901"));
        mvc.perform(get(base + "902").with(asApplicant(applicant)))
                .andExpect(redirectedUrl(base + "901"));
    }

    @Test
    @DisplayName("หน้าฉบับที่ 3: ชื่อผลงานล็อกตามเอกสารที่ 1 และบันทึกลงฉบับของตัวเอง")
    void aCopyPageShowsItsLockedTitle() throws Exception {
        String page = mvc.perform(get("/user/position/request/" + request.getId() + "/document/903")
                .with(asApplicant(applicant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("งานวิจัยที่ <span>3</span>")
                .containsPattern("name=\"title_name\"[^>]*value=\"งานวิจัยเรื่องที่สาม\"[^>]*readonly")
                .contains("action=\"/user/position/request/" + request.getId() + "/document/903\"")
                .contains("/api/draft/position/" + request.getId() + "/903");
    }

    @Test
    @DisplayName("บันทึกร่างอัตโนมัติ: ชื่อผลงานถูกเขียนทับด้วยชื่อในเอกสารที่ 1 ส่วนเอกสารที่ 9 ฉบับรวมบันทึกไม่ได้")
    void autoDraftPinsTheTitle() throws Exception {
        mvc.perform(post("/api/draft/position/" + request.getId() + "/903")
                .with(asApplicant(applicant)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title_name\":\"ชื่อที่พิมพ์เอง\",\"role_des1\":\"ออกแบบการทดลอง\"}"))
                .andExpect(status().isOk());

        assertThat(positionService.getLatestDocumentData(request.getId(), 903))
                .containsEntry("title_name", "งานวิจัยเรื่องที่สาม")
                .containsEntry("role_des1", "ออกแบบการทดลอง");
        assertThat(positionService.getLatestDocumentData(request.getId(), 901)).isNull();
        assertThat(positionService.canApplicantEditDocument(request, 9)).isFalse();
    }

    @Test
    @DisplayName("เอางานวิจัยออกจากเอกสารที่ 1 — ฉบับนั้นไม่ต้องใช้แล้ว")
    void removingAWorkDropsItsCopy() {
        // ผู้ยื่นแก้เอกสารที่ 1 — การบันทึกแก้แถวเดิม ไม่ได้เพิ่มแถวใหม่
        var doc1 = positionService.getDocumentsByType(request.getId(), 1).get(0);
        doc1.setJsonData("{\"assoc_research_working_3\":\"งานวิจัยเรื่องที่สาม\"}");
        positionDocuments.save(doc1);

        assertThat(positionService.applicantDocTypes(request)).containsExactly(1, 2, 3, 4, 6, 903);
    }

    @Test
    @DisplayName("ยังไม่มีงานวิจัยในเอกสารที่ 1 — ไม่มีฉบับให้กรอก หน้าคำร้องบอกให้กรอกเอกสารที่ 1 ก่อน")
    void noResearchYet() throws Exception {
        PositionRequest empty = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null, "รองศาสตราจารย์");

        assertThat(positionService.applicantDocTypes(empty)).containsExactly(1, 2, 3, 4, 6);
        String page = mvc.perform(get("/user/position/request/" + empty.getId()).with(asApplicant(applicant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("กรอกงานวิจัยในเอกสารที่ 1 ก่อน");
    }

    @Test
    @DisplayName("หน้าคำร้องแสดงเอกสารที่ 9 แยกตามงานวิจัย")
    void requestPageListsEachWork() throws Exception {
        String page = mvc.perform(get("/user/position/request/" + request.getId()).with(asApplicant(applicant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("งานวิจัยที่ 1").contains("งานวิจัยที่ 3")
                .contains("/document/901").contains("/document/903")
                .contains("งานวิจัยเรื่องที่สาม");
    }

    @Test
    @DisplayName("ผู้นิพนธ์ร่วมเป็นพร้อมผู้ประพันธ์อันดับแรกไม่ได้ — พิมพ์เฉพาะผู้นิพนธ์ร่วม ช่องที่ไม่ติ๊กเป็นกล่องว่าง")
    void coauthorExcludesTheOtherTwoInPrint() throws IOException {
        Map<String, String> placeholders = new HashMap<>(Map.of("chk_ firstauthor", "☑", "chk_coauthor", "☑"));
        DocumentGenerationService.normalizePhase2(9, placeholders);
        assertThat(placeholders).containsEntry("chk_ firstauthor", "☐")
                .containsEntry("chk_corresp", "☐")
                .containsEntry("chk_coauthor", "☑");

        String xml = documentXml(new DocumentGenerationService().generateP2PreviewDocx(
                PositionDocTypes.copyType(3), "{\"title_name\":\"งานวิจัยเรื่องที่สาม\",\"chk_corresp\":\"☑\"}"));
        assertThat(xml).contains("งานวิจัยเรื่องที่สาม").contains("☑").contains("☐");
    }

    private static String documentXml(byte[] docx) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zis.transferTo(out);
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("no document.xml");
    }
}
