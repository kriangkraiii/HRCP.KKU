package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.service.pdf.PdfIncrementService.DoesNotFitException;

@DisplayName("ข้อความตอนค่ายาวเกินช่องขณะลงนาม — บอกช่อง และบอกว่าใครต้องแก้")
class DoesNotFitMessageTest {

    private static SignatureRequest doc2() {
        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(SignatureModule.ACADEMIC);
        envelope.setDocumentType(2);
        return envelope;
    }

    @Test
    @DisplayName("ช่องของเจ้าหน้าที่ — บอกผู้ลงนามให้แจ้งเจ้าหน้าที่ ไม่ใช่ให้ย่อเอง")
    void staffFieldPointsAtTheStaff() {
        String msg = SignatureWorkflowService.doesNotFitMessage(doc2(),
                new DoesNotFitException("text_1", "ไม่เห็นเอกสาร"));
        assertThat(msg).contains("หมายเหตุข้อ 1").contains("ไม่เห็นเอกสาร").contains("แจ้งเจ้าหน้าที่");
    }

    @Test
    @DisplayName("ช่องของผู้ลงนามเอง (ความเห็น) — ให้ย่อเองแล้วลงนามใหม่")
    void ownFieldAsksTheSigner() {
        String msg = SignatureWorkflowService.doesNotFitMessage(doc2(),
                new DoesNotFitException("dean_comment", "ยาวมาก"));
        assertThat(msg).contains("ความเห็น").contains("ลงนามอีกครั้ง").doesNotContain("แจ้งเจ้าหน้าที่");
    }
}
