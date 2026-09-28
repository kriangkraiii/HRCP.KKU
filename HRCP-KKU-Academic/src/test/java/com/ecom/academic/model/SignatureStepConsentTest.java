package com.ecom.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignatureStepConsentTest {

    @Test
    @DisplayName("คำรับรองระบุชื่อ ตำแหน่ง และเอกสาร ตามด้วยเนื้อความทางกฎหมาย")
    void statementNamesSignerRoleAndDocument() {
        String text = SignatureStep.consentStatement("สมชาย ใจดี", "คณบดี", "แบบขอกำหนดตำแหน่ง");

        assertThat(text)
                .startsWith("ข้าพเจ้า สมชาย ใจดี ในฐานะคณบดี ได้ตรวจสอบเอกสาร \"แบบขอกำหนดตำแหน่ง\" แล้ว ")
                .endsWith(SignatureStep.CONSENT_TERMS);
    }

    @Test
    @DisplayName("ข้อมูลที่ว่างถูกข้ามไป ไม่เหลือเครื่องหมายคำพูดเปล่า")
    void blankPartsAreLeftOut() {
        String text = SignatureStep.consentStatement("สมชาย ใจดี", "  ", null);

        assertThat(text)
                .startsWith("ข้าพเจ้า สมชาย ใจดี ได้ตรวจสอบเอกสารฉบับนี้ แล้ว ")
                .doesNotContain("ในฐานะ")
                .doesNotContain("\"\"");
    }
}
