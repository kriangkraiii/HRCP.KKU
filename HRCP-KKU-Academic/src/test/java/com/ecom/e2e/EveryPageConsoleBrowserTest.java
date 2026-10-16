package com.ecom.e2e;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;

/**
 * เปิดทุกหน้าในเบราว์เซอร์จริงแล้วเก็บสิ่งที่เบราว์เซอร์บ่น — JavaScript ตาย ไฟล์ที่โหลดไม่ได้ และ console error
 *
 * <p>{@code EveryPageRendersTest} ตรวจว่าเซิร์ฟเวอร์ประกอบ HTML ได้ แต่ไม่รันสคริปต์ หน้าที่ HTML ครบแต่สคริปต์พัง
 * (ปุ่มกดแล้วเงียบ) จึงผ่านชั้นนั้นไปได้ ชั้นนี้เปิดหน้าเดียวกันด้วยเบราว์เซอร์ แล้วรายงานทุกหน้าที่มีปัญหาในครั้งเดียว
 */
@DisplayName("ทุกหน้าเปิดในเบราว์เซอร์ได้โดยไม่มีข้อผิดพลาดใน console")
class EveryPageConsoleBrowserTest extends PlaywrightTestBase {

    @Autowired
    private PositionRequestService positionRequestService;

    private final List<String> problems = new ArrayList<>();
    private String current = "";

    private void watch() {
        page.onConsoleMessage(m -> {
            String t = m.text();
            if ("error".equals(m.type()) && !t.contains("gstatic.com") && !t.contains("translate.google")
                    && !t.contains("Applying inline style violates") && !t.contains("ERR_INTERNET_DISCONNECTED")
                    && !t.contains("ERR_NAME_NOT_RESOLVED") && !t.contains("www.google.com/sorry")) {
                problems.add(current + " — console: " + t);
            }
        });
        page.onPageError(e -> problems.add(current + " — JavaScript ตาย: " + e));
        page.onResponse(r -> {
            if (r.status() >= 400 && r.url().startsWith(baseUrl())) {
                problems.add(current + " — HTTP " + r.status() + " " + r.url());
            }
        });
    }

    private void visit(String url) {
        current = url;
        page.navigate(baseUrl() + url);
        page.waitForLoadState();
        page.waitForTimeout(1200);
    }

    @Test
    @DisplayName("ผู้ยื่นและเจ้าหน้าที่ เปิดทุกหน้าหลัก")
    void everyPageIsQuietInTheConsole() {
        toleratingBrowserErrors();
        watch();
        UserDtls applicant = data.applicant();
        data.otherApplicant();
        data.admin();

        AcademicRequest draftEvaluation = data.evaluation(applicant, RequestStatus.DRAFT);
        data.academicDocument(draftEvaluation, 1, "{\"current_position\":\"อาจารย์\"}");
        AcademicRequest liveEvaluation = data.evaluation(applicant, RequestStatus.RECEIVED);
        AcademicRequest evaluationForDraft = data.evaluation(applicant, RequestStatus.COMPLETED);
        PositionRequest draftPosition = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                evaluationForDraft, "ผู้ช่วยศาสตราจารย์");
        data.positionDocument(draftPosition, 1, "{\"applicant_name\":\"" + applicant.getName()
                + "\",\"asst_research_working_1\":\"งานวิจัยเรื่องแรก\"}");
        PositionRequest livePosition = data.positionRequest(
                applicant, PositionRequestStatus.DOCUMENT_RECEIVED, liveEvaluation, "ผู้ช่วยศาสตราจารย์");
        data.positionDocument(livePosition, 1, "{\"asst_research_working_1\":\"งานวิจัยเรื่องแรก\"}");

        // ---------- ผู้ยื่น ----------
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        for (String url : List.of("/user/academic/dashboard", "/user/academic/documents", "/user/academic/history",
                "/user/academic/request/" + liveEvaluation.getId(),
                "/user/academic/request/" + draftEvaluation.getId() + "/document-1",
                "/user/academic/request/" + draftEvaluation.getId() + "/document-2",
                "/user/position/dashboard", "/user/position/request/" + draftPosition.getId(),
                "/esign/inbox", "/esign/my-signatures", "/user/academic/settings", "/user/academic/guide",
                "/user/profile", "/user/notifications", "/user/feedback", "/search?q=%E0%B8%9B%E0%B8%A3%E0%B8%B0")) {
            visit(url);
        }
        for (int type : positionRequestService.getApplicantDocLabels().keySet()) {
            visit("/user/position/request/" + draftPosition.getId() + "/document/" + (type == 9 ? 901 : type));
        }
        signOut();

        // ---------- เจ้าหน้าที่ ----------
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        for (String url : List.of("/admin/academic/dashboard", "/admin/academic/requests",
                "/admin/academic/request/" + liveEvaluation.getId(), "/admin/position/requests",
                "/admin/position/request/" + livePosition.getId(), "/esign/inbox", "/admin/academic/settings",
                "/admin/academic/settings/signers", "/admin/academic/committee", "/admin/academic/guide",
                "/admin/profile", "/admin/notifications", "/admin/users", "/admin/activity-logs", "/admin/add-admin",
                "/admin/academic/staff", "/admin/academic/staff/add", "/admin/file-manager", "/admin/publications",
                "/admin/external-sync", "/admin/faculty-changes", "/admin/academic/settings/login-announcement",
                "/search?q=%E0%B8%AA%E0%B8%A1%E0%B8%8A%E0%B8%B2%E0%B8%A2")) {
            visit(url);
        }
        for (int type : AcademicRequestService.getDocLabels().keySet()) {
            visit("/admin/academic/request/" + liveEvaluation.getId() + "/document/" + type);
        }
        for (int type : positionRequestService.getAdminDocLabels().keySet()) {
            visit("/admin/position/request/" + livePosition.getId() + "/document/" + (type == 9 ? 901 : type));
        }

        if (!problems.isEmpty()) {
            throw new AssertionError("หน้าที่เบราว์เซอร์บ่น:\n  - " + String.join("\n  - ",
                    problems.stream().distinct().toList()));
        }
    }
}
