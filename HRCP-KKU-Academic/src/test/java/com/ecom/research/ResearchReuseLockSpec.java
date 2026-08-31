package com.ecom.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.external.model.ScopusPublication;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * "งานวิจัยที่ยื่นไปแล้ว ใช้ยื่นซ้ำไม่ได้" — the rule, written down as tests.
 *
 * <p><b>The rule, as agreed:</b> a publication used in a position request that
 * has left {@code DRAFT} is spent — it must not appear in the picker again and
 * must not be accepted on another request. The single exception is
 * {@code REJECTED}: a request that was refused never consumed anything, so its
 * publications come back.
 *
 * <p><b>Why almost everything here is disabled.</b> The rule cannot be enforced
 * today, and not because a check is missing — because the fact it would check is
 * never recorded. {@code ScopusPicker.applySelection()} asks the server for a
 * formatted citation string and writes that string into a plain text input;
 * {@code publication.id} is dropped in the browser and never travels back. So no
 * row anywhere ties a position request to a publication. That is GAP-11, and it
 * has to be built before GAP-12 can be.
 *
 * <p>{@link LinkageEvidence} proves the linkage really is absent, and will start
 * failing the moment it is added — which is the signal to enable the rest of
 * this class.
 *
 * <p>These tests are written against the picker's own HTTP API rather than any
 * new interface, so they constrain behaviour and not implementation: whoever
 * builds the link table is free to choose its shape.
 */
@DisplayName("งานวิจัย: กติกาห้ามใช้ผลงานซ้ำ (GAP-11/GAP-12)")
class ResearchReuseLockSpec extends AbstractFlowTest {

    private static final long FS_ID = 7001L;

    @Nested
    @DisplayName("หลักฐานว่ายังไม่มีการผูกผลงานกับคำร้อง")
    class LinkageEvidence {

        /**
         * Nothing in the position module references a publication id. When the
         * link table lands this test fails, and the failure message says what to
         * do next.
         */
        @Test
        @DisplayName("GAP-11: ไม่มีโค้ดใดในโมดูลคำร้องตำแหน่งอ้างถึง publication id เลย")
        void positionModuleNeverReferencesAPublicationId() throws IOException {
            Path module = Path.of("src/main/java/com/ecom/academic");
            List<Path> referencing;
            try (Stream<Path> files = Files.walk(module)) {
                referencing = files
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(ResearchReuseLockSpec::mentionsAPublicationId)
                        .toList();
            }

            assertThat(referencing)
                    .as("""
                            ยังไม่มีการเก็บว่าคำร้องไหนใช้ผลงานชิ้นใด กติกา 'ยื่นซ้ำไม่ได้' จึงบังคับไม่ได้
                            เมื่อสร้างตารางเชื่อม (เช่น position_request_publication) แล้ว เทสข้อนี้จะ fail
                            ให้ลบเทสข้อนี้ทิ้ง แล้วเปิด @Disabled ที่เหลือในคลาสนี้""")
                    .isEmpty();
        }

        /**
         * The browser-side half of the same gap: the picker sends ids up to ask
         * for citations, then keeps nothing. Pinned here so that a future change
         * which starts submitting ids is noticed.
         */
        @Test
        @DisplayName("GAP-11: picker ส่ง citation เป็นข้อความล้วนลงฟอร์ม ไม่เก็บ id ไว้เลย")
        void pickerWritesOnlyFreeText() throws IOException {
            String picker = Files.readString(
                    Path.of("src/main/resources/static/js/scopus_picker.js"), StandardCharsets.UTF_8);

            assertThat(picker)
                    .as("ค่าที่ใส่ลงฟอร์มคือ row.citation ซึ่งเป็นข้อความ")
                    .contains("setValue(target.titlePrefix + '_' + n, row.citation)");
            assertThat(picker)
                    .as("ไม่มีการเขียน id ลง hidden input ใด ๆ")
                    .doesNotContain("type=\"hidden\"");
        }
    }

    @Nested
    @Disabled("GAP-11: ต้องสร้างการผูกผลงานกับคำร้องก่อน จึงจะบังคับกติกานี้ได้")
    @DisplayName("กติกาที่ต้องเป็นจริงเมื่อ implement แล้ว")
    class TheRule {

