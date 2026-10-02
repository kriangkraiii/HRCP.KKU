package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

import jakarta.persistence.EntityManagerFactory;

/**
 * หน้ารายการและแดชบอร์ดเคยอ่านเอกสารทีละคำร้อง — แดชบอร์ดของแอดมินยิงราว 3.4 query
 * ต่อคำร้องหนึ่งรายการ (4,066 query ที่ 1,200 คำร้อง) เทสนี้ยืนยันสองอย่าง: ทางลัดแบบอ่านรวด
 * ให้ผลเหมือนทางเดิมทุกกรณี และจำนวน query ไม่โตตามจำนวนคำร้องอีก
 */
@DisplayName("อ่านเอกสารของหลายคำร้องในคราวเดียว")
class BatchDocumentLoadingTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private DashboardAnalyticsService dashboard;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UserDtls applicant;

    @BeforeEach
    void setUp() {
        data.reset();
        applicant = data.applicant();
    }

    @Test
    @DisplayName("summarizeAll ให้ผลเท่ากับ summarize ทีละคำร้อง รวมคำร้องที่ไม่มีเอกสารและสำเนาแรกที่ว่าง")
    void summarizeAllMatchesOneByOne() {
        AcademicRequest full = data.evaluation(applicant, RequestStatus.COMPLETED);
        data.academicDocument(full, 1, "{\"course_code\":\"CP101\",\"course_name\":\"Intro\","
                + "\"academic_year\":\"2568\",\"semester\":\"1/2568\"}");
        data.academicDocument(full, 9, "{\"evaluation_date\":\"10 สิงหาคม 2569\",\"result_level\":\"ดีมาก\"}");

        AcademicRequest blankFirstCopy = data.evaluation(applicant, RequestStatus.COMPLETED_PASS);
        data.academicDocument(blankFirstCopy, 9, "", 0);
        data.academicDocument(blankFirstCopy, 9, "{\"expiration_date\":\"1 มกราคม 2571\"}", 1);

        AcademicRequest noDocuments = data.evaluation(applicant, RequestStatus.RECEIVED);

        List<AcademicRequest> requests = List.of(full, blankFirstCopy, noDocuments);
        Map<Long, EvaluationSummary> batch = academicService.summarizeAll(requests);

        assertThat(batch).hasSize(3);
        for (AcademicRequest r : requests) {
            assertThat(batch.get(r.getId())).as("คำร้อง %s", r.getRequestCode())
                    .isEqualTo(academicService.summarize(r));
        }
        assertThat(batch.get(full.getId()).courseCode()).isEqualTo("CP101");
        assertThat(batch.get(blankFirstCopy.getId()).expiryDate()).isEqualTo("1 มกราคม 2571");
    }

    @Test
    @DisplayName("documentDataFor ข้ามคำร้องที่ไม่มีเอกสาร ทั้งฝั่งประเมินและฝั่งขอตำแหน่ง")
    void documentDataForOmitsRequestsWithoutTheDocument() {
        AcademicRequest withDoc = data.evaluation(applicant, RequestStatus.RECEIVED);
        data.academicDocument(withDoc, 1, "{\"course_code\":\"CP202\"}");
        AcademicRequest withoutDoc = data.evaluation(applicant, RequestStatus.RECEIVED);

        assertThat(academicService.documentDataFor(List.of(withDoc.getId(), withoutDoc.getId()), 1))
                .containsOnlyKeys(withDoc.getId())
                .extractingByKey(withDoc.getId())
                .isEqualTo(Map.of("course_code", "CP202"));

        PositionRequest pos = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(pos, 2, "{\"target_position\":\"ผศ.\"}");
        PositionRequest posWithout = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);

        assertThat(positionService.documentDataFor(List.of(pos.getId(), posWithout.getId()), 2))
                .containsOnlyKeys(pos.getId())
                .extractingByKey(pos.getId())
                .isEqualTo(Map.of("target_position", "ผศ."));
    }

    @Test
    @DisplayName("แดชบอร์ดใช้ query เท่าเดิมไม่ว่าจะมีคำร้อง 3 หรือ 30 รายการ")
    void dashboardQueryCountDoesNotGrowWithRequests() {
        createEvaluations(3);
        long few = statementsFor(this::renderDashboardData);

        createEvaluations(27);
        long many = statementsFor(this::renderDashboardData);

        assertThat(many).as("query ที่ 30 คำร้อง เทียบกับที่ 3 คำร้อง (%d)", few).isEqualTo(few);
    }

    private void renderDashboardData() {
        dashboard.getKpiSummary();
        dashboard.getStatusDistribution();
        DashboardAnalyticsService.Evaluations evaluations = dashboard.loadEvaluations();
        dashboard.getEvaluationExpiryAnalytics(null, evaluations);
        dashboard.getYearlySubjectAnalytics(null, evaluations);
        dashboard.getEvaluationQualityMetrics(evaluations);
        dashboard.getStalledRequestAnalytics(evaluations);
    }

    private void createEvaluations(int count) {
        RequestStatus[] statuses = { RequestStatus.COMPLETED, RequestStatus.COMPLETED_PASS, RequestStatus.RECEIVED };
        for (int i = 0; i < count; i++) {
            AcademicRequest r = data.evaluation(applicant, statuses[i % statuses.length]);
            data.academicDocument(r, 1, "{\"course_code\":\"CP" + i + "\",\"academic_year\":\"2568\"}");
            data.academicDocument(r, 9, "{\"evaluation_date\":\"10 สิงหาคม 2569\"}");
        }
    }

    private long statementsFor(Runnable work) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            work.run();
            return stats.getPrepareStatementCount();
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }
}
