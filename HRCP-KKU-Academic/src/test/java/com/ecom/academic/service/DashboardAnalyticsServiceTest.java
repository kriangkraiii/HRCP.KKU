package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ecom.academic.dto.EvaluationExpiryItem;
import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.dto.SubjectSubmissionItem;
import com.ecom.academic.model.AcademicRank;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.AdminLogRepository;
import com.ecom.repository.UserRepository;

class DashboardAnalyticsServiceTest {

    private AcademicRequestRepository acadRepo;
    private PositionRequestRepository posRepo;
    private UserRepository userRepo;
    private StaffMemberRepository staffRepo;
    private AdminLogRepository logRepo;
    private AcademicRequestService acadService;
    private SignatureStepRepository signatureStepRepo;
    private DashboardAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        acadRepo = mock(AcademicRequestRepository.class);
        posRepo = mock(PositionRequestRepository.class);
        userRepo = mock(UserRepository.class);
        staffRepo = mock(StaffMemberRepository.class);
        logRepo = mock(AdminLogRepository.class);
        acadService = mock(AcademicRequestService.class);
        signatureStepRepo = mock(SignatureStepRepository.class);

        analyticsService = new DashboardAnalyticsService(
                acadRepo, posRepo, userRepo, staffRepo, logRepo, acadService, signatureStepRepo);
    }

    @Test
    void testEvaluationExpiryAnalyticsCategorization() {
        UserDtls applicant = new UserDtls();
        applicant.setFirstName("สมชาย");
        applicant.setLastName("ใจดี");

        AcademicRequest req1 = new AcademicRequest();
        req1.setId(1L);
        req1.setRequestCode("KKU-ACAD-2569-0001");
        req1.setCurrentStatus(RequestStatus.COMPLETED);
        req1.setApplicant(applicant);

        AcademicRequest req2 = new AcademicRequest();
        req2.setId(2L);
        req2.setRequestCode("KKU-ACAD-2569-0002");
        req2.setCurrentStatus(RequestStatus.COMPLETED_PASS);
        req2.setApplicant(applicant);

        AcademicRequest req3 = new AcademicRequest();
        req3.setId(3L);
        req3.setRequestCode("KKU-ACAD-2568-0003");
        req3.setCurrentStatus(RequestStatus.COLLEGE_ENDORSED);
        req3.setApplicant(applicant);

        when(acadRepo.findAllNonDraftWithApplicant()).thenReturn(List.of(req1, req2, req3));
        when(posRepo.findLinkedEvaluationIds()).thenReturn(List.of(1L)); // req1 is linked

        // req1: 15 days left (Urgent <= 30)
        EvaluationSummary summary1 = new EvaluationSummary(
                1L, "KKU-ACAD-2569-0001", "CP351001", "Data Structures",
                "2569", "1/2569", "ดีมาก", "10/01/2569", "10/01/2572",
                LocalDateTime.now().plusDays(15), 15L, "อาจารย์", AcademicRank.ASSISTANT_PROFESSOR);

        // req2: 60 days left (Warning <= 90)
        EvaluationSummary summary2 = new EvaluationSummary(
                2L, "KKU-ACAD-2569-0002", "CP352002", "Database Systems",
                "2569", "1/2569", "ดี", "15/02/2569", "15/02/2572",
                LocalDateTime.now().plusDays(60), 60L, "อาจารย์", AcademicRank.ASSISTANT_PROFESSOR);

        // req3: -10 days (Expired)
        EvaluationSummary summary3 = new EvaluationSummary(
                3L, "KKU-ACAD-2568-0003", "CP353003", "Software Engineering",
                "2568", "2/2568", "ดีเด่น", "01/01/2565", "01/01/2568",
                LocalDateTime.now().minusDays(10), -10L, "อาจารย์", AcademicRank.ASSOCIATE_PROFESSOR);

        when(acadService.summarize(req1)).thenReturn(summary1);
        when(acadService.summarize(req2)).thenReturn(summary2);
        when(acadService.summarize(req3)).thenReturn(summary3);

        Map<String, Object> result = analyticsService.getEvaluationExpiryAnalytics("all");
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Long> summary = (Map<String, Long>) result.get("summary");
        assertEquals(3L, summary.get("totalPassed"));
        assertEquals(1L, summary.get("expiringSoon30Count"));
        assertEquals(2L, summary.get("expiringSoon90Count"));
        assertEquals(1L, summary.get("expiredCount"));
        assertEquals(2L, summary.get("unusedCount")); // req2 and req3 are not linked

        @SuppressWarnings("unchecked")
        List<EvaluationExpiryItem> items = (List<EvaluationExpiryItem>) result.get("items");
        assertEquals(3, items.size());
    }

    @Test
    void testYearlySubjectAnalyticsFiltering() {
        UserDtls applicant = new UserDtls();
        applicant.setFirstName("วิชาญ");
        applicant.setLastName("สอนดี");

        AcademicRequest req1 = new AcademicRequest();
        req1.setRequestCode("KKU-ACAD-2569-0010");
        req1.setCurrentStatus(RequestStatus.RECEIVED);
        req1.setApplicant(applicant);

        AcademicRequest req2 = new AcademicRequest();
        req2.setRequestCode("KKU-ACAD-2568-0020");
        req2.setCurrentStatus(RequestStatus.COMPLETED);
        req2.setApplicant(applicant);

        when(acadRepo.findAllNonDraftWithApplicant()).thenReturn(List.of(req1, req2));

        EvaluationSummary summary1 = new EvaluationSummary(
                10L, "KKU-ACAD-2569-0010", "CP1001", "Intro to AI",
                "2569", "1/2569", null, null, null,
                null, null, "อาจารย์", null);

        EvaluationSummary summary2 = new EvaluationSummary(
                20L, "KKU-ACAD-2568-0020", "CP1002", "Computer Networks",
                "2568", "2/2568", "ดีมาก", "05/05/2568", "05/05/2571",
                null, null, "อาจารย์", null);

        when(acadService.summarize(req1)).thenReturn(summary1);
        when(acadService.summarize(req2)).thenReturn(summary2);

        // Filter by 2569
        Map<String, Object> result2569 = analyticsService.getYearlySubjectAnalytics("2569");
        @SuppressWarnings("unchecked")
        List<SubjectSubmissionItem> items2569 = (List<SubjectSubmissionItem>) result2569.get("items");
        assertEquals(1, items2569.size());
        assertEquals("CP1001", items2569.get(0).courseCode());
        assertEquals("2569", items2569.get(0).academicYear());

        // All years
        Map<String, Object> resultAll = analyticsService.getYearlySubjectAnalytics("ALL");
        @SuppressWarnings("unchecked")
        List<SubjectSubmissionItem> itemsAll = (List<SubjectSubmissionItem>) resultAll.get("items");
        assertEquals(2, itemsAll.size());
    }

    @Test
    void testPositionPipelineAnalytics() {
        when(posRepo.countByTargetPosition()).thenReturn(List.of(
                new Object[] { "ผู้ช่วยศาสตราจารย์", 5L },
                new Object[] { "รองศาสตราจารย์", 2L }));
        when(posRepo.countByMajor()).thenReturn(List.of(
                new Object[] { "วิทยาการคอมพิวเตอร์", 4L },
                new Object[] { "เทคโนโลยีสารสนเทศ", 3L }));

        Map<String, Object> pipeline = analyticsService.getPositionPipelineAnalytics();
        assertNotNull(pipeline);

        @SuppressWarnings("unchecked")
        Map<String, Long> byRank = (Map<String, Long>) pipeline.get("byRank");
        assertEquals(5L, byRank.get("ผู้ช่วยศาสตราจารย์"));
        assertEquals(2L, byRank.get("รองศาสตราจารย์"));

        @SuppressWarnings("unchecked")
        Map<String, Long> byMajor = (Map<String, Long>) pipeline.get("byMajor");
        assertEquals(4L, byMajor.get("วิทยาการคอมพิวเตอร์"));
    }

    @Test
    void testEvaluationQualityMetrics() {
        when(acadRepo.countByCurrentStatusIn(List.of(
                RequestStatus.COMPLETED_PASS,
                RequestStatus.COLLEGE_ENDORSED,
                RequestStatus.COMPLETED))).thenReturn(9L);
        when(acadRepo.countByCurrentStatus(RequestStatus.COMPLETED_FAIL)).thenReturn(1L);
        when(acadRepo.countByCurrentStatusIn(List.of(
                RequestStatus.COMPLETED_REVISE,
                RequestStatus.REVISION_SUBMITTED))).thenReturn(2L);
        when(acadRepo.findAllNonDraftWithApplicant()).thenReturn(List.of());

        Map<String, Object> metrics = analyticsService.getEvaluationQualityMetrics();
        assertNotNull(metrics);
        assertEquals(90, metrics.get("passRate")); // 9 / (9 + 1) = 90%
        assertEquals(9L, metrics.get("passedCount"));
        assertEquals(1L, metrics.get("failedCount"));
    }

    @Test
    void testStalledRequestAnalytics() {
        UserDtls user = new UserDtls();
        user.setFirstName("ทดสอบ");
        user.setLastName("ค้างนาน");

        AcademicRequest stalledAcad = new AcademicRequest();
        stalledAcad.setId(10L);
        stalledAcad.setApplicant(user);
        stalledAcad.setCurrentStatus(RequestStatus.SUB_COMMITTEE_APPOINTED);
        stalledAcad.setCreatedAt(LocalDateTime.now().minusDays(20));
        stalledAcad.setUpdatedAt(LocalDateTime.now().minusDays(20));

        AcademicRequest normalAcad = new AcademicRequest();
        normalAcad.setId(11L);
        normalAcad.setApplicant(user);
        normalAcad.setCurrentStatus(RequestStatus.RECEIVED);
        normalAcad.setCreatedAt(LocalDateTime.now().minusDays(3));
        normalAcad.setUpdatedAt(LocalDateTime.now().minusDays(3));

        when(acadRepo.findAllNonDraftWithApplicant()).thenReturn(List.of(stalledAcad, normalAcad));
        when(posRepo.findAllNonDraftWithApplicant()).thenReturn(List.of());

        Map<String, Object> stalled = analyticsService.getStalledRequestAnalytics();
        assertNotNull(stalled);
        assertEquals(1L, stalled.get("totalStalled14"));
        assertEquals(0L, stalled.get("totalCritical30"));

        @SuppressWarnings("unchecked")
        List<com.ecom.academic.dto.StalledRequestItem> items = (List<com.ecom.academic.dto.StalledRequestItem>) stalled.get("items");
        assertEquals(1, items.size());
        assertEquals("ประเมินการสอน", items.get(0).requestType());
        assertTrue(items.get(0).stalledDays() >= 20);
        // The row links straight to the request — the list page's search box
        // matches applicant names only, so a code-based search found nothing.
        assertEquals("/admin/academic/request/10", items.get(0).detailUrl());
    }

    @Test
    void stalledPositionRequestLinksToPositionAdminDetail() {
        UserDtls user = new UserDtls();
        user.setFirstName("ทดสอบ");
        PositionRequest pr = new PositionRequest();
        pr.setId(7L);
        pr.setApplicant(user);
        pr.setRequestCode("KKU-POS-2569-0007");
        pr.setCurrentStatus(PositionRequestStatus.SCREENING_COMMITTEE);
        pr.setCreatedAt(LocalDateTime.now().minusDays(40));
        pr.setUpdatedAt(LocalDateTime.now().minusDays(40));

        when(acadRepo.findAllNonDraftWithApplicant()).thenReturn(List.of());
        when(posRepo.findAllNonDraftWithApplicant()).thenReturn(List.of(pr));

        @SuppressWarnings("unchecked")
        List<com.ecom.academic.dto.StalledRequestItem> items = (List<com.ecom.academic.dto.StalledRequestItem>)
                analyticsService.getStalledRequestAnalytics().get("items");
        assertEquals(1, items.size());
        assertEquals("/admin/position/request/7", items.get(0).detailUrl());
    }

    @Test
    void testPendingSignatureAnalytics() {
        com.ecom.academic.model.SignatureRequest sigReq = new com.ecom.academic.model.SignatureRequest();
        sigReq.setId(100L);
        sigReq.setRequestId(1L);
        sigReq.setDocumentType(1);
        sigReq.setDocumentLabel("บันทึกข้อความแบบที่ 1");
        sigReq.setCreatedAt(LocalDateTime.now().minusDays(4));
        sigReq.setDueAt(LocalDateTime.now().plusDays(2));

        UserDtls signer = new UserDtls();
        signer.setFirstName("คณบดี");
        signer.setLastName("ประจำวิทยาลัย");

        com.ecom.academic.model.SignatureStep step = new com.ecom.academic.model.SignatureStep();
        step.setId(50L);
        step.setSignatureRequest(sigReq);
        step.setRoleLabel("คณบดี");
        step.setSigner(signer);
        step.setStatus(com.ecom.academic.model.SignatureStepStatus.ACTIVE);

        when(signatureStepRepo.findAllActivePendingSteps()).thenReturn(List.of(step));

        Map<String, Object> sigAnalytics = analyticsService.getPendingSignatureAnalytics();
        assertNotNull(sigAnalytics);
        assertEquals(1L, sigAnalytics.get("totalPending"));
        assertEquals(0L, sigAnalytics.get("overdueCount"));

        @SuppressWarnings("unchecked")
        Map<String, Long> byRole = (Map<String, Long>) sigAnalytics.get("byRole");
        assertEquals(1L, byRole.get("คณบดี"));
    }

    @Test
    void pendingSignatureShowsRealRequestCodeAndLinksToRequest() {
        com.ecom.academic.model.SignatureRequest sigReq = new com.ecom.academic.model.SignatureRequest();
        sigReq.setId(100L);
        sigReq.setModule(com.ecom.academic.model.SignatureModule.ACADEMIC);
        sigReq.setRequestId(12L);
        sigReq.setDocumentType(1);
        sigReq.setCreatedAt(LocalDateTime.now().minusDays(1));

        com.ecom.academic.model.SignatureStep step = new com.ecom.academic.model.SignatureStep();
        step.setId(51L);
        step.setSignatureRequest(sigReq);
        step.setRoleLabel("คณบดี");

        AcademicRequest acad = new AcademicRequest();
        acad.setId(12L);
        acad.setRequestCode("KKU-ACAD-2569-0012");

        when(signatureStepRepo.findAllActivePendingSteps()).thenReturn(List.of(step));
        when(acadRepo.findAllById(any())).thenReturn(List.of(acad));

        @SuppressWarnings("unchecked")
        List<com.ecom.academic.dto.PendingSignatureItem> items = (List<com.ecom.academic.dto.PendingSignatureItem>)
                analyticsService.getPendingSignatureAnalytics().get("items");
        assertEquals(1, items.size());
        // Used to print "KKU-ACAD-12", a code no request actually has.
        assertEquals("KKU-ACAD-2569-0012", items.get(0).requestCode());
        assertEquals("/admin/academic/request/12", items.get(0).detailUrl());
    }

    @Test
    void testFacultyRankAnalytics() {
        com.ecom.academic.model.StaffMember s1 = new com.ecom.academic.model.StaffMember();
        s1.setAcademicTitle("ศาสตราจารย์ ดร.");
        s1.setIsActive(true);

        com.ecom.academic.model.StaffMember s2 = new com.ecom.academic.model.StaffMember();
        s2.setAcademicTitle("รศ.ดร.");
        s2.setIsActive(true);

        com.ecom.academic.model.StaffMember s3 = new com.ecom.academic.model.StaffMember();
        s3.setAcademicTitle("ผศ.");
        s3.setIsActive(true);

        com.ecom.academic.model.StaffMember s4 = new com.ecom.academic.model.StaffMember();
        s4.setAcademicTitle("อาจารย์");
        s4.setIsActive(true);

        when(staffRepo.findByIsActiveTrueOrderByFirstNameAscLastNameAsc()).thenReturn(List.of(s1, s2, s3, s4));

        Map<String, Object> faculty = analyticsService.getFacultyRankAnalytics();
        assertNotNull(faculty);

        com.ecom.academic.dto.FacultyRankStat stat = (com.ecom.academic.dto.FacultyRankStat) faculty.get("stat");
        assertEquals(4L, stat.totalFaculty());
        assertEquals(1L, stat.professorCount());
        assertEquals(1L, stat.associateProfessorCount());
        assertEquals(1L, stat.assistantProfessorCount());
        assertEquals(1L, stat.lecturerCount());
        assertEquals(75.0, stat.promotedPercentage()); // 3 / 4 = 75.0%
    }
}
