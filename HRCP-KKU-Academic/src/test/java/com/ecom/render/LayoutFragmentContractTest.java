package com.ecom.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * กติกาของ layout: อะไรที่อยู่นอก {@code <section>} จะไม่มีวันถึงเบราว์เซอร์
 *
 * <p>ทุกหน้าในระบบประกาศตัวเองแบบนี้:
 *
 * <pre>{@code
 * <html th:replace="~{academic/base_academic :: layout(~{::section})}">
 * }</pre>
 *
 * <p>Thymeleaf จะ <b>แทนที่</b> เอกสารทั้งฉบับด้วย layout แล้วส่งเข้าไปเฉพาะสิ่งที่
 * {@code ~{::section}} เลือกได้ นั่นคือ element {@code <section>} เท่านั้น
 * มาร์กอัปอื่นในไฟล์หน้า — รวมถึง {@code <script>} ที่วางไว้ท้ายไฟล์ตามความเคยชิน
 * ของหน้า HTML ธรรมดา — <b>ถูกทิ้งทั้งหมดโดยไม่มีคำเตือน</b>
 *
 * <p>เขียนโค้ดผิดแบบนี้แล้วไม่มีอะไรพัง ไม่มี error ใน log ไม่มี 500 หน้ายัง
 * render ได้สวยงามตามปกติ — มีแค่ฟีเจอร์ที่เงียบหายไป ซึ่งเป็นบั๊กชนิดที่ต้อง
 * เปิดเบราว์เซอร์กดเองถึงจะเจอ และเป็นเหตุผลที่กติกานี้ต้องถูกบังคับด้วยเทส
 *
 * <p>เทสนี้อ่านไฟล์เทมเพลตตรง ๆ ไม่ต้องมี Spring context จึงรันจบในหลักมิลลิวินาที
 */
@DisplayName("layout: อะไรที่อยู่นอก <section> จะไม่ถึงเบราว์เซอร์")
class LayoutFragmentContractTest {

    private static final Path TEMPLATES =
            Path.of("src", "main", "resources", "templates");

    /** หน้าที่ประกาศใช้ layout โดยส่งเข้าไปเฉพาะ ~{::section} */
    private static final Pattern USES_SECTION_LAYOUT = Pattern.compile(
            "th:replace\\s*=\\s*\"~\\{academic/base_academic\\s*::\\s*layout\\s*\\(\\s*~\\{::section\\}");

    private static final Pattern SECTION = Pattern.compile(
            "<section\\b.*?</section>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern SCRIPT = Pattern.compile(
            "<script\\b[^>]*>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static List<Path> templates() {
        try (Stream<Path> walk = Files.walk(TEMPLATES)) {
            return walk.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("ไม่มีหน้าไหนวาง <script> ไว้นอก <section>")
    void noPageLeavesAScriptOutsideItsSection() {
        Map<String, List<String>> stranded = new LinkedHashMap<>();

        for (Path template : templates()) {
            String source = read(template);
            if (!USES_SECTION_LAYOUT.matcher(source).find()) {
                continue;
            }

            // ตัดทุก <section> ออก เหลือไว้แต่ส่วนที่ layout จะทิ้ง
            String outside = SECTION.matcher(source).replaceAll("");

            List<String> lost = new ArrayList<>();
            Matcher script = SCRIPT.matcher(outside);
            while (script.find()) {
                lost.add(describe(script.group()));
            }
            if (!lost.isEmpty()) {
                stranded.put(TEMPLATES.relativize(template).toString(), lost);
            }
        }

        assertThat(stranded)
                .as("""
                        สคริปต์เหล่านี้อยู่นอก <section> จึงถูก layout ทิ้ง ไม่เคยถูกส่งไปเบราว์เซอร์เลย
                        ย้ายเข้าไปไว้ใน <section> ของหน้านั้น""")
                .isEmpty();
    }

    /** บอกว่าเป็นสคริปต์ตัวไหน โดยไม่ต้องพ่นเนื้อสคริปต์ทั้งก้อนออกมา */
    private static String describe(String scriptTag) {
        Matcher src = Pattern.compile("src\\s*=\\s*\"([^\"]+)\"").matcher(scriptTag);
        if (src.find()) {
            return "src=" + src.group(1);
        }
        long lines = scriptTag.lines().count();
        return "inline " + lines + " บรรทัด";
    }
}
