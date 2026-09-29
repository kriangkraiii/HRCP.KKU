package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * เปลี่ยนลายเซ็นบนหน้าลงนาม — ม่านโหลดกับเอกสารต้องเปลี่ยนพร้อมกัน
 *
 * <p>เดิมม่านโหลดอยู่ในพื้นที่เลื่อนของหน้าเอกสาร และการวาดหน้าใหม่ล้างพื้นที่นั้นทิ้งตั้งแต่เริ่ม
 * ม่านจึงหายไปก่อน แล้วเอกสารค่อย ๆ โผล่ทีละหน้า — หน้าจอเปลี่ยนไม่พร้อมกัน
 */
@DisplayName("เปลี่ยนลายเซ็นแล้วตัวอย่างเอกสารสลับพร้อมม่านโหลด")
class SignaturePreviewSwapBrowserTest extends PlaywrightTestBase {

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private SignatureStepRepository steps;

    @Test
    @DisplayName("เลือกลายเซ็นอื่น — ม่านขึ้นทับเอกสารเดิม แล้วเอกสารใหม่กับม่านที่หายไปเกิดพร้อมกัน")
    void switchingSignatureSwapsThePreviewTogetherWithTheLoadingScreen() {
        UserDtls professor = data.applicant();
        UserDtls dean = data.otherApplicant();
        UserDtls officer = data.admin();
        data.digitalCertificateFor(professor); // ตัวเลือกลายเซ็นแสดงเฉพาะเมื่อมีใบรับรอง
        // signatureFor ตั้งฉบับล่าสุดเป็นค่าเริ่มต้น — ฉบับแรกคือตัวที่ยังไม่ได้เลือก
        UserSignature other = data.signatureFor(professor);
        data.signatureFor(professor);

        AcademicRequest evaluation = data.evaluationForCourse(professor, "CP001101", "2568");
        PositionRequest request = data.positionRequest(professor, PositionRequestStatus.DRAFT,
                evaluation, "ผู้ช่วยศาสตราจารย์");
        String doc3 = "{\"applicant_name\":\"สมชาย ใจดี\",\"position\":\"อาจารย์\"}";
        data.positionDocument(request, 3, doc3);
        var none = SignatureWorkflowService.ActorContext.none();
        var created = workflow.createEnvelope(SignatureModule.POSITION, request.getId(), 3,
                "แบบรับรองจริยธรรม", doc3,
                List.of(new SignatureWorkflowService.SignerAssignment("applicant", professor.getId()),
                        new SignatureWorkflowService.SignerAssignment("dean", dean.getId())),
                null, officer, none);
        assertThat(created.ok()).as(String.valueOf(created.error())).isTrue();
        workflow.startCirculation(created.request().getId(), officer, none);
        SignatureStep applicantStep = steps
                .findBySignatureRequestIdOrderByStepOrderAsc(created.request().getId()).stream()
                .filter(st -> "applicant".equals(st.getSlotKey()))
                .findFirst().orElseThrow();

        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);
        page.navigate(baseUrl() + "/esign/sign/" + applicantStep.getId());

        Locator overlay = page.locator("#esign-pdf-container .esign-viewer-loading");
        overlay.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

        // บันทึกลำดับเหตุการณ์ในหน้าเอง — การหน่วงผ่าน route handler ของ Playwright Java
        // บล็อกเธรดเดียวกับที่เทสใช้เฝ้าหน้าจอ จึงจับจังหวะม่านขึ้น-ลงจากฝั่งเทสไม่ได้แน่นอน
        page.evaluate("""
                () => {
                  const ov = document.querySelector('#esign-pdf-container .esign-viewer-loading');
                  const pv = document.querySelector('#esign-pdf-container .esign-viewer-pages');
                  const first = pv.firstElementChild;
                  const up = () => ov.style.display !== 'none' && ov.style.opacity !== '0';
                  window.__swapLog = { hadFirst: !!first, events: [] };
                  new MutationObserver(() => window.__swapLog.events.push(
                      { ev: up() ? 'overlay-up' : 'overlay-down', oldShown: first.isConnected }))
                    .observe(ov, { attributes: true, attributeFilter: ['style'] });
                  new MutationObserver(() => window.__swapLog.events.push(
                      { ev: 'swap', overlayUp: up(), oldShown: first.isConnected }))
                    .observe(pv, { childList: true });
                }
                """);

        page.locator("label[for='sig-" + other.getId() + "']").click();

        page.waitForFunction("() => window.__swapLog.events.some(e => e.ev === 'swap')"
                + " && window.__swapLog.events.at(-1).ev === 'overlay-down'",
                null, new Page.WaitForFunctionOptions().setTimeout(30000));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> log = (java.util.Map<String, Object>) page.evaluate("() => window.__swapLog");
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> events = (List<java.util.Map<String, Object>>) log.get("events");
        String trace = String.valueOf(events);

        assertThat(log.get("hadFirst")).as("ตัวอย่างเอกสารแรกต้องแสดงก่อน").isEqualTo(true);
        // ม่านขึ้นก่อน ขณะที่เอกสารเดิมยังอยู่ข้างหลัง (ไม่ถูกล้างทิ้งก่อน)
        assertThat(events.get(0)).as(trace).containsEntry("ev", "overlay-up").containsEntry("oldShown", true);
        // สลับเอกสารทีเดียว ตอนม่านยังปิดอยู่ — ไม่มีหน้าว่างหรือหน้าโผล่ทีละหน้าให้เห็น
        List<java.util.Map<String, Object>> swaps = events.stream().filter(e -> "swap".equals(e.get("ev"))).toList();
        assertThat(swaps).as(trace).hasSize(1);
        assertThat(swaps.get(0)).as(trace).containsEntry("overlayUp", true).containsEntry("oldShown", false);
        // ม่านลงหลังสลับเท่านั้น
        int swapAt = events.indexOf(swaps.get(0));
        int downAt = -1;
        for (int i = 0; i < events.size(); i++) {
            if ("overlay-down".equals(events.get(i).get("ev"))) { downAt = i; break; }
        }
        assertThat(downAt).as(trace).isGreaterThan(swapAt);
    }
}
