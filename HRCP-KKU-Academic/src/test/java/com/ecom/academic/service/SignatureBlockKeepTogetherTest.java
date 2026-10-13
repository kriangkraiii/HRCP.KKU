package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * บล็อกลงนามไม่ถูกตัดหน้า — "ลงชื่อ" ชื่อในวงเล็บ และตำแหน่ง ติดกันด้วย keep-with-next
 *
 * <p>p2doc_7 ที่บล็อกของคณบดีอยู่ชิดท้ายหน้า ลงนามแล้วชื่อกับตำแหน่งตกไปหน้าถัดไปแยกจากลายเซ็น
 */
@DisplayName("บล็อกลงนามไม่แยกหน้า")
class SignatureBlockKeepTogetherTest {

    private final DocumentGenerationService service = new DocumentGenerationService();

    private static String p(String text) {
        return "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>" + text + "</w:t></w:r></w:p>";
    }

    private static String body(String inner) {
        return "<w:document><w:body>" + inner + "</w:body></w:document>";
    }

    private static int keeps(String xml) {
        return xml.split("<w:keepNext/>", -1).length - 1;
    }

    @Test
    @DisplayName("ลงชื่อ + (ชื่อ) + ตำแหน่ง + บทบาท: ทุกย่อหน้ายกเว้นย่อหน้าสุดท้ายติดกับย่อหน้าถัดไป")
    void wholeBlockKeepsTogether() {
        String xml = body(p("ข้อความ") + p("ลงชื่อ.......................")
                + p("({{hr_officer_name}})") + p("{{hr_officer_position}}") + p("เจ้าหน้าที่ผู้ตรวจสอบ") + p(""));

        String kept = service.keepSignatureBlocksTogether(xml);

        assertThat(keeps(kept)).isEqualTo(3);
        assertThat(kept).contains("<w:keepNext/><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>ลงชื่อ")
                .contains("<w:keepNext/><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>({{hr_officer_name}})")
                .contains("<w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>เจ้าหน้าที่ผู้ตรวจสอบ");
    }

    @Test
    @DisplayName("บล็อกในตาราง และวงเล็บที่ไม่มีบรรทัดลงชื่อด้านบน ไม่ถูกแตะ")
    void tablesAndProseAreLeftAlone() {
        String table = "<w:tbl><w:tr><w:tc>" + p("ลงชื่อ.......................") + p("({{dean_name}})")
                + p("คณบดี") + "</w:tc></w:tr></w:tbl>";
        String xml = body(table + p("ข้อความ") + p("({{title}}) ของผู้ขอ") + p("ต่อ"));

        assertThat(service.keepSignatureBlocksTogether(xml)).isEqualTo(xml);
    }
}
