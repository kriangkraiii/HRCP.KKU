package com.ecom.render;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * เปิดทุกหน้าในระบบ แล้วตรวจว่ามัน render ออกมาได้จริง
 *
 * <p><b>ทำไมต้องมีเทสนี้:</b> ชุดทดสอบเดิม 1,100 กว่าเทสอยู่ที่ชั้น service กับ
 * ชั้นกติกาของ flow เกือบทั้งหมด ส่วนชั้นที่ผู้ใช้เห็นจริง — เทมเพลต 86 ไฟล์
 * เกือบสามหมื่นบรรทัด — แทบไม่ถูกแตะเลย ตอนสำรวจพบว่า <b>72 จาก 86 เทมเพลต
 * ไม่เคยถูก render โดยเทสใดเลย</b> และในบรรดาแบบฟอร์มเอกสาร 10 ชนิด × 2 บทบาท
 * มีแค่ {@code document/1} ชนิดเดียวที่เคยถูกเรียก
 *
 * <p>ผลคือหน้าที่พังตอน render — model attribute ที่หายไป, expression ที่พิมพ์ผิด,
 * fragment ที่อ้างชื่อผิด — ไม่มีอะไรจับได้เลยจนกว่าจะมีคนเปิดหน้านั้นในเบราว์เซอร์
 * ซึ่งคือเหตุผลที่การเทสมือเจอบั๊กเยอะ
 *
 * <p><b>ขอบเขต:</b> เทสนี้ครอบเฉพาะ "หน้าถูกส่งออกมาครบและมีโครงสร้างที่ถูกต้อง"
 * เท่านั้น พฤติกรรมที่ต้องรัน JavaScript จริงหรือต้องมองด้วยตาเป็นคนละชั้น
 * (ดู {@code com.ecom.e2e}) และรายการเอกสารที่ตรวจดึงมาจากค่าคงที่ของ production
 * โดยตรง เพิ่มเอกสารชนิดใหม่ในระบบแล้วเทสนี้จะตามไปเองโดยไม่ต้องแก้
 */
