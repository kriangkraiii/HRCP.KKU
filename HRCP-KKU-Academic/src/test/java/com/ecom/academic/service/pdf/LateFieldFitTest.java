package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ค่าที่เจ้าหน้าที่กรอกทีหลัง ใส่ลงช่องในไฟล์ลงนามได้หรือไม่")
class LateFieldFitTest {

    /** ช่องหมายเหตุของเอกสารที่ 2 ตามเทมเพลตเดิม (ซอง 71): กว้าง 36pt แถว 1 มีบรรทัดเดียว แถว 4 มีสองบรรทัด */
    private static final String OLD_DOC2_LAYOUT = "{\"texts\":["
            + "{\"field\":\"text_1\",\"width\":36.024017,\"fontSize\":14.0,\"lines\":1},"
            + "{\"field\":\"text_4\",\"width\":36.024017,\"fontSize\":14.0,\"lines\":2},"
            + "{\"field\":\"text_5\",\"width\":36.024017,\"fontSize\":14.0,\"lines\":1}]}";

    @Test
    @DisplayName("ซอง 71: หมายเหตุข้อ 1 \"ไม่เห็นเอกสาร\" ยาวเกิน — ข้อ 4 ตัดสองบรรทัดได้ ข้อความสั้นผ่าน")
    void theRemarkThatBrokeEnvelope71() {
        var problems = LateFieldFit.check(OLD_DOC2_LAYOUT, Map.of(
                "text_1", "ไม่เห็นเอกสาร", "text_4", "ไม่เห็นอะไรเลย", "text_5", "ไม่มี"));

        assertThat(problems).extracting(LateFieldFit.Problem::field).containsExactly("text_1");
        assertThat(problems.get(0).label()).isEqualTo("หมายเหตุข้อ 1");
        assertThat(LateFieldFit.message(problems)).contains("หมายเหตุข้อ 1").contains("ไม่เห็นเอกสาร");
    }

    @Test
    @DisplayName("ค่าว่าง ช่องที่ไฟล์ไม่มี และซองที่ไม่มีผังช่อง — ไม่นับว่ายาวเกิน")
    void nothingToCheck() {
        assertThat(LateFieldFit.check(OLD_DOC2_LAYOUT, Map.of("text_1", "", "not_in_file", "ยาวมากมายเกินช่องแน่นอน")))
                .isEmpty();
        assertThat(LateFieldFit.check(null, Map.of("text_1", "ไม่เห็นเอกสาร"))).isEmpty();
    }

    @Test
    @DisplayName("ใช้กติกาเดียวกับตอนเขียนลงไฟล์จริง — ผ่านที่นี่ต้องเขียนได้ ไม่ผ่านต้องถูกปฏิเสธ")
    void sameRuleAsFill() {
        PdfIncrementService pdf = new PdfIncrementService(ThaiText.sarabun());
        assertThat(pdf.fits("ไม่เห็นเอกสาร", 14f, 36.024017f, 1)).isFalse();
        assertThat(pdf.fits("ไม่เห็นเอกสาร", 14f, 80f, 1)).isTrue();
        assertThat(pdf.fits("ไม่เห็นอะไรเลย", 14f, 36.024017f, 2)).isTrue();
    }
}
