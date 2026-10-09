package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionRequest;
import com.ecom.model.UserDtls;

class DocumentDataAutoFillHelperTest {

    private DocumentDataAutoFillHelper autoFillHelper;
    private UserDtls testUser;

    @BeforeEach
    void setUp() {
        // None of the cases here link a teaching evaluation, so the summariser is
        // never asked anything; the stub is only what the constructor requires.
        // The evaluation path has its own test, EvaluationAutoFillTest, against
        // the real service and real documents.
        autoFillHelper = new DocumentDataAutoFillHelper(
                org.mockito.Mockito.mock(AcademicRequestService.class));

        testUser = new UserDtls();
        testUser.setId(10);
        testUser.setTitle("ผู้ช่วยศาสตราจารย์");
        testUser.setFirstName("สมชาย");
        testUser.setLastName("ใจดีวิชาการ");
        testUser.setFirstNameEn("Somchai");
        testUser.setLastNameEn("Jaideewichakan");
        testUser.setAcademicPosition("ผู้ช่วยศาสตราจารย์");
        testUser.setAcademicPositionEn("Assistant Professor");
        testUser.setEmail("user@user.com");
        testUser.setMobileNumber("081-234-5678");
    }

    @Test
    void testPreFilledAcademicDoc1_fromUserProfile() {
        AcademicRequest request = new AcademicRequest();
        request.setId(101L);
        request.setApplicant(testUser);
        request.setDocuments(new ArrayList<>());

        Map<String, String> data = autoFillHelper.getPreFilledAcademicDocData(request, 1, null);

        assertNotNull(data);
        assertEquals("ผู้ช่วยศาสตราจารย์", data.get("title"));
        assertEquals("สมชาย ใจดีวิชาการ", data.get("applicant_name"));
        assertEquals("ผู้ช่วยศาสตราจารย์", data.get("current_position"));
        assertEquals("user@user.com", data.get("applicant_email"));
        assertEquals("081-234-5678", data.get("applicant_phone"));
        assertEquals("วิทยาลัยการคอมพิวเตอร์", data.get("faculty"));
        assertEquals("สาขาวิชาวิทยาการคอมพิวเตอร์", data.get("department"));
    }

    @Test
    void testPreFilledAcademicDoc2_inheritsFromDoc1() {
        AcademicRequest request = new AcademicRequest();
        request.setId(102L);
        request.setApplicant(testUser);

        List<AcademicDocument> docs = new ArrayList<>();
        AcademicDocument doc1 = new AcademicDocument();
        doc1.setDocumentType(1);
        doc1.setJsonData("{\"course_code\":\"CP351101\",\"course_name\":\"Software Architecture\",\"academic_year\":\"2568\",\"chk1\":\"✓\"}");
        docs.add(doc1);
        request.setDocuments(docs);

        Map<String, String> data = autoFillHelper.getPreFilledAcademicDocData(request, 2, null);

        assertNotNull(data);
        assertEquals("CP351101", data.get("course_code"));
        assertEquals("Software Architecture", data.get("course_name"));
        assertEquals("2568", data.get("academic_year"));
        assertEquals("✓", data.get("chk1"));
        assertEquals("สมชาย ใจดีวิชาการ", data.get("applicant_name"));
    }

    @Test
    void testPreFilledAcademicDoc5_inheritsCommitteeFromDoc3AndDoc4() {
        AcademicRequest request = new AcademicRequest();
        request.setId(103L);
        request.setApplicant(testUser);

        List<AcademicDocument> docs = new ArrayList<>();
        AcademicDocument doc1 = new AcademicDocument();
        doc1.setDocumentType(1);
        doc1.setJsonData("{\"course_code\":\"CP351102\",\"course_name\":\"Cloud Computing\",\"academic_year\":\"2568\"}");
        docs.add(doc1);

        AcademicDocument doc4 = new AcademicDocument();
        doc4.setDocumentType(4);
        doc4.setJsonData("{\"committee_1_name\":\"ศ.ดร.วิชาการ ดีเด่น\",\"committee_2_name\":\"รศ.ดร.นวัตกรรม ก้าวหน้า\",\"committee_3_name\":\"ผศ.ดร.เทคโนโลยี มั่นคง\",\"meeting_date\":\"15 กันยายน 2568\",\"meeting_room\":\"ห้องประชุม 1\"}");
        docs.add(doc4);
        request.setDocuments(docs);

        Map<String, String> data = autoFillHelper.getPreFilledAcademicDocData(request, 5, null);

        assertNotNull(data);
        assertEquals("ศ.ดร.วิชาการ ดีเด่น", data.get("committee_1_name"));
        assertEquals("รศ.ดร.นวัตกรรม ก้าวหน้า", data.get("committee_2_name"));
        assertEquals("ผศ.ดร.เทคโนโลยี มั่นคง", data.get("committee_3_name"));
        assertEquals("15 กันยายน 2568", data.get("meeting_date"));
        assertEquals("ห้องประชุม 1", data.get("meeting_room"));
        assertEquals("CP351102", data.get("course_code"));
    }

