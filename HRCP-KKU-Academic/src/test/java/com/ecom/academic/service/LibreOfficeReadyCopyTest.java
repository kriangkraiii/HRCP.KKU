package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * สำเนาที่ส่งเข้า LibreOffice ต้องจัดชิดขอบได้ทุกเทมเพลต ไม่ใช่แค่ฉบับที่เคยเปิดดู
 *
 * <p>{@code RightMarginRendersFlushTest} เรนเดอร์เอกสารที่ 1 ออกมาเป็น PDF จริงแล้ววัด
 * ขอบขวา ซึ่งพิสูจน์ว่า<em>กลไก</em>ใช้ได้ แต่ช้าเกินกว่าจะทำแบบนั้นกับทุกฉบับ และต้องมี
 * LibreOffice ติดตั้งอยู่ด้วย เทสต์ชุดนี้จึงรับหน้าที่อีกครึ่ง: ยืนยันว่ากลไกนั้น<em>ไปถึง</em>
 * ทุกเทมเพลตและทุกไฟล์ XML ข้างใน
 *
 * <p>ที่ต้องตรวจทีละไฟล์ข้างในเพราะการจัดชิดขอบไม่ได้อยู่แต่ใน {@code word/document.xml} —
 * มีอยู่ใน {@code word/styles.xml} ด้วย ถ้าเผลอเขียนให้ครอบแค่ตัวเอกสาร ย่อหน้าที่รับ
 * รูปแบบมาจาก style จะยังชิดซ้ายอยู่เงียบ ๆ
 */
@DisplayName("สำเนาสำหรับ LibreOffice ของทุกเทมเพลต")
class LibreOfficeReadyCopyTest {

    private static final Path TEMPLATE_DIR = Path.of("src/main/resources/templates/docx");

    /** ค่าที่ LibreOffice ไม่รู้จักและตกกลับไปชิดซ้ายเงียบ ๆ */
    private static final String UNSUPPORTED = "w:val=\"thaiDistribute\"";

    private static List<Path> templates() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATE_DIR)) {
            return files.filter(p -> p.toString().endsWith(".docx"))
                    // ไฟล์ล็อกของ Word ที่เปิดค้างไว้ ไม่ใช่เอกสาร
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .sorted()
                    .toList();
        }
    }

    /** เนื้อไฟล์ XML ทุกตัวใน docx แยกตามชื่อ entry */
    private static Map<String, String> xmlPartsOf(byte[] docx) throws IOException {
        Map<String, String> parts = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml")) {
                    parts.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return parts;
    }

    @TestFactory
    @DisplayName("ทุกเทมเพลต: สำเนาที่ส่งเข้า LibreOffice ไม่เหลือค่าที่มันไม่รู้จัก")
    List<DynamicTest> everyTemplateIsRewritten() throws IOException {
        List<Path> templates = templates();
        assertThat(templates)
                .as("ไม่พบเทมเพลตเลย — พาธเปลี่ยนไปหรือเปล่า")
                .hasSizeGreaterThanOrEqualTo(18);

        List<DynamicTest> tests = new ArrayList<>();
        for (Path template : templates) {
            tests.add(DynamicTest.dynamicTest(template.getFileName().toString(), () -> {
                byte[] original = Files.readAllBytes(template);
                byte[] forConversion = DocumentGenerationService.forLibreOffice(original);

                Map<String, String> before = xmlPartsOf(original);
                Map<String, String> after = xmlPartsOf(forConversion);

                assertThat(after.keySet())
                        .as("ประกอบ zip กลับแล้วต้องมีไฟล์ครบเท่าเดิม")
                        .containsExactlyElementsOf(before.keySet());

                assertThat(after)
                        .as("สำเนาที่ส่งเข้า LibreOffice ต้องไม่เหลือ thaiDistribute เลยสักไฟล์")
                        .allSatisfy((name, xml) -> assertThat(xml).doesNotContain(UNSUPPORTED));

                // ส่วนที่ไม่เกี่ยวต้องไม่ถูกแตะ — เทียบทีละไฟล์หลังตัดค่าที่ตั้งใจเปลี่ยนออก
                before.forEach((name, xml) -> assertThat(
                        after.get(name).replace("w:val=\"both\"", "w:val=\"thaiDistribute\""))
                        .as("%s ถูกแก้เกินกว่าที่ตั้งใจ", name)
                        .isEqualTo(xml.replace("w:val=\"both\"", "w:val=\"thaiDistribute\"")));
            }));
        }
        return tests;
    }

    @Test
    @DisplayName("เทมเพลตต้นฉบับยังเป็น thaiDistribute อยู่ — เราแก้แค่สำเนา")
    void theTemplatesThemselvesAreUntouched() throws IOException {
        List<String> withoutThaiJustify = new ArrayList<>();
        for (Path template : templates()) {
            boolean hasIt = xmlPartsOf(Files.readAllBytes(template)).values().stream()
                    .anyMatch(xml -> xml.contains(UNSUPPORTED));
            if (!hasIt) {
                withoutThaiJustify.add(template.getFileName().toString());
            }
        }

        // doc_6 เป็นใบปะหน้าที่มีย่อหน้าเดียวและจัดกึ่งกลาง จึงไม่มีการจัดชิดขอบให้แปลง
        assertThat(withoutThaiJustify)
                .as("เทมเพลตควรยังเป็น thaiDistribute เพราะ Word จัดระยะภาษาไทยด้วยค่านี้ได้ดีที่สุด")
                .containsExactly("doc_6.docx");
    }
}
