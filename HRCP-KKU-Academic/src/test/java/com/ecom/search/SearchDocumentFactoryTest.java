package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.search.index.JsonFormTextExtractor;
import com.ecom.search.index.SearchDocumentFactory;
import com.ecom.search.model.ExtractionState;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchVisibility;

/**
 * What each source row turns into.
 *
 * <p>Four decisions are made per row — searchable text, who may see it, where it
 * links, how it is labelled — and three of them are only visible here. The
 * scoping tests prove the query honours {@code visibility}; this proves the
 * right value was written in the first place.
 *
 * <p>A plain unit test: the factory takes entities and returns an object, with
 * no database in between.
 */
class SearchDocumentFactoryTest {

    private final SearchDocumentFactory factory =
            new SearchDocumentFactory(new JsonFormTextExtractor());

    private UserDtls applicant() {
        UserDtls u = new UserDtls();
        u.setId(42);
        u.setFirstName("สมชาย");
        u.setLastName("ใจดี");
        u.setFirstNameEn("Somchai");
        u.setLastNameEn("Jaidee");
        u.setEmail("somchai@example.invalid");
        u.setRole("ROLE_USER");
        return u;
    }

    private AcademicRequest request(UserDtls owner) {
        AcademicRequest r = new AcademicRequest();
        r.setId(7L);
        r.setRequestCode("KKU-ACAD-256801-0001");
        r.setApplicant(owner);
        r.setCurrentStatus(RequestStatus.RECEIVED);
        return r;
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("คำร้องถูกผูกกับเจ้าของ และเก็บชื่อไว้ครบทุกคอลัมน์")
    void requestCarriesOwnerAndEveryName() {
        SearchDocument d = factory.fromAcademicRequest(request(applicant()));

        assertThat(d.getVisibility()).isEqualTo(SearchVisibility.OWNER_OR_ADMIN);
        assertThat(d.getOwnerUserId()).isEqualTo(42);
        assertThat(d.getStatus()).isEqualTo("RECEIVED");
        assertThat(d.getKeywords())
                .as("บั๊กต้นเรื่องคือค้นชื่อจากคอลัมน์เดียว ที่นี่ต้องมีทั้งไทยและอังกฤษ")
                .contains("สมชาย").contains("ใจดี")
                .contains("Somchai").contains("Jaidee")
                .contains("somchai@example.invalid");
        assertThat(d.getUrl()).isNotBlank();
    }

    @Test
    @DisplayName("คำร้องที่ไม่มีผู้ยื่นเหลืออยู่ ยังค้นได้แต่เฉพาะแอดมิน")
    void orphanedRequestBecomesAdminOnly() {
        SearchDocument d = factory.fromAcademicRequest(request(null));

        assertThat(d.getVisibility())
                .as("คำร้องกำพร้าคือสิ่งที่แอดมินต้องหาเจอ ไม่ใช่สิ่งที่ควรหายไปจาก index")
                .isEqualTo(SearchVisibility.ADMIN);
        assertThat(d.getOwnerUserId()).isNull();
        assertThat(d.getUrl()).as("คอลัมน์ url เป็น NOT NULL").isNotBlank();
    }

    @Test
    @DisplayName("เนื้อหาฟอร์มใน json_data ถูกดึงออกมาเป็นข้อความค้นได้")
    void formAnswersBecomeSearchableBody() {
        AcademicDocument doc = new AcademicDocument();
        doc.setId(11L);
        doc.setRequest(request(applicant()));
        doc.setDocumentType(6);
        doc.setDocumentLabel("แบบฟอร์มประเมินการสอน");
        doc.setJsonData("""
                {"course_name":"การเรียนรู้ของเครื่อง","course_code":"SC361001",
                 "committee":[{"name":"รศ.ดร. มาลี ตั้งใจ"}],
                 "is_draft":false,"score":1}
                """);

        SearchDocument d = factory.fromAcademicDocument(doc);

        assertThat(d.getBody())
                .as("คลังข้อความที่ใหญ่ที่สุดในระบบ และของเดิมไม่เคยค้นเลย")
                .contains("การเรียนรู้ของเครื่อง")
                .contains("SC361001")
                .contains("มาลี ตั้งใจ");
        assertThat(d.getBody())
                .as("ชื่อฟิลด์ไม่ใช่คำที่คนค้น และจะทำให้ trigram index สกปรก")
                .doesNotContain("course_name")
                .doesNotContain("is_draft");
        assertThat(d.getVisibility()).isEqualTo(SearchVisibility.OWNER_OR_ADMIN);
        assertThat(d.getOwnerUserId()).isEqualTo(42);
    }

    /**
     * The link-attachment guard.
     *
     * <p>{@code stored_file_path} holds 2048 characters of whatever an applicant
     * typed when attaching a link (V20). It becomes the {@code href} an
     * administrator clicks from the results, so anything but a real web address
     * has to be refused before it is ever written.
     */
    @Nested
    @DisplayName("ลิงก์ที่ผู้ยื่นแนบเอง")
    class LinkAttachments {

        private AcademicAttachment link(String url) {
            AcademicAttachment a = new AcademicAttachment();
            a.setId(3L);
            a.setRequest(request(applicant()));
            a.setOriginalFilename("เอกสารประกอบ");
            a.setFileType("LINK");
            a.setStoredFilePath(url);
            return a;
        }

        @Test
        @DisplayName("ลิงก์ https ปกติใช้เป็นปลายทางได้ และเปิดแท็บใหม่")
        void httpsLinkIsUsed() {
            SearchDocument d = factory.fromAcademicAttachment(link("https://drive.google.com/file/x"));

            assertThat(d.getUrl()).isEqualTo("https://drive.google.com/file/x");
            assertThat(d.isExternal()).isTrue();
            assertThat(d.getExtractionState())
                    .as("ลิงก์ไม่มีไฟล์บนดิสก์ให้สกัด")
                    .isEqualTo(ExtractionState.NONE);
        }

        @Test
        @DisplayName("javascript: ต้องไม่กลายเป็นปลายทาง")
        void javascriptSchemeIsRefused() {
            SearchDocument d = factory.fromAcademicAttachment(link("javascript:alert(1)"));

            assertThat(d.getUrl())
                    .as("ถ้าค่านี้หลุดไปเป็น href แอดมินที่กดผลค้นหาจะรันสคริปต์ของผู้ยื่น")
                    .doesNotStartWith("javascript:");
            assertThat(d.isExternal()).isFalse();
            assertThat(d.getTitle())
                    .as("แถวยังต้องอยู่ — ชื่อไฟล์ยังมีค่าในการค้น เพียงแต่พาไปหน้าคำร้องแทน")
                    .isEqualTo("เอกสารประกอบ");
        }

        @Test
        @DisplayName("สคีมอื่นและ URL ที่แอบซ่อน https ไว้กลางสตริงก็ต้องไม่ผ่าน")
        void otherSchemesAreRefused() {
            assertThat(factory.fromAcademicAttachment(link("data:text/html;base64,PHNjcmlwdD4="))
                    .isExternal()).isFalse();
            assertThat(factory.fromAcademicAttachment(link("file:///etc/passwd"))
                    .isExternal()).isFalse();
            assertThat(factory.fromAcademicAttachment(link("javascript:x//https://ok.example"))
                    .isExternal())
                    .as("การตรวจต้องยึดที่ต้นสตริง ไม่ใช่แค่ 'มี https อยู่ที่ไหนสักแห่ง'")
                    .isFalse();
        }

        @Test
        @DisplayName("ไฟล์ที่อัปโหลดจริงเข้าคิวรอสกัดข้อความ")
        void uploadedFileIsQueuedForExtraction() {
            AcademicAttachment a = new AcademicAttachment();
            a.setId(4L);
            a.setRequest(request(applicant()));
            a.setOriginalFilename("หลักฐาน.pdf");
            a.setFileType("pdf");
            a.setStoredFilePath("uploads/academic/7/attachments/หลักฐาน.pdf");

            SearchDocument d = factory.fromAcademicAttachment(a);

            assertThat(d.getExtractionState()).isEqualTo(ExtractionState.PENDING);
            assertThat(d.getFilePath()).endsWith("หลักฐาน.pdf");
            assertThat(d.isExternal()).isFalse();
        }
    }
}