    @Test
    void testPreFilledAcademicDoc_overlayExistingJson_highestPriority() {
        AcademicRequest request = new AcademicRequest();
        request.setId(104L);
        request.setApplicant(testUser);

        String savedJson = "{\"applicant_name\":\"ศ.เกียรติคุณ ดร.สมชาย พิเศษ\",\"custom_note\":\"หมายเหตุเฉพาะฉบับนี้\"}";
        Map<String, String> data = autoFillHelper.getPreFilledAcademicDocData(request, 1, savedJson);

        assertNotNull(data);
        // Saved user input takes priority over profile defaults
        assertEquals("ศ.เกียรติคุณ ดร.สมชาย พิเศษ", data.get("applicant_name"));
        assertEquals("หมายเหตุเฉพาะฉบับนี้", data.get("custom_note"));
        // Unsaved fields still get profile defaults
        assertEquals("user@user.com", data.get("applicant_email"));
    }

    @Test
    void testPreFilledPositionDoc1_fromUserProfile() {
        PositionRequest request = new PositionRequest();
        request.setId(201L);
        request.setApplicant(testUser);
        request.setDocuments(new ArrayList<>());

        Map<String, String> data = autoFillHelper.getPreFilledPositionDocData(request, 1, null);

        assertNotNull(data);
        assertEquals("ผู้ช่วยศาสตราจารย์", data.get("title"));
        assertEquals("สมชาย ใจดีวิชาการ", data.get("applicant_name"));
        assertEquals("Somchai Jaideewichakan", data.get("applicant_name_en"));
        assertEquals("ผู้ช่วยศาสตราจารย์", data.get("current_position"));
        assertEquals("Assistant Professor", data.get("current_position_en"));
        assertEquals("user@user.com", data.get("applicant_email"));
        assertEquals("081-234-5678", data.get("tel"));
    }

    @Test
    void testPreFilledPositionDoc2_inheritsFromDoc1() {
        PositionRequest request = new PositionRequest();
        request.setId(202L);
        request.setApplicant(testUser);

        List<PositionDocument> docs = new ArrayList<>();
        PositionDocument doc1 = new PositionDocument();
        doc1.setDocumentType(1);
        doc1.setJsonData("{\"target_position\":\"รองศาสตราจารย์\",\"discipline\":\"วิทยาการคอมพิวเตอร์\",\"department\":\"สาขาวิชาวิทยาการคอมพิวเตอร์\",\"faculty\":\"วิทยาลัยการคอมพิวเตอร์\"}");
        docs.add(doc1);
        request.setDocuments(docs);

        Map<String, String> data = autoFillHelper.getPreFilledPositionDocData(request, 2, null);

        assertNotNull(data);
        assertEquals("รองศาสตราจารย์", data.get("target_position"));
        assertEquals("วิทยาการคอมพิวเตอร์", data.get("discipline"));
        assertEquals("สมชาย ใจดีวิชาการ", data.get("applicant_name"));
        assertEquals("วิทยาลัยการคอมพิวเตอร์", data.get("faculty"));
    }

