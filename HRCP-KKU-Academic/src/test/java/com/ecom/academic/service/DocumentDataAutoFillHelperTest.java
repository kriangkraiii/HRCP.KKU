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
}
