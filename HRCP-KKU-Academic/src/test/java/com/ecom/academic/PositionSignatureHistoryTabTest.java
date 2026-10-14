package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureAuditEvent;
import com.ecom.academic.model.SignatureAuditEventType;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.repository.SignatureAuditEventRepository;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * แท็บ "ประวัติการลงนาม" ในหน้าคำร้องเฟส 2 ของแอดมิน
 *
 * <p>การลงนาม การถอนลายเซ็น และการปฏิเสธ บันทึกอยู่ใน audit ของระบบลงนามเท่านั้น
 * เดิมแสดงแค่หน้า esign/verify แอดมินที่ดูคำร้องจึงไม่เห็นว่าใครลงนามหรือถอนเมื่อไร
 */
@DisplayName("แท็บประวัติการลงนามของคำร้องขอตำแหน่ง")
class PositionSignatureHistoryTabTest extends AbstractFlowTest {

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignatureAuditEventRepository audits;

    private SignatureRequest envelope(PositionRequest request, int documentType, String code) {
        SignatureRequest r = new SignatureRequest();
        r.setModule(SignatureModule.POSITION);
        r.setRequestId(request.getId());
        r.setDocumentType(documentType);
        r.setFrozenJson("{}");
        r.setFrozenHash("hash-" + code);
        r.setVerificationCode(code);
        return envelopes.save(r);
    }

    private void event(SignatureRequest envelope, SignatureAuditEventType type, UserDtls actor) {
        SignatureAuditEvent e = new SignatureAuditEvent();
        e.setSignatureRequest(envelope);
        e.setEventType(type);
        e.setActor(actor);
        audits.save(e);
    }

    @Test
    @DisplayName("แสดงเหตุการณ์ลงนามของทุกเอกสารในคำร้อง พร้อมผู้กระทำ — ฉบับแยกตามงานวิจัยกรองด้วยเอกสารหลัก")
    void showsEverySigningEventOfTheRequest() throws Exception {
        UserDtls applicant = data.applicant();
        UserDtls admin = data.admin();
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED,
                data.evaluation(applicant, RequestStatus.COMPLETED));
        event(envelope(request, 2, "SIGHIST2"), SignatureAuditEventType.SIGNED, applicant);
        event(envelope(request, 902, "SIGHIST902"), SignatureAuditEventType.WITHDRAWN, applicant);

        PositionRequest other = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED,
                data.evaluation(applicant, RequestStatus.COMPLETED));
        event(envelope(other, 2, "SIGHISTOTHER"), SignatureAuditEventType.DECLINED, applicant);

        String html = mvc.perform(get("/admin/position/request/" + request.getId())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-target=\"sigHistPane\"");
        assertThat(html).containsPattern("class=\"history-entry sig-log-entry\"\\s+data-doc-type=\"2\"");
        assertThat(html).as("ฉบับที่ 902 กรองด้วยเอกสารหลัก 9")
                .containsPattern("class=\"history-entry sig-log-entry\"\\s+data-doc-type=\"9\"");
        assertThat(html).contains("hist-sig-SIGNED").contains("hist-sig-WITHDRAWN");
        assertThat(html).as("เหตุการณ์ของคำร้องอื่นไม่ปน").doesNotContain("hist-sig-DECLINED");
    }
}
