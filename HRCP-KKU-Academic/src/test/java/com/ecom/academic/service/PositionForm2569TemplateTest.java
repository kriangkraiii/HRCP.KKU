package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * เทมเพลตเฟส 2 ตามแบบฟอร์มข้อบังคับ พ.ศ. 2569 — เอกสารที่ 1 (แบบ ก.พ.ว. มข.๐๓ ฉบับเต็ม ส่วนที่ ๑–๕) และ
 * เอกสารที่ 9 (แบบแสดงหลักฐานการมีส่วนร่วมในผลงานทางวิชาการ)
 *
 * <p>แบบฟอร์มที่ได้มาเป็นฉบับเปล่า ไม่มี placeholder สักตัว ทุกช่องจึงถูกฝังเข้าไปใหม่ทั้งหมด
 * เทสต์ชุดนี้เดินผ่านตัวสร้างเอกสารตัวจริงแล้วอ่านข้อความที่ออกมา เพราะความผิดพลาดที่น่ากลัว
 * ที่สุดของงานแบบนี้คือ<em>ช่องที่หายไปเงียบ ๆ</em> — placeholder สะกดไม่ตรงกับชื่อช่องในฟอร์ม
 * เอกสารก็ยังสร้างได้ปกติ แค่ช่องนั้นว่าง และไม่มีใครรู้จนกว่าจะพิมพ์ออกมาดู
 */
@DisplayName("เทมเพลตเฟส 2 ตามแบบฟอร์ม พ.ศ. 2569")
class PositionForm2569TemplateTest {

    private final DocumentGenerationService service = new DocumentGenerationService();
    private final ObjectMapper mapper = new ObjectMapper();

