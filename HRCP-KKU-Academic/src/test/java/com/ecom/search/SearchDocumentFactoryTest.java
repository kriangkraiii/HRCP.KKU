package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.AcademicRevisionFile;
import com.ecom.academic.model.AdminFile;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.PositionStatusHistory;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.external.model.KkuRegulationDoc;
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

    /**
     * Sources added after the first cut of the index, each one a gap someone
     * could fall into: a search that found nothing for a record that exists.
     */
    @Nested
    @DisplayName("แหล่งข้อมูลที่เพิ่มเข้า index ภายหลัง")
    class LaterSources {

        private PositionRequest positionRequest() {
            PositionRequest r = new PositionRequest();
            r.setId(11L);
            r.setRequestCode("KKU-POS-256801-0001");
            r.setApplicant(applicant());
            r.setCurrentStatus(PositionRequestStatus.DOCUMENT_RECEIVED);
            return r;
        }

        @Test
        @DisplayName("เอกสารลงนามลิงก์ไปหน้า /esign/inbox ที่มีอยู่จริง และค้นด้วยชื่อผู้ลงนามได้")
        void signatureRequestLinksToRealInboxAndCarriesSigners() {
            SignatureRequest envelope = new SignatureRequest();
            envelope.setId(5L);
            envelope.setDocumentLabel("บันทึกข้อความขอประเมิน");
            SignatureStep step = new SignatureStep();
            step.setSignatureRequest(envelope);
            step.setSignerNameSnapshot("รศ.ดร.มาลี ทองดี");
            step.setRoleLabel("คณบดี");
            envelope.getSteps().add(step);

            SearchDocument d = factory.fromSignatureRequest(envelope);

            assertThat(d.getUrl())
                    .as("/user/esign/inbox กับ /admin/esign/inbox ไม่มีอยู่จริง กดแล้วเจอ 404")
                    .isEqualTo("/esign/inbox");
            assertThat(d.getAdminUrl()).isEqualTo("/esign/inbox");
            assertThat(d.getKeywords()).contains("มาลี").contains("คณบดี");
        }

        @Test
        @DisplayName("ประวัติสถานะคำร้องตำแหน่งผูกกับผู้ยื่น และลิงก์ไปหน้าคำร้องนั้น")
        void positionStatusHistoryIsScopedAndLinked() {
            PositionStatusHistory h = new PositionStatusHistory();
            h.setId(3L);
            h.setRequest(positionRequest());
            h.setNewStatus(PositionRequestStatus.REVISION_REQUESTED);
            h.setNote("ขอให้แก้ไขรายชื่อผลงานในเอกสารฉบับที่ 2");

            SearchDocument d = factory.fromPositionStatusHistory(h);

            assertThat(d.getVisibility()).isEqualTo(SearchVisibility.OWNER_OR_ADMIN);
            assertThat(d.getOwnerUserId()).isEqualTo(42);
            assertThat(d.getUrl()).isEqualTo("/user/position/request/11");
            assertThat(d.getBody()).contains("รายชื่อผลงาน");
            assertThat(d.getKeywords()).contains("KKU-POS-256801-0001");
        }

        @Test
        @DisplayName("ไฟล์ฉบับแก้ไขเข้าคิวสกัดข้อความ ส่วนลิงก์ไม่เข้า")
        void revisionFileIsQueuedUnlessItIsALink() {
            AcademicRevisionFile f = new AcademicRevisionFile();
            f.setId(9L);
            f.setRequest(request(applicant()));
            f.setRound(2);
            f.setOriginalFilename("มคอ3-แก้ไข.pdf");
            f.setFileType("PDF");
            f.setStoredPath("uploads/academic/7/revisions/มคอ3-แก้ไข.pdf");

            SearchDocument file = factory.fromRevisionFile(f);
            assertThat(file.getExtractionState()).isEqualTo(ExtractionState.PENDING);
            assertThat(file.getSubtitle()).contains("แก้ไขรอบที่ 2");
            assertThat(file.getOwnerUserId()).isEqualTo(42);

            f.setFileType(AcademicRevisionFile.LINK);
            f.setStoredPath("https://drive.google.com/file/y");
            SearchDocument link = factory.fromRevisionFile(f);
            assertThat(link.getExtractionState()).isEqualTo(ExtractionState.NONE);
            assertThat(link.isExternal()).isTrue();
        }

        @Test
        @DisplayName("ชื่อผลงานที่แนบกับคำร้องตำแหน่งค้นเจอคำร้องนั้น")
        void positionRequestCarriesLinkedPublicationTitles() {
            SearchDocument d = factory.fromPositionRequest(positionRequest(),
                    List.of("Deep Learning for Thai OCR"));

            assertThat(d.getBody()).contains("Deep Learning for Thai OCR");
            assertThat(factory.fromPositionRequest(positionRequest()).getBody()).isNull();
        }

        @Test
        @DisplayName("ไฟล์ระบบและเอกสารข้อบังคับเข้าคิวสกัดเนื้อหา")
        void storedFilesAndRegulationsAreQueued() {
            AdminFile f = new AdminFile();
            f.setId(1L);
            f.setOriginalFilename("ประกาศ.pdf");
            f.setStoredFilePath("uploads/admin-storage/abc.pdf");
            SearchDocument stored = factory.fromAdminFile(f);
            assertThat(stored.getExtractionState()).isEqualTo(ExtractionState.PENDING);
            assertThat(stored.getFilePath()).isEqualTo("uploads/admin-storage/abc.pdf");

            KkuRegulationDoc doc = new KkuRegulationDoc();
            doc.setId(2L);
            doc.setTitle("ข้อบังคับ มข. ว่าด้วยตำแหน่งทางวิชาการ 2569");
            doc.setFileUrl("https://hr2.kku.ac.th/files/reg-2569.pdf");
            SearchDocument regulation = factory.fromRegulationDoc(doc);
            assertThat(regulation.getExtractionState()).isEqualTo(ExtractionState.PENDING);
            assertThat(regulation.getFilePath()).isEqualTo("https://hr2.kku.ac.th/files/reg-2569.pdf");
        }
    }
}
