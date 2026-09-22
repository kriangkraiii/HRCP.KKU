package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentFieldOwnership;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ตัวเลือกที่ติ๊กได้ทีละหนึ่ง ต้องหน้าตาเหมือนกันเสมอไม่ว่าใครบันทึก
 *
 * <p>เอกสารที่ 1 ถามว่าขอประเมินเพื่อไปตำแหน่งใด แล้วเก็บคำตอบเป็นช่องติ๊กสองช่อง
 * ตัวที่ไม่ได้เลือกจึงต้อง "ว่าง" เสมอโดยการออกแบบ เทมเพลตเขียนวงเล็บไว้เองแล้ว
 * ({@code [{{chk1}}] ผู้ช่วยศาสตราจารย์}) ค่าที่เก็บจึงไปโผล่<em>กลางวงเล็บ</em>บนเอกสารจริง
 *
 * <p>เคยพังมาแล้วสองแบบคนละทิศ: ใส่ {@code ☐} ลงไปก็ได้ {@code [☐]} เป็นกล่องซ้อน
 * ในวงเล็บข้าง ๆ {@code [✓]} ส่วนใส่สตริงว่างก็ได้ {@code []} ที่แคบกว่าเพื่อน
 * และที่แย่ที่สุดคือสองเส้นทางบันทึกเคยใช้คนละกติกา เอกสารฉบับเดียวกันจึงหน้าตา
 * ต่างกันตามว่าใครกดบันทึกคนสุดท้าย
 */
@DisplayName("ตัวเลือกที่ติ๊กได้ทีละหนึ่งในเอกสารที่ 1")
class ExclusiveTickNormalizationTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService requestService;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    /** ฟอร์มเอกสารที่ 1 อย่างที่เบราว์เซอร์ส่งมาจริง — ตัวที่ไม่ได้เลือกส่งค่าว่าง */
    private void applicantSavesChoosingAssociate(AcademicRequest request) throws Exception {
        mvc.perform(post("/user/academic/request/" + request.getId() + "/document-1")
                .with(as(applicant)).with(csrf())
                .param("title", "ผู้ช่วยศาสตราจารย์")
                .param("applicant_name", "สมชาย ใจดีวิชาการ")
                .param("employee_type", "ข้าราชการ")
                .param("current_position", "ผู้ช่วยศาสตราจารย์")
                .param("chk1", "")
                .param("chk2", "✓")
                .param("course_code", "CS111111")
                .param("course_name", "intro com")
                .param("academic_year", "1/2569"))
                .andExpect(status().is3xxRedirection());
    }

    private Map<String, String> storedDocumentOne(AcademicRequest request) {
        return requestService.getLatestDocumentData(request.getId(), 1);
    }

    @Test
    @DisplayName("ผู้ยื่นบันทึก: ตัวที่เลือกเป็น ✓ ตัวที่ไม่เลือกเป็นช่องว่าง ไม่ใช่ค่าว่างเปล่า")
    void theApplicantPathWritesASpacerNotAnEmptyString() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);

        applicantSavesChoosingAssociate(request);

        Map<String, String> stored = storedDocumentOne(request);
        assertThat(stored).containsEntry("chk2", "✓");
        assertThat(stored.get("chk1"))
                .as("ว่างเปล่าจะพิมพ์ออกมาเป็น [] ซึ่งแคบกว่า [✓] ข้าง ๆ กันอย่างเห็นได้ชัด")
                .isNotEmpty()
                .isBlank();
        assertThat(stored.get("chk1"))
                .as("กล่อง ☐ จะพิมพ์ออกมาเป็น [☐] คือกล่องซ้อนในวงเล็บ")
                .doesNotContain("☐");
    }

    @Test
    @DisplayName("เจ้าหน้าที่บันทึกทับ: ค่าไม่เปลี่ยน")
    void theOfficerPathLeavesTheChoiceAlone() throws Exception {
        // ผู้ยื่นกรอกตอนยังเป็นแบบร่าง แล้วจึงส่งคำร้อง — หลังจากนั้นแก้เองไม่ได้แล้ว
        AcademicRequest request = data.evaluation(applicant, RequestStatus.DRAFT);
        applicantSavesChoosingAssociate(request);
        requestService.updateStatus(request.getId(), RequestStatus.RECEIVED, officer,
                "ส่งคำร้องเข้าสู่กระบวนการ", false);
        Map<String, String> beforeOfficer = storedDocumentOne(request);
        assertThat(beforeOfficer).as("ผู้ยื่นต้องบันทึกเอกสารที่ 1 ได้ก่อน").isNotNull();

        // เจ้าหน้าที่เปิดเอกสารเดิมแล้วลงเลขที่หนังสือ ซึ่งเป็นช่องของสำนักงาน
        mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                .with(as(officer)).with(csrf())
                .param("memo_no", "อว 660301.26.8/13"))
                .andExpect(status().is3xxRedirection());

        Map<String, String> afterOfficer = storedDocumentOne(request);
        assertThat(afterOfficer)
                .as("เจ้าหน้าที่ลงเลขที่หนังสือแล้ว ตัวเลือกของผู้ยื่นต้องไม่ขยับ")
                .containsEntry("chk1", beforeOfficer.get("chk1"))
                .containsEntry("chk2", beforeOfficer.get("chk2"));
        assertThat(afterOfficer).containsEntry("memo_no", "อว 660301.26.8/13");
    }

    @Test
    @DisplayName("ค่าที่ค้างมาจากของเดิมถูกซ่อมเมื่อบันทึกใหม่")
    void staleBoxGlyphsAreRepairedOnTheNextSave() {
        Map<String, String> stale = Map.of("chk1", "☐", "chk2", "✓");

        Map<String, String> repaired = DocumentFieldOwnership.normalizeExclusiveTicks(
                SignatureModule.ACADEMIC, 1, stale);

        assertThat(repaired).containsEntry("chk2", "✓");
        assertThat(repaired.get("chk1")).isNotEmpty().isBlank();
    }

    @Test
    @DisplayName("ยังไม่ได้เลือกเลย — ไม่เติมอะไรให้ เพราะต้องยังฟ้องว่ากรอกไม่ครบ")
    void nothingChosenYetIsLeftAlone() {
        Map<String, String> untouched = Map.of("chk1", "", "chk2", "");

        assertThat(DocumentFieldOwnership.normalizeExclusiveTicks(
                SignatureModule.ACADEMIC, 1, untouched))
                .as("ถ้าเติมช่องว่างให้ตรงนี้ ด่านตรวจความครบถ้วนจะมองว่าตอบแล้ว")
                .isEqualTo(untouched);
    }

    @Test
    @DisplayName("เอกสารที่ 7 ใช้กล่อง ☑/☐ ของมันเอง ห้ามเหมารวม")
    void documentSevenKeepsItsOwnBoxes() {
        Map<String, String> evaluationBoxes = Map.of("ch1", "☐", "ch2", "☑", "ch3", "☐");

        assertThat(DocumentFieldOwnership.normalizeExclusiveTicks(
                SignatureModule.ACADEMIC, 7, evaluationBoxes))
                .isEqualTo(evaluationBoxes);
    }
}
