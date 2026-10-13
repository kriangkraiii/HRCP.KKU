package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ส่งเวียนไม่ได้เพราะช่องของเจ้าหน้าที่ว่าง — ข้อความบอกว่าช่องไหน")
class AdminFieldLabelsTest {

    @Test
    @DisplayName("ตำแหน่งคณบดีที่ว่างบอกให้ไปแก้ที่ข้อมูลผู้ใช้ เพราะแก้ในฟอร์มไม่ได้")
    void deanPositionPointsToTheUserProfile() {
        assertThat(SignatureWorkflowService.describeAdminFields(List.of("dean_position")))
                .contains("ตำแหน่งคณบดี").contains("ข้อมูลผู้ใช้");
    }

    @Test
    @DisplayName("ช่องตรวจและหมายเหตุของเจ้าหน้าที่บอกเลขข้อ ช่องที่ไม่รู้จักแสดงชื่อในระบบ")
    void numberedAndUnknownFields() {
        assertThat(SignatureWorkflowService.describeAdminFields(List.of("chk_off_2", "text_3", "something_else")))
                .isEqualTo("ช่องตรวจของเจ้าหน้าที่ข้อ 2, หมายเหตุข้อ 3, something_else");
    }
}
