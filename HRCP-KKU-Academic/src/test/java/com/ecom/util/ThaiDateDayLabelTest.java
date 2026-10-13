package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ช่องวันที่ที่ขึ้นต้นด้วย \"วันที่\" — เทมเพลตพิมพ์คำนี้เองแล้ว")
class ThaiDateDayLabelTest {

    @Test
    @DisplayName("ตัวเติมวันที่รุ่นก่อนใส่ \"วันที่\" นำหน้า — ตัดออก ทั้งเลขไทยและเลขอารบิก")
    void stripsTheLabelTheAutofillUsedToAdd() {
        assertThat(ThaiDateUtil.withoutDayLabel("date", "วันที่ ๑๓ ตุลาคม พ.ศ. ๒๕๖๙")).isEqualTo("๑๓ ตุลาคม พ.ศ. ๒๕๖๙");
        assertThat(ThaiDateUtil.withoutDayLabel("certification_date", "วันที่ 1 ตุลาคม 2569"))
                .isEqualTo("1 ตุลาคม 2569");
        assertThat(ThaiDateUtil.withoutDayLabel("DATE", "  วันที่๕ มกราคม ๒๕๖๙")).isEqualTo("๕ มกราคม ๒๕๖๙");
    }

    @Test
    @DisplayName("ไม่แตะค่าอื่น: ช่องที่ไม่ใช่วันที่ ค่าที่ไม่ได้ตามด้วยตัวเลข และค่าว่าง")
    void leavesEverythingElse() {
        assertThat(ThaiDateUtil.withoutDayLabel("remark", "วันที่ 1 ตุลาคม 2569")).isEqualTo("วันที่ 1 ตุลาคม 2569");
        assertThat(ThaiDateUtil.withoutDayLabel("date", "วันที่ตามประกาศ")).isEqualTo("วันที่ตามประกาศ");
        assertThat(ThaiDateUtil.withoutDayLabel("date", "๑๓ ตุลาคม ๒๕๖๙")).isEqualTo("๑๓ ตุลาคม ๒๕๖๙");
        assertThat(ThaiDateUtil.withoutDayLabel("date", null)).isNull();
        assertThat(ThaiDateUtil.withoutDayLabel(null, "วันที่ 1")).isEqualTo("วันที่ 1");
    }

    @Test
    @DisplayName("ตัดเฉพาะช่องที่เทมเพลตพิมพ์ \"วันที่\" ไว้หน้าช่อง — \"เมื่อ {{...}}\" ยังต้องการคำนั้น")
    void onlyWhereTheTemplatePrintsTheLabel() {
        String xml = "<w:t>ที่ {{memo_no}}</w:t><w:t>วันที่</w:t><w:t xml:space=\"preserve\"> {{date}}</w:t>"
                + "<w:t>ดำรงตำแหน่งอาจารย์ เมื่อ {{lecturer_appointment_date}}</w:t>";
        assertThat(ThaiDateUtil.followsDayLabel(xml, xml.indexOf("{{date}}"))).isTrue();
        assertThat(ThaiDateUtil.followsDayLabel(xml, xml.indexOf("{{lecturer_appointment_date}}"))).isFalse();
        assertThat(ThaiDateUtil.followsDayLabel(xml, xml.indexOf("{{memo_no}}"))).isFalse();
    }
}