        @Test
        @DisplayName("ยังเป็นแบบร่าง — ผลงานยังใช้ได้ตามปกติ")
        void draftDoesNotConsumeAPublication() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "ผลงานชิ้นที่หนึ่ง", 2023, 5);
            PositionRequest draft =
                    data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
            data.recordPublicationUse(draft, paper);

            expectPickerOffers(applicant, 1);
        }

        @Test
        @DisplayName("ส่งคำร้องแล้ว — ผลงานนั้นหายจาก picker ทันที")
        void submittingConsumesThePublication() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "ผลงานชิ้นที่หนึ่ง", 2023, 5);
            data.publication(FS_ID, "ผลงานชิ้นที่สอง", 2024, 2);
            PositionRequest submitted = data.positionRequest(
                    applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.recordPublicationUse(submitted, paper);

            expectPickerOffers(applicant, 1);
            mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                    .andExpect(jsonPath("$.data[0].title").value("ผลงานชิ้นที่สอง"));
        }

        @Test
        @DisplayName("ล็อกตลอดทุกสถานะหลังส่ง จนถึงส่งออกกองทรัพยากรบุคคล")
        void lockHoldsThroughEveryPostDraftStatus() throws Exception {
            for (PositionRequestStatus status : PositionRequestStatus.values()) {
                if (status == PositionRequestStatus.DRAFT
                        || status == PositionRequestStatus.REJECTED) {
                    continue;
                }
                data.reset();
                UserDtls applicant = linkedApplicant();
                ScopusPublication paper = data.publication(FS_ID, "ผลงานที่ใช้ไปแล้ว", 2023, 5);
                data.recordPublicationUse(data.positionRequest(applicant, status, null), paper);

                expectPickerOffers(applicant, 0);
            }
        }

        /**
         * The one release valve. A refused request consumed nothing, and its
         * owner has to be able to put the same work forward again — otherwise a
         * single rejection would permanently retire their publications.
         */
        @Test
        @DisplayName("คำร้องถูกปฏิเสธ — ผลงานกลับมาใช้ได้")
        void rejectionReleasesThePublication() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "ผลงานที่เคยยื่นแล้วถูกปฏิเสธ", 2023, 5);
            data.recordPublicationUse(
                    data.positionRequest(applicant, PositionRequestStatus.REJECTED, null), paper);

            expectPickerOffers(applicant, 1);
        }

        @Test
        @DisplayName("การล็อกไม่ข้ามไปหาอาจารย์ท่านอื่น")
        void lockIsPerOwnerNotGlobal() throws Exception {
            UserDtls somchai = linkedApplicant();
            UserDtls malee = data.otherApplicant();
            data.faculty(7002L, TestDataFactory.OTHER_APPLICANT_EMAIL);

            ScopusPublication somchaisPaper = data.publication(FS_ID, "ของสมชาย", 2023, 5);
            data.publication(7002L, "ของมาลี", 2023, 5);
            data.recordPublicationUse(
                    data.positionRequest(somchai, PositionRequestStatus.SENT_TO_HR, null),
                    somchaisPaper);

            expectPickerOffers(somchai, 0);
            expectPickerOffers(malee, 1);
        }

        @Test
        @DisplayName("ยิง citation ของผลงานที่ใช้ไปแล้วตรง ๆ — ต้องถูกปฏิเสธ ไม่ใช่แค่ซ่อนใน UI")
        void spentPublicationIsRefusedAtTheApiToo() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "ผลงานที่ใช้ไปแล้ว", 2023, 5);
            data.recordPublicationUse(
                    data.positionRequest(applicant, PositionRequestStatus.SCREENING_COMMITTEE, null),
                    paper);

            mvc.perform(get("/api/my/publications/citations")
                    .param("ids", String.valueOf(paper.getId()))
                    .with(user(applicant.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        private UserDtls linkedApplicant() {
            UserDtls applicant = data.applicant();
            data.faculty(FS_ID, TestDataFactory.APPLICANT_EMAIL);
            return applicant;
        }

        private void expectPickerOffers(UserDtls applicant, int expected) throws Exception {
            mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(expected));
        }
    }

    private static boolean mentionsAPublicationId(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            return source.contains("publicationId")
                    || source.contains("publication_id")
                    || source.contains("ScopusPublication");
        } catch (IOException e) {
            return false;
        }
    }
}
