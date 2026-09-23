package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * เทมเพลตเฟส 2 ตามแบบฟอร์มข้อบังคับ พ.ศ. 2569 — เอกสารที่ 1 (แบบ ก.พ.ว. มข.๐๓) และ
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
        @DisplayName("ตัดส่วนที่ ๒–๕ ออก เหลือเฉพาะส่วนที่ผู้ยื่นกรอก")
        void onlyPartOneRemains() throws IOException {
            String doc = render(1, associateApplicant());

            assertThat(doc).contains("ส่วนที่ ๑");
            assertThat(doc)
                    .as("ส่วนที่ ๒ คือเอกสารที่ 5 อยู่แล้ว ส่วนที่ ๓–๕ กรรมการกับสภาเป็นผู้กรอก")
                    .doesNotContain("ส่วนที่ ๒").doesNotContain("ส่วนที่ ๓")
                    .doesNotContain("ส่วนที่ ๔").doesNotContain("ส่วนที่  ๕");
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