    private String render(int documentType, Map<String, String> data) throws IOException {
        byte[] docx = service.generateP2PreviewDocx(documentType, mapper.writeValueAsString(data));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static byte[] samplePng() throws IOException {
        BufferedImage image = new BufferedImage(30, 10, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String documentXml(byte[] docx) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("ไม่พบ word/document.xml");
    }

    private static int count(String haystack, String needle) {
        Matcher m = Pattern.compile(Pattern.quote(needle)).matcher(haystack);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Nested
    @DisplayName("เอกสารที่ 1 — แบบ ก.พ.ว. มข.๐๓")
    class DocumentOne {

        /** ผู้ขอ รศ. ที่กรอกทุกช่องของตัวเองหนึ่งรายการ */
        private Map<String, String> associateApplicant() {
            Map<String, String> d = new LinkedHashMap<>();
            d.put("target_position", "รองศาสตราจารย์");
            d.put("method", "ปกติ");
            d.put("major", "วิทยาการคอมพิวเตอร์");
            d.put("major_code", "๐๑๐๓");
            d.put("sub_major", "ปัญญาประดิษฐ์");
            d.put("sub_major_code", "๐๑๐๓๐๑");
            d.put("title", "ผศ.ดร.");
            d.put("applicant_name", "สมชาย ใจดีวิชาการ");
            d.put("department", "สาขาวิชาวิทยาการคอมพิวเตอร์");
            d.put("faculty", "วิทยาลัยการคอมพิวเตอร์");
            d.put("university", "ขอนแก่น");
            d.put("birth_date", "๑ มกราคม ๒๕๒๐");
            d.put("age", "๔๙");
            d.put("education_no_1", "๑.๓.๑");
            d.put("education_degree_1", "ปร.ด.");
            d.put("education_major_1", "วิทยาการคอมพิวเตอร์");
            d.put("education_year_1", "๒๕๕๐");
            d.put("education_institution_1", "มหาวิทยาลัยขอนแก่น");
            d.put("education_country_1", "ประเทศไทย");
            d.put("current_position", "ผู้ช่วยศาสตราจารย์");
            d.put("current_salary", "๕๐,๐๐๐");
            d.put("lecturer_appointment_date", "๑ มิถุนายน ๒๕๕๐");
            d.put("assistant_method", "ปกติ");
            d.put("assistant_department", "วิทยาการคอมพิวเตอร์");
            d.put("assistant_appointment_date", "๑ มิถุนายน ๒๕๕๕");
            d.put("years", "๑๙");
            d.put("months", "๓");
            d.put("other_position_no_1", "๑");
            d.put("other_position_1", "หัวหน้าสาขาวิชา");
            d.put("international_speaker_last_5_years_no_1", "๑");
            d.put("international_speaker_last_5_years_1", "วิทยากรรับเชิญ ICCSE");
            d.put("teaching_level_1", "ปริญญาตรี");
            d.put("teaching_subject_1", "โครงสร้างข้อมูล");
            d.put("teaching_hours_per_week_1", "๓");
            d.put("teaching_semester_1", "๑/๒๕๖๘");
            d.put("reseach", "งานวิจัยที่ได้รับทุนในฐานะหัวหน้าโครงการ");
            d.put("academic_service", "กรรมการวิชาการ");
            d.put("administration", "หัวหน้าสาขาวิชา");
            d.put("other", "งานพัฒนาหลักสูตร");
            d.put("assoc_research_working_no_1", "๑");
            d.put("assoc_research_working_1", "การจำแนกภาพด้วยการเรียนรู้เชิงลึก");
            d.put("assoc_used_research_1", "not_used");
            d.put("sign_date", "๑๐ มีนาคม พ.ศ. ๒๕๖๙");
            return d;
        }

        @Test
        @DisplayName("ทุกช่องที่กรอกขึ้นบนเอกสาร และไม่มี placeholder หลงเหลือ")
        void everyFilledFieldLands() throws IOException {
            Map<String, String> data = associateApplicant();
            String doc = render(1, data);

            assertThat(doc).doesNotContain("{{").doesNotContain("}}");
            for (String value : data.values()) {
                if (value.length() > 1 && !"not_used".equals(value)) {
                    assertThat(doc).as("ค่า \"%s\" ต้องขึ้นบนเอกสาร", value).contains(value);
                }
            }
            assertThat(doc)
                    .contains("เพื่อขอดำรงตำแหน่ง รองศาสตราจารย์")
                    .contains("(โดยวิธีปกติ)")
                    .contains("ของ ผศ.ดร.สมชาย ใจดีวิชาการ")
                    .contains("๒.๕.๑ หัวหน้าสาขาวิชา")
                    .contains("๔.๒.๑.๑ การจำแนกภาพด้วยการเรียนรู้เชิงลึก")
                    .contains("(ผศ.ดร.สมชาย ใจดีวิชาการ)");
        }

        @Test
        @DisplayName("เป็นแบบฟอร์มฉบับเต็ม ครบทั้งส่วนที่ ๑–๕")
        void allFivePartsArePresent() throws IOException {
            String doc = render(1, associateApplicant());

            assertThat(doc)
                    .contains("ส่วนที่ ๑")
                    .contains("ส่วนที่ ๒")
                    .contains("แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา")
                    .contains("ส่วนที่ ๓")
                    .contains("แบบประเมินผลการสอน")
                    .contains("ส่วนที่ ๔")
                    .contains("ส่วนที่  ๕ มติสภามหาวิทยาลัยขอนแก่น");
        }

        @Test
        @DisplayName("ส่วนที่ ๒ ส่วนหัวมาจากข้อมูลของผู้ยื่นชุดเดียวกับส่วนที่ ๑")
        void partTwoHeaderRepeatsTheApplicant() throws IOException {
            String doc = partTwoOf(render(1, associateApplicant()));

            assertThat(doc)
                    .contains("แบบประเมินแต่งตั้งให้ดำรงตำแหน่ง รองศาสตราจารย์")
                    .contains("(โดยวิธีปกติ)")
                    .contains("ในสาขาวิชา วิทยาการคอมพิวเตอร์  (รหัส ๐๑๐๓)")
                    .contains("ของ ผศ.ดร.สมชาย ใจดีวิชาการ")
                    .contains("ได้ตรวจสอบคุณสมบัติเฉพาะสำหรับตำแหน่ง รองศาสตราจารย์ แล้วเห็นว่า ผศ.ดร.สมชาย ใจดีวิชาการ");
        }

        @Test
        @DisplayName("ส่วนที่ ๒ ก่อนผู้บังคับบัญชาลงนาม ยังเป็นแบบฟอร์มเปล่า")
        void partTwoIsBlankUntilTheSupervisorsSign() throws IOException {
            String doc = partTwoOf(render(1, associateApplicant()));

            assertThat(doc)
                    .contains("เป็นผู้มีคุณสมบัติ (ครบถ้วน / ไม่ครบถ้วน) ตามหลักเกณฑ์")
                    .contains("เป็นผู้มีคุณสมบัติ ...(เข้าข่าย/ไม่เข้าข่าย) ที่จะได้รับการแต่งตั้ง")
                    .contains("วันที่ ........เดือน.................พ.ศ......");
        }

        @Test
        @DisplayName("ส่วนที่ ๒ ผลการตรวจและวันที่ลงนามของผู้บังคับบัญชาขึ้นบนเอกสาร")
        void partTwoCarriesTheSupervisorsAnswers() throws IOException {
            Map<String, String> data = associateApplicant();
            data.put("qualification_status", "ครบถ้วน");
            data.put("dean_qualification_status", "เข้าข่าย");
            data.put("department_head_name", "รศ.ดร.หัวหน้า สาขาวิชา");
            data.put("dean_name", "ศ.ดร.คณบดี วิทยาลัย");
            data.put("head_sign_date", "1 ตุลาคม 2569");
            data.put("dean_sign_date", "3 ตุลาคม 2569");

            String doc = partTwoOf(render(1, data));

            assertThat(doc)
                    .contains("เป็นผู้มีคุณสมบัติ ครบถ้วน ตามหลักเกณฑ์")
                    .contains("เป็นผู้มีคุณสมบัติ เข้าข่าย ที่จะได้รับการแต่งตั้งให้ดำรงตำแหน่ง รองศาสตราจารย์")
                    .contains("(รศ.ดร.หัวหน้า สาขาวิชา)")
                    .contains("(ศ.ดร.คณบดี วิทยาลัย)")
                    .as("แบบฟอร์มพิมพ์เลขไทย วันที่ที่ระบบเติมต้องเป็นเลขไทยด้วย")
                    .contains("วันที่ ๑ ตุลาคม ๒๕๖๙")
                    .contains("วันที่ ๓ ตุลาคม ๒๕๖๙");
        }

        @Test
        @DisplayName("ส่วนที่ ๓ เติมจากผลประเมินการสอน")
        void partThreeCarriesTheTeachingEvaluation() throws IOException {
            Map<String, String> data = associateApplicant();
            data.put("s3_meeting_no", "3/2568");
            data.put("s3_meeting_date", "15 มกราคม 2569");
            data.put("s3_university", "มหาวิทยาลัยขอนแก่น");
            data.put("s3_course_code", "CP353001");
            data.put("s3_course_name", "โครงสร้างข้อมูล");
            data.put("s3_level", "ชำนาญพิเศษ");
            data.put("s3_quality", "อยู่");
            data.put("s3_chair_name", "ศ.ดร.ประธาน อนุกรรมการ");
            data.put("s3_sign_date", "20 มกราคม 2569");

            String doc = render(1, data);

            assertThat(doc)
                    .contains("ในการประชุมครั้งที่ ๓/๒๕๖๘ เมื่อวันที่ ๑๕ มกราคม ๒๕๖๙")
                    .contains("คณะกรรมการพิจารณาตำแหน่งทางวิชาการ มหาวิทยาลัยขอนแก่น")
                    .contains("รหัสวิชา CP๓๕๓๐๐๑ รายวิชา โครงสร้างข้อมูล")
                    .contains("เป็นผู้มีความชำนาญพิเศษ ในการสอนมีคุณภาพอยู่ในหลักเกณฑ์")
                    .contains("(ศ.ดร.ประธาน อนุกรรมการ)")
                    .contains("วันที่ ๒๐ มกราคม ๒๕๖๙");
        }

        @Test
        @DisplayName("ส่วนที่ ๓ เมื่อไม่มีผลประเมินให้ดึง ยังเป็นแบบฟอร์มเปล่า")
        void partThreeIsBlankWithoutAnEvaluation() throws IOException {
            String doc = render(1, associateApplicant());

            assertThat(doc)
                    .contains("ในการประชุมครั้งที่ ....... เมื่อวันที่ ................")
                    .contains("เป็นผู้มีความ....(ชำนาญ/ชำนาญพิเศษ/เชี่ยวชาญ).....")
                    .contains("ในการสอนมีคุณภาพ..(อยู่/ไม่อยู่).....ในหลักเกณฑ์");
        }

        @Test
        @DisplayName("ส่วนที่ ๔–๕ ของกองทรัพยากรบุคคลและสภามหาวิทยาลัย ตรงกับแบบฟอร์มเปล่าทุกตัวอักษร")
        void partsFourAndFiveMatchTheBlankForm() throws IOException {
            String doc = render(1, associateApplicant());
            String blank;
            try (XWPFDocument document = new XWPFDocument(new ClassPathResource(
                    "templates/docx/Phase2/แบบ-ก.พ.ว.-มข.๐๓.docx").getInputStream());
                    XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                blank = extractor.getText();
            }

            assertThat(fromPartFour(doc)).isEqualTo(fromPartFour(blank));
        }

        @Test
        @DisplayName("ช่องลงนามของแต่ละคนมีที่เดียว ลายเซ็นจะได้ไม่ไปลงผิดส่วน")
        void everySignatureAnchorAppearsOnce() throws IOException {
            String template;
            try (XWPFDocument document = new XWPFDocument(new ClassPathResource(
                    "templates/docx/Phase2/p2doc_1.docx").getInputStream());
                    XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                template = extractor.getText();
            }

            assertThat(count(template, "({{title}}{{applicant_name}})")).isEqualTo(1);
            assertThat(count(template, "({{department_head_name}})")).isEqualTo(1);
            assertThat(count(template, "({{dean_name}})")).isEqualTo(1);
            assertThat(count(template, "({{s3_chair_name}})")).isEqualTo(1);
        }

        @Test
        @DisplayName("ลายเซ็นประธานอนุกรรมการลงที่ส่วนที่ ๓ ไม่ใช่ที่อื่น")
        void theChairSignatureLandsInPartThree() throws IOException {
            byte[] docx = service.generateSignedP2Docx(1, mapper.writeValueAsString(associateApplicant()),
                    List.of(new StampedSignature(TeachingEvaluationPartResolver.CHAIR_ANCHOR, samplePng(), 300, 100)));
            String xml = documentXml(docx);

            int partThree = xml.indexOf("แบบประเมินผลการสอน");
            int partFour = xml.indexOf("แบบสรุปผลการประเมินผลงานทางวิชาการ");
            int signature = xml.indexOf("r:embed=\"rIdHrcpSig1\"");

            assertThat(signature).isGreaterThan(partThree).isLessThan(partFour);
        }

        private String partTwoOf(String doc) {
            return doc.substring(doc.indexOf("แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา"), doc.indexOf("แบบประเมินผลการสอน"));
        }

        private String fromPartFour(String doc) {
            return doc.substring(doc.indexOf("แบบสรุปผลการประเมินผลงานทางวิชาการ"));
        }

        @Test
        @DisplayName("ไม่มีหัวข้อ \"วิธีที่ ๓\" (Scopus/h-index) ที่แบบ พ.ศ. 2569 ตัดออกแล้ว")
        void methodThreeIsGone() throws IOException {
            assertThat(render(1, associateApplicant()))
                    .doesNotContain("h-index").doesNotContain("Scopus").doesNotContain("Quartile");
        }

        @Test
        @DisplayName("ผลงานเรื่องที่ ๒ ต้องมีบรรทัดคำถามของตัวเอง ไม่ใช่ช่องติ๊กลอย ๆ")
        void aSecondWorkCarriesItsOwnQuestion() throws IOException {
            Map<String, String> data = associateApplicant();
            data.put("assoc_research_working_no_2", "๒");
            data.put("assoc_research_working_2", "การตรวจจับวัตถุแบบเรียลไทม์");
            data.put("assoc_used_research_2", "used");
            data.put("assoc_used_research_year_2", "๒๕๖๕");
            data.put("assoc_used_research_level_2", "ดี");

            String doc = render(1, data);

            assertThat(doc)
                    .contains("๔.๒.๑.๑ การจำแนกภาพด้วยการเรียนรู้เชิงลึก")
                    .contains("๔.๒.๑.๒ การตรวจจับวัตถุแบบเรียลไทม์")
                    .contains("พ.ศ. ๒๕๖๕ และผลการพิจารณาคุณภาพอยู่ในระดับ ดี ตามเกณฑ์ที่มหาวิทยาลัยกำหนด)");
            // จบที่ "รองศาสตราจารย์มาแล้วหรือไม่" — คำถามของหัวข้อ ศ. ขึ้นต้นเหมือนกันทุกคำ
            assertThat(count(doc, "งานวิจัยนี้เคยใช้สำหรับการพิจารณาขอกำหนดตำแหน่งผู้ช่วยศาสตราจารย์ และ/หรือตำแหน่งรองศาสตราจารย์มาแล้วหรือไม่"))
                    .as("เดิมโคลนเฉพาะย่อหน้าที่มี placeholder บรรทัดคำถามของเรื่องที่ ๒ จึงหายไป")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("ตารางการศึกษาและงานสอนเพิ่มแถวตามที่กรอก")
        void educationAndTeachingRowsGrow() throws IOException {
            Map<String, String> data = associateApplicant();
            data.put("education_no_2", "๑.๓.๒");
            data.put("education_degree_2", "วท.ม.");
            data.put("education_major_2", "วิทยาการคอมพิวเตอร์");
            data.put("education_year_2", "๒๕๔๕");
            data.put("education_institution_2", "จุฬาลงกรณ์มหาวิทยาลัย");
            data.put("education_country_2", "ประเทศไทย");
            data.put("teaching_level_2", "บัณฑิตศึกษา");
            data.put("teaching_subject_2", "การเรียนรู้ของเครื่อง");
            data.put("teaching_hours_per_week_2", "๓");
            data.put("teaching_semester_2", "๒/๒๕๖๘");

            String doc = render(1, data);

            assertThat(doc)
                    .contains("๑.๓.๑").contains("ปร.ด.")
                    .contains("๑.๓.๒").contains("วท.ม.").contains("จุฬาลงกรณ์มหาวิทยาลัย")
                    .contains("โครงสร้างข้อมูล").contains("การเรียนรู้ของเครื่อง");
        }

        @Test
        @DisplayName("หัวข้อของตำแหน่งที่ไม่ได้ขอ พิมพ์ออกมาเหมือนแบบฟอร์มเปล่า — ยังมีเลขข้อ")
        void unusedSectionsLookLikeTheBlankForm() throws IOException {
            String doc = render(1, associateApplicant());

            assertThat(doc)
                    .as("ผู้ขอ รศ. ไม่ได้กรอกหัวข้อของ ศ. — เลขท้ายต้องยังขึ้น ไม่ใช่ \"๔.๓.๑.\" ห้อยอยู่")
                    .contains("๔.๓.๑.๑ ........")
                    .contains("๔.๑.๑.๑ ........");
        }
    }

    @Nested
    @DisplayName("เอกสารที่ 9 — แบบแสดงหลักฐานการมีส่วนร่วม")
    class DocumentNine {

        private Map<String, String> sample() {
            Map<String, String> d = new LinkedHashMap<>();
            d.put("title_name", "การจำแนกภาพด้วยการเรียนรู้เชิงลึก");
            d.put("chk_ firstauthor", "☑");
            d.put("chk_corresp", "☐");
            d.put("chk_coauthor", "☐");
            for (int i = 1; i <= 7; i++) {
                d.put("role_des" + i, "บทบาทข้อที่ " + "กขคงจฉช".charAt(i - 1));
            }
            d.put("applicant_name", "ผศ.ดร.สมชาย ใจดีวิชาการ");
            d.put("firstauthor_name", "ผศ.ดร.สมชาย ใจดีวิชาการ");
            d.put("corres_name", "รศ.ดร.สมหญิง ใจงาม");
            return d;
        }

        @Test
        @DisplayName("ทุกช่องขึ้นบนเอกสาร และไม่มี placeholder หลงเหลือ")
        void everyFieldLands() throws IOException {
            Map<String, String> data = sample();
            String doc = render(9, data);

            assertThat(doc).doesNotContain("{{").doesNotContain("}}");
            assertThat(doc)
                    .contains("กลุ่มที่ 1 งานวิจัย")
                    .contains("ก. ชื่อผลงาน การจำแนกภาพด้วยการเรียนรู้เชิงลึก")
                    .contains("ผู้นิพนธ์ร่วม (co-author)")
                    .contains("(ผศ.ดร.สมชาย ใจดีวิชาการ)")
                    .contains("(รศ.ดร.สมหญิง ใจงาม)");
            for (int i = 1; i <= 7; i++) {
                assertThat(doc).contains("บทบาทข้อที่ " + "กขคงจฉช".charAt(i - 1));
            }
        }

        @Test
        @DisplayName("ไม่มีกลุ่ม ๒–๓ และส่วนที่ ๒ ที่แบบ พ.ศ. 2569 ตัดออกแล้ว")
        void retiredSectionsAreGone() throws IOException {
            assertThat(render(9, sample()))
                    .doesNotContain("กลุ่มที่ ๒").doesNotContain("กลุ่มที่ ๓")
                    .doesNotContain("ส่วนที่ ๒")
                    .doesNotContain("essentially intellectual contributor");
        }

        @Test
        @DisplayName("ผู้ร่วมงานได้ช่องลงนามของตัวเอง ใช้คำตามแบบใหม่")
        void coauthorsGetTheirOwnSignatureBlock() throws IOException {
            Map<String, String> data = sample();
            data.put("coauthor_count", "1");
            data.put("coauthor_name_1", "อ.ดร.สมศักดิ์ รักเรียน");

            String doc = render(9, data);

            assertThat(doc)
                    .contains("(อ.ดร.สมศักดิ์ รักเรียน)")
                    .contains("ผู้นิพนธ์ร่วม (Co-author)");
            assertThat(count(doc, "ผู้ประพันธ์บรรณกิจ (Corresponding author)"))
                    .as("บล็อกของผู้ประพันธ์บรรณกิจต้องเหลือหนึ่งเดียว ไม่ถูกโคลนซ้ำ")
                    .isEqualTo(1);
        }
    }
}
