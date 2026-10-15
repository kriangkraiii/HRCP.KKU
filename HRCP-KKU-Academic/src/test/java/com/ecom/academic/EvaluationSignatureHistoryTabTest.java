package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
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
 * แท็บ "ประวัติการลงนาม" ในหน้าคำร้องเฟส 1 ของแอดมิน — แบบเดียวกับเฟส 2 ({@link PositionSignatureHistoryTabTest})
 */
@DisplayName("แท็บประวัติการลงนามของคำร้องประเมินการสอน")
class EvaluationSignatureHistoryTabTest extends AbstractFlowTest {

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignatureAuditEventRepository audits;

    private SignatureRequest envelope(Long requestId, SignatureModule module, int documentType, String code) {
        SignatureRequest r = new SignatureRequest();
        r.setModule(module);
        r.setRequestId(requestId);
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
    @DisplayName("แสดงเหตุการณ์ลงนามของทุกเอกสารในคำร้อง — ไม่ปนคำร้องอื่นหรือคำร้องเฟส 2 ที่เลขตรงกัน")
    void showsEverySigningEventOfTheRequest() throws Exception {
        UserDtls applicant = data.applicant();
        UserDtls admin = data.admin();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        event(envelope(request.getId(), SignatureModule.ACADEMIC, 1, "EVSIG1"), SignatureAuditEventType.SIGNED,
                applicant);
        event(envelope(request.getId(), SignatureModule.ACADEMIC, 3, "EVSIG3"), SignatureAuditEventType.WITHDRAWN,
                admin);

        AcademicRequest other = data.evaluation(applicant, RequestStatus.RECEIVED);
        event(envelope(other.getId(), SignatureModule.ACADEMIC, 1, "EVSIGOTHER"), SignatureAuditEventType.DECLINED,
                applicant);
        // เลขคำร้องเดียวกันแต่เป็นของเฟส 2 — ต้องไม่ขึ้นในหน้าเฟส 1
        event(envelope(request.getId(), SignatureModule.POSITION, 2, "EVSIGPOS"), SignatureAuditEventType.EXPIRED,
                applicant);

        String html = mvc.perform(get("/admin/academic/request/" + request.getId())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-target=\"sigHistPane\"").contains("id=\"sigHistFilter\"");
        assertThat(html).containsPattern("class=\"history-entry sig-log-entry\"\\s+data-doc-type=\"1\"");
        assertThat(html).containsPattern("class=\"history-entry sig-log-entry\"\\s+data-doc-type=\"3\"");
        assertThat(html).contains("hist-sig-SIGNED").contains("hist-sig-WITHDRAWN");
        assertThat(html).as("เหตุการณ์ของคำร้องอื่นไม่ปน").doesNotContain("hist-sig-DECLINED");
        assertThat(html).as("เหตุการณ์ของเฟส 2 ไม่ปน").doesNotContain("hist-sig-EXPIRED");
    }
}
