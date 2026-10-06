package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsCommentStripper — ตัดคอมเมนต์ JS โดยไม่แตะโค้ด")
class JsCommentStripperTest {

    private static String strip(String js) {
        return JsCommentStripper.strip(js);
    }

    @Test
    void removesLineAndBlockComments() {
        assertThat(strip("var a = 1; // the user's theme\nvar b = 2;"))
                .isEqualTo("var a = 1; \nvar b = 2;");
        assertThat(strip("var a = /* admin */ 1;")).isEqualTo("var a =   1;");
    }

    @Test
    @DisplayName("คอมเมนต์หลายบรรทัดทิ้งการขึ้นบรรทัดไว้ — ASI ยังเห็นเหมือนเดิม")
    void multiLineBlockLeavesANewline() {
        assertThat(strip("return /*\n*/ x")).isEqualTo("return \n x");
    }

    @Test
    void keepsWindowsLineEndings() {
        assertThat(strip("a(); // note\r\nb();")).isEqualTo("a(); \r\nb();");
    }

    @Test
    @DisplayName("// และ /* ในสตริงหรือ URL ไม่ใช่คอมเมนต์")
    void leavesStringsAlone() {
        String js = "var u = 'https://x.kku.ac.th/a'; var s = \"/* no */\"; var e = 'it\\'s // fine';";
        assertThat(strip(js)).isEqualTo(js);
    }

    @Test
    @DisplayName("regex ที่มี // หรือ /* อยู่ข้างในยังอยู่ครบ")
    void leavesRegexLiteralsAlone() {
        String js = "s.replace(/\\/\\/+/g, '/'); var r = /[/*]+/; if (ok) return /a\\/b/.test(x);";
        assertThat(strip(js)).isEqualTo(js);
    }

    @Test
    @DisplayName("ตัวหารไม่ถูกมองเป็น regex")
    void divisionIsNotARegex() {
        assertThat(strip("var h = w / 2 / dpr; // half\nx = a[i] / b;"))
                .isEqualTo("var h = w / 2 / dpr; \nx = a[i] / b;");
    }

    @Test
    @DisplayName("template literal — ข้อความคงเดิม คอมเมนต์ใน ${...} ถูกตัด")
    void followsTemplateLiterals() {
        assertThat(strip("var t = `a // b ${ x /* c */ + {k: 1}.k } d /* e */`; // f"))
                .isEqualTo("var t = `a // b ${ x   + {k: 1}.k } d /* e */`; ");
        assertThat(strip("`${`${y}`}` // z")).isEqualTo("`${`${y}`}` ");
    }

    @Test
    void codeWithoutCommentsIsReturnedAsIs() {
        String js = "var a = b / c;";
        assertThat(strip(js)).isSameAs(js);
    }
}