@DisplayName("ทุกหน้าในระบบต้อง render ได้จริง")
class EveryPageRendersTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionRequestService;

    /** หนึ่งหน้าที่ต้องเปิดได้ พร้อมบทบาทที่ใช้เปิด */
    private record Page(String url, String email, String role) {
        DynamicTest asTest(EveryPageRendersTest owner) {
            return DynamicTest.dynamicTest(role.toLowerCase() + " เปิด " + url,
                    () -> owner.mustRender(this));
        }
    }

    private static Page asApplicant(String url) {
        return new Page(url, TestDataFactory.APPLICANT_EMAIL, "USER");
    }

    private static Page asStaff(String url) {
        return new Page(url, TestDataFactory.ADMIN_EMAIL, "ADMIN");
    }

    private static Page asOtherApplicant(String url) {
        return new Page(url, TestDataFactory.OTHER_APPLICANT_EMAIL, "USER");
    }

    @TestFactory
    @DisplayName("ทุกหน้าตอบ 200 พร้อม HTML ที่ประกอบครบ")
    Stream<DynamicTest> everyPageRenders() {
        UserDtls applicant = data.applicant();
        data.otherApplicant();
        data.admin();

        // คำร้องสองสถานะต่อเฟส เพราะหน้าต่าง ๆ เปิดได้คนละช่วงของ flow:
        // แบบร่างเป็นของผู้ยื่นล้วน — หน้ารายละเอียดของผู้ยื่นจะเด้งกลับไปหน้ากรอก
        // และฝั่งเจ้าหน้าที่ยังเปิดแบบฟอร์มไม่ได้จนกว่าคำร้องจะถูกส่งเข้ามาจริง
        AcademicRequest draftEvaluation = data.evaluation(applicant, RequestStatus.DRAFT);
        AcademicRequest liveEvaluation = data.evaluation(applicant, RequestStatus.RECEIVED);
        PositionRequest draftPosition =
                data.positionRequest(applicant, PositionRequestStatus.DRAFT, liveEvaluation);
        PositionRequest livePosition = data.positionRequest(
                applicant, PositionRequestStatus.DOCUMENT_RECEIVED, liveEvaluation);

        List<Page> pages = new ArrayList<>();

        // --- เฟส 1: การประเมินการสอน ---
        pages.add(asApplicant("/user/academic/dashboard"));
        pages.add(asApplicant("/user/academic/documents"));
        pages.add(asApplicant("/user/academic/history"));
        // ผู้ที่ยังไม่มีคำร้องค้างอยู่ ไม่อย่างนั้นระบบจะพากลับไปที่คำร้องเดิม
        pages.add(asOtherApplicant("/user/academic/new-request"));
        pages.add(asApplicant("/user/academic/request/" + liveEvaluation.getId()));
        pages.add(asApplicant("/user/academic/request/" + draftEvaluation.getId() + "/document-0"));
        pages.add(asApplicant("/user/academic/request/" + draftEvaluation.getId() + "/document-1"));

        pages.add(asStaff("/admin/academic/requests"));
        pages.add(asStaff("/admin/academic/request/" + liveEvaluation.getId()));
        for (int type : AcademicRequestService.getDocLabels().keySet()) {
            pages.add(asStaff("/admin/academic/request/" + liveEvaluation.getId()
                    + "/document/" + type));
        }

        // --- เฟส 2: การขอกำหนดตำแหน่ง ---
        pages.add(asApplicant("/user/position/dashboard"));
        // ผู้ที่ยังไม่มีแบบร่างค้างอยู่ ไม่อย่างนั้นระบบจะพากลับไปที่แบบร่างเดิม
        pages.add(asOtherApplicant("/user/position/new-request"));
        pages.add(asApplicant("/user/position/request/" + draftPosition.getId()));
        for (int type : positionRequestService.getApplicantDocLabels().keySet()) {
            pages.add(asApplicant("/user/position/request/" + draftPosition.getId()
                    + "/document/" + type));
        }

        pages.add(asStaff("/admin/position/requests"));
        pages.add(asStaff("/admin/position/request/" + livePosition.getId()));
        for (int type : positionRequestService.getAdminDocLabels().keySet()) {
            pages.add(asStaff("/admin/position/request/" + livePosition.getId()
                    + "/document/" + type));
        }

        // --- ลายเซ็นอิเล็กทรอนิกส์ ---
        pages.add(asApplicant("/esign/inbox"));
        pages.add(asApplicant("/esign/my-signatures"));
        pages.add(asStaff("/esign/inbox"));

        // --- ตั้งค่า คู่มือ โปรไฟล์ แจ้งเตือน ---
        pages.add(asApplicant("/user/academic/settings"));
        pages.add(asApplicant("/user/academic/guide"));
        pages.add(asApplicant("/user/profile"));
        pages.add(asApplicant("/user/notifications"));
        pages.add(asStaff("/admin/academic/settings"));
        pages.add(asStaff("/admin/academic/guide"));
        pages.add(asStaff("/admin/profile"));
        pages.add(asStaff("/admin/notifications"));

        // --- หน้าฝั่งผู้ดูแลระบบ ---
        pages.add(asStaff("/admin/"));
        pages.add(asStaff("/admin/users"));
        pages.add(asStaff("/admin/activity-logs"));
        pages.add(asStaff("/admin/add-admin"));
        pages.add(asStaff("/admin/academic/staff"));
        pages.add(asStaff("/admin/academic/staff/add"));

        return pages.stream().map(p -> p.asTest(this));
    }

    private void mustRender(Page page) throws Exception {
        MockHttpServletResponse response = mvc
                .perform(get(page.url()).with(user(page.email()).roles(page.role())))
                .andReturn().getResponse();

        List<String> complaints = RenderedPage.complaintsAbout(page.url(), response);
        if (!complaints.isEmpty()) {
            throw new AssertionError(page.url() + " (" + page.role() + ")\n  - "
                    + String.join("\n  - ", complaints));
        }
    }
}