    /**
     * เอกสารที่ 7: ค่าที่ระบบดึงมาล็อกไว้และชนะค่าที่บันทึกไว้เสมอ — รวมถึงเอกสารที่บันทึกไว้ก่อนมีการดึงข้อมูล
     * ซึ่งฟอร์มเก็บช่องที่ไม่ติ๊กเป็น ☐ ทั้งกลุ่ม ส่วนช่องที่ระบบไม่รู้ค่ายังเป็นของเจ้าหน้าที่
     */
    @Test
    void doc7LockedValuesBeatWhatWasSaved() {
        AcademicRequestService evaluations = org.mockito.Mockito.mock(AcademicRequestService.class);
        org.mockito.Mockito.when(evaluations.summarize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.ecom.academic.dto.EvaluationSummary(1L, "EV-1", null, null, null, null,
                        "ชำนาญพิเศษ", null, null, null, null, null, null));
        DocumentDataAutoFillHelper helper = new DocumentDataAutoFillHelper(evaluations);

        PositionRequest request = new PositionRequest();
        request.setId(203L);
        request.setApplicant(testUser);
        request.setTargetPosition("รองศาสตราจารย์");
        request.setLinkedEvaluation(new AcademicRequest());
        PositionDocument doc1 = new PositionDocument();
        doc1.setDocumentType(1);
        doc1.setJsonData("{\"department\":\"สาขาวิชาวิทยาการคอมพิวเตอร์\",\"assoc_research_working_1\":\"งานวิจัย A\"}");
        request.setDocuments(new ArrayList<>(List.of(doc1)));

        String saved = """
                {"is_req_asst":"☑","is_req_assoc":"☐",
                 "is_teach_asst":"☐","is_teach_assoc":"☐","is_teach_prof":"☐",
                 "eval_skilled":"☑","eval_highly_skilled":"☐",
                 "is_req_03":"☐","email":"typo@kku.ac.th","total_stories":"4",
                 "hr_officer_name":"เจ้าหน้าที่ผู้ตรวจ","boss_eval_10_sets":"☑"}""";
        Map<String, String> data = helper.getPreFilledPositionDocData(request, 7, saved);

        assertEquals("☑", data.get("is_req_assoc"));
        assertEquals("☐", data.get("is_req_asst"), "ติ๊กซ้อนในกลุ่มเดียวกันไม่ได้");
        assertEquals("☑", data.get("is_teach_assoc"));
        assertEquals("☑", data.get("eval_highly_skilled"), "ระดับตามผลประเมินการสอน");
        assertEquals("☐", data.get("eval_skilled"));
        // เอกสารประกอบ: ติ๊กให้ตั้งต้น แต่เจ้าหน้าที่เอาออกได้เมื่อเอกสารจริงไม่ครบ
        assertEquals("☐", data.get("is_req_03"), "เจ้าหน้าที่เอาติ๊กออกไว้");
        assertEquals("☑", data.get("is_req_05"), "ยังไม่เคยบันทึก — ติ๊กตามเอกสารที่ 1 ที่มีในระบบ");
        assertEquals("☑", data.get("teaching_eval_1_set"));
        assertEquals("user@user.com", data.get("email"));
        assertEquals("งานวิจัย A", data.get("research_working_title_1"));
        // ช่องที่ระบบไม่รู้ค่า และจำนวนผลงานรวม ยังเป็นของเจ้าหน้าที่
        assertEquals("4", data.get("total_stories"));
        assertEquals("เจ้าหน้าที่ผู้ตรวจ", data.get("hr_officer_name"));
        assertEquals("☑", data.get("boss_eval_10_sets"));

        Map<String, String> locked = helper.lockedFields(request, 7);
        assertTrue(locked.containsKey("email"));
        assertTrue(!locked.containsKey("total_stories") && !locked.containsKey("hr_officer_name"));
        assertTrue(!locked.containsKey("is_req_03") && !locked.containsKey("teaching_eval_1_set"),
                "เอกสารประกอบต้องไม่ล็อก");
    }

    @Test
    void doc7DraftsCannotOverwriteLockedFields() throws Exception {
        PositionRequest request = new PositionRequest();
        request.setId(204L);
        request.setApplicant(testUser);
        request.setTargetPosition("รองศาสตราจารย์");
        request.setDocuments(new ArrayList<>());

        String pinned = autoFillHelper.pinLockedFieldsInJson(request, 7,
                "{\"applicant_firstname\":\"แก้ชื่อ\",\"is_teach_asst\":\"☑\",\"reason_if_none\":\"หมายเหตุ\"}");
        Map<String, String> data = new com.fasterxml.jackson.databind.ObjectMapper().readValue(pinned,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});

        assertEquals("สมชาย", data.get("applicant_firstname"));
        assertEquals("☐", data.get("is_teach_asst"));
        assertEquals("☑", data.get("is_teach_assoc"));
        assertEquals("หมายเหตุ", data.get("reason_if_none"));
    }
}
