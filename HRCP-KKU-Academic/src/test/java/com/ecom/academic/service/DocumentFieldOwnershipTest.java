package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureModule;

/**
 * ใครเขียนช่องไหนได้บ้าง — กติกาเดียวที่ใช้ร่วมกันทั้งสองเฟส.
 *
 * <p>เดิมฝั่งเซิร์ฟเวอร์รับค่าที่ส่งมาทั้งก้อนโดยไม่ดูว่าใครส่ง สิ่งที่กันไว้คือ
 * {@code <input type="hidden">} ที่เบราว์เซอร์ส่งค่ากลับมาเอง ซึ่งปิด JS หรือยิง POST ตรงก็ทะลุ
 * เทสต์ชุดนี้จึงยิงที่ระดับ map ล้วน ๆ ไม่ผ่านเทมเพลต เพื่อยืนยันว่าการกันอยู่ที่เซิร์ฟเวอร์จริง
 */
@DisplayName("ความเป็นเจ้าของช่องในเอกสาร")
class DocumentFieldOwnershipTest {

    private static final SignatureModule P1 = SignatureModule.ACADEMIC;
    private static final SignatureModule P2 = SignatureModule.POSITION;

    private static Map<String, String> map(String... pairs) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Nested
    @DisplayName("เอกสารฉบับไหนเป็นของใคร")
    class DocumentOwner {

        @Test
        @DisplayName("เฟส 1 ผู้ยื่นเป็นเจ้าของเอกสาร 1 และ 2 เท่านั้น")
        void phase1ApplicantOwnsOneAndTwo() {
            assertThat(DocumentFieldOwnership.isApplicantDocument(P1, 1)).isTrue();
            assertThat(DocumentFieldOwnership.isApplicantDocument(P1, 2)).isTrue();
            for (int type = 3; type <= 9; type++) {
                assertThat(DocumentFieldOwnership.isApplicantDocument(P1, type))
                        .as("เอกสารเฟส 1 ที่ %d ต้องเป็นของแอดมิน", type)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("เฟส 2 แอดมินเหลือแค่เอกสาร 7 กับ 8 — เอกสาร 5 ย้ายมาเป็นของผู้ยื่น")
        void phase2AdminKeepsOnlySevenAndEight() {
            for (int type : new int[] { 1, 2, 3, 4, 5, 6, 9 }) {
                assertThat(DocumentFieldOwnership.isApplicantDocument(P2, type))
                        .as("เอกสารเฟส 2 ที่ %d ต้องเป็นของผู้ยื่น", type)
                        .isTrue();
            }
            assertThat(DocumentFieldOwnership.isApplicantDocument(P2, 7)).isFalse();
            assertThat(DocumentFieldOwnership.isApplicantDocument(P2, 8)).isFalse();
        }

        @Test
        @DisplayName("เอกสารที่ไม่รู้จักถือเป็นของแอดมิน — ปลอดภัยไว้ก่อน")
        void unknownDocumentTypesAreAdminOwned() {
            assertThat(DocumentFieldOwnership.isApplicantDocument(P1, 99)).isFalse();
            assertThat(DocumentFieldOwnership.isApplicantDocument(P2, 0)).isFalse();
        }
    }

    @Nested
    @DisplayName("แอดมินบันทึกเอกสารของผู้ยื่น")
    class AdminSavingApplicantDocument {

        @Test
        @DisplayName("แก้ชื่อผู้ยื่นไม่ได้ ค่าเดิมถูกคืนกลับ")
        void cannotChangeApplicantFields() {
            Map<String, String> submitted = map("applicant_name", "แอดมินแอบแก้", "memo_no", "อว 1/2569");
            Map<String, String> existing = map("applicant_name", "ผู้ยื่นกรอกไว้", "memo_no", "");

            Map<String, String> result = DocumentFieldOwnership.merge(P1, 1, true, submitted, existing);

            assertThat(result).containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }

        @Test
        @DisplayName("กรอกเลขที่หนังสือได้ เพราะเป็นช่องของแอดมิน")
        void mayWriteTheMemoNumber() {
            Map<String, String> submitted = map("applicant_name", "แอดมินแอบแก้", "memo_no", "อว 1/2569");
            Map<String, String> existing = map("applicant_name", "ผู้ยื่นกรอกไว้", "memo_no", "");

            Map<String, String> result = DocumentFieldOwnership.merge(P1, 1, true, submitted, existing);

            assertThat(result).containsEntry("memo_no", "อว 1/2569");
        }

        @Test
        @DisplayName("คอลัมน์ 'เจ้าหน้าที่' ของเอกสารที่ 2 ยังกรอกได้ แต่ติ๊กของผู้ยื่นแตะไม่ได้")
        void mayFillTheOfficerColumnOfDocumentTwo() {
            Map<String, String> submitted = map(
                    "chk_off_1", "✓", "text_1", "ครบ",
                    "chk_app_1", "", "applicant_name", "แอดมินแอบแก้");
            Map<String, String> existing = map("chk_app_1", "✓", "applicant_name", "ผู้ยื่นกรอกไว้");

            Map<String, String> result = DocumentFieldOwnership.merge(P1, 2, true, submitted, existing);

            assertThat(result)
                    .containsEntry("chk_off_1", "✓")
                    .containsEntry("text_1", "ครบ")
                    .containsEntry("chk_app_1", "✓")
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }

        @Test
        @DisplayName("ช่องใหม่ที่ผู้ยื่นไม่เคยมี แอดมินยัดเพิ่มไม่ได้")
        void cannotSmuggleInBrandNewApplicantKeys() {
            Map<String, String> submitted = map("course_name", "วิชาที่แอดมินแต่งขึ้น");

            Map<String, String> result = DocumentFieldOwnership.merge(P1, 1, true, submitted, map());

            assertThat(result).doesNotContainKey("course_name");
        }

        @Test
        @DisplayName("เฟส 2 เอกสาร 1, 2, 9 แอดมินอ่านอย่างเดียว ไม่มีช่องของตัวเองเลย")
        void phase2DocumentsOneTwoNineAreFullyReadOnly() {
            for (int type : new int[] { 1, 2, 9 }) {
                assertThat(DocumentFieldOwnership.adminFields(P2, type))
                        .as("เอกสารเฟส 2 ที่ %d", type)
                        .isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("ผู้ยื่นบันทึกเอกสารของตัวเอง")
    class ApplicantSaving {

        @Test
        @DisplayName("แตะเลขที่หนังสือไม่ได้ ค่าเดิมของแอดมินถูกคืนกลับ")
        void cannotTouchTheMemoNumber() {
            Map<String, String> submitted = map("memo_no", "ผู้ยื่นแต่งเอง", "applicant_name", "ชื่อจริง");
            Map<String, String> existing = map("memo_no", "อว 1/2569", "applicant_name", "ชื่อเก่า");

            Map<String, String> result = DocumentFieldOwnership.merge(P1, 1, false, submitted, existing);

            assertThat(result)
                    .containsEntry("memo_no", "อว 1/2569")
                    .containsEntry("applicant_name", "ชื่อจริง");
        }

        @Test
        @DisplayName("ลบช่องของแอดมินด้วยการส่งค่าว่างก็ไม่ได้")
        void cannotEraseAdminFieldsBySubmittingBlanks() {
            Map<String, String> submitted = map("memo_no", "");
            Map<String, String> existing = map("memo_no", "อว 1/2569");

            Map<String, String> result = DocumentFieldOwnership.merge(P1, 1, false, submitted, existing);

            assertThat(result).containsEntry("memo_no", "อว 1/2569");
        }

        @Test
        @DisplayName("เฟส 2 เอกสาร 6 ทั้งเลขที่หนังสือและวันที่เป็นของเจ้าหน้าที่")
        void phase2DocumentSixKeepsTheMemoNumberAndDateWithTheOffice() {
            Map<String, String> submitted = map("memo_no", "ผู้ยื่นแต่งเอง", "date", "ผู้ยื่นลงวันเอง");
            Map<String, String> existing = map(
                    "memo_no", "อว 660301.26.3/45", "date", "๑๐ มีนาคม ๒๕๖๙");

            Map<String, String> result = DocumentFieldOwnership.merge(P2, 6, false, submitted, existing);

            assertThat(result)
                    .containsEntry("memo_no", "อว 660301.26.3/45")
                    .containsEntry("date", "๑๐ มีนาคม ๒๕๖๙");
        }

        @Test
        @DisplayName("เฟส 2 เอกสาร 3 ช่องคณบดีเป็นของแอดมิน แต่วันที่รับรองเป็นของผู้ยื่น")
        void phase2DocumentThreeSplitsDeanFieldsFromTheApplicants() {
            Map<String, String> submitted = map(
                    "certification_date", "๑๐ มีนาคม ๒๕๖๙",
                    "dean_name", "ผู้ยื่นแต่งเอง");
            Map<String, String> existing = map("dean_name", "คณบดีตัวจริง");

            Map<String, String> result = DocumentFieldOwnership.merge(P2, 3, false, submitted, existing);

            assertThat(result)
                    .containsEntry("certification_date", "๑๐ มีนาคม ๒๕๖๙")
                    .containsEntry("dean_name", "คณบดีตัวจริง");
        }
    }

    @Nested
    @DisplayName("ช่องที่เป็นของผู้ลงนาม")
    class SignerOwnedFields {

        @Test
        @DisplayName("ผลประเมินของเอกสาร 5 เฟส 2 มาจากทะเบียนช่องลงนาม ไม่ได้ตั้งรายการซ้ำ")
        void areDerivedFromTheSignatureRegistry() {
            assertThat(DocumentFieldOwnership.signerFields(P2, 5))
                    .containsExactlyInAnyOrder("qualification_status", "dean_qualification_status");
        }

        @Test
        @DisplayName("เอกสารที่ผู้ลงนามไม่ต้องตอบคำถาม ไม่มีช่องกลุ่มนี้")
        void areEmptyWhereNoSignerIsAskedAnything() {
            assertThat(DocumentFieldOwnership.signerFields(P2, 7)).isEmpty();
            assertThat(DocumentFieldOwnership.signerFields(P1, 1)).isEmpty();
        }

        @Test
        @DisplayName("ทั้งแอดมินและผู้ยื่นเขียนช่องผลประเมินไม่ได้")
        void areWritableByNeitherSide() {
            Map<String, String> forged = map("qualification_status", "ครบถ้วน", "major", "วิทยาการคอมพิวเตอร์");

            Map<String, String> byApplicant = DocumentFieldOwnership.merge(P2, 5, false, forged, map());
            Map<String, String> byAdmin = DocumentFieldOwnership.merge(P2, 5, true, forged, map());

            assertThat(byApplicant).doesNotContainKey("qualification_status");
            assertThat(byAdmin).doesNotContainKey("qualification_status");
            assertThat(byApplicant).containsEntry("major", "วิทยาการคอมพิวเตอร์");
        }

        @Test
        @DisplayName("ค่าที่ผู้ลงนามเคยบันทึกไว้ ไม่ถูกคืนกลับเข้าเอกสารตอนบันทึกฟอร์ม")
        void areNotRestoredFromExistingEither() {
            Map<String, String> existing = map("qualification_status", "ครบถ้วน");

            Map<String, String> result = DocumentFieldOwnership.merge(P2, 5, false, map(), existing);

            assertThat(result).doesNotContainKey("qualification_status");
        }
    }

    @Nested
    @DisplayName("เอกสารของแอดมินและกรณีขอบ")
    class AdminDocumentsAndEdgeCases {

        @Test
        @DisplayName("ในเอกสารของแอดมิน แอดมินเขียนได้ทุกช่อง")
        void adminOwnsEveryFieldOfAnAdminDocument() {
            Map<String, String> submitted = map("hr_officer_name", "เจ้าหน้าที่", "anything_at_all", "ค่า");

            Map<String, String> result = DocumentFieldOwnership.merge(P2, 7, true, submitted, map());

            assertThat(result)
                    .containsEntry("hr_officer_name", "เจ้าหน้าที่")
                    .containsEntry("anything_at_all", "ค่า");
        }

        @Test
        @DisplayName("ผู้ยื่นเขียนเอกสารของแอดมินไม่ได้แม้แต่ช่องเดียว")
        void applicantOwnsNothingInAnAdminDocument() {
            Map<String, String> submitted = map("hr_officer_name", "ผู้ยื่นแต่งเอง");
            Map<String, String> existing = map("hr_officer_name", "เจ้าหน้าที่ตัวจริง");

            Map<String, String> result = DocumentFieldOwnership.merge(P2, 7, false, submitted, existing);

            assertThat(result).containsEntry("hr_officer_name", "เจ้าหน้าที่ตัวจริง");
        }

        @Test
        @DisplayName("ยังไม่เคยบันทึก (existing เป็น null) ก็ไม่พัง")
        void toleratesAMissingExistingDocument() {
            Map<String, String> submitted = map("memo_no", "อว 1/2569", "applicant_name", "ชื่อ");

            Map<String, String> result = DocumentFieldOwnership.merge(P1, 1, true, submitted, null);

            assertThat(result).containsEntry("memo_no", "อว 1/2569");
            assertThat(result).doesNotContainKey("applicant_name");
        }

        @Test
        @DisplayName("ไม่แก้ map ที่รับเข้ามา — ผู้เรียกยังใช้ของเดิมต่อได้")
        void doesNotMutateItsArguments() {
            Map<String, String> submitted = map("applicant_name", "แอดมินแอบแก้");
            Map<String, String> existing = map("applicant_name", "ผู้ยื่นกรอกไว้");

            DocumentFieldOwnership.merge(P1, 1, true, submitted, existing);

            assertThat(submitted).containsEntry("applicant_name", "แอดมินแอบแก้");
            assertThat(existing).containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }
    }

    /**
     * เลขที่หนังสือกับวันที่เอกสารออกโดยสารบรรณ ซึ่งในทางปฏิบัติทำ <em>หลัง</em> เอกสารลงนาม
     * ครบแล้ว สองช่องนี้จึงต้องเขียนทับได้ตอนที่ช่องอื่นปิดตายไปแล้ว
     */
    @Nested
    @DisplayName("ช่องสารบรรณ")
    class OfficeFields {

        @Test
        @DisplayName("เฟส 1 เอกสาร 1, 5, 9 มีทั้งเลขที่หนังสือและวันที่")
        void phase1DocumentsWithAMemoHeader() {
            for (int type : new int[] { 1, 5, 9 }) {
                assertThat(DocumentFieldOwnership.officeFields(P1, type))
                        .as("เอกสารเฟส 1 ที่ %d", type)
                        .containsExactlyInAnyOrder("memo_no", "date");
            }
        }

        @Test
        @DisplayName("เฟส 2 เอกสาร 4 และ 6 มีทั้งสองช่อง ส่วนเอกสาร 8 มีแค่วันที่")
        void phase2DocumentsWithAMemoHeader() {
            assertThat(DocumentFieldOwnership.officeFields(P2, 4))
                    .containsExactlyInAnyOrder("memo_no", "date");
            assertThat(DocumentFieldOwnership.officeFields(P2, 6))
                    .containsExactlyInAnyOrder("memo_no", "date");
            assertThat(DocumentFieldOwnership.officeFields(P2, 8))
                    .containsExactly("date");
        }

        @Test
        @DisplayName("เอกสารที่ไม่มีหัวบันทึกข้อความคืนค่าว่าง")
        void documentsWithoutAMemoHeaderHaveNone() {
            for (int type : new int[] { 2, 3, 4, 6, 7, 8 }) {
                assertThat(DocumentFieldOwnership.officeFields(P1, type))
                        .as("เอกสารเฟส 1 ที่ %d", type)
                        .isEmpty();
            }
            for (int type : new int[] { 1, 2, 3, 5, 7, 9 }) {
                assertThat(DocumentFieldOwnership.officeFields(P2, type))
                        .as("เอกสารเฟส 2 ที่ %d", type)
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("ทุกช่องสารบรรณต้องเป็นช่องของแอดมินด้วย ไม่งั้นกรอกแล้วถูกตัดทิ้ง")
        void areAlwaysAdminOwnedOnApplicantDocuments() {
            for (int type : DocumentFieldOwnership.applicantDocuments(P1)) {
                assertThat(DocumentFieldOwnership.adminFields(P1, type))
                        .as("เอกสารเฟส 1 ที่ %d", type)
                        .containsAll(DocumentFieldOwnership.officeFields(P1, type));
            }
            for (int type : DocumentFieldOwnership.applicantDocuments(P2)) {
                assertThat(DocumentFieldOwnership.adminFields(P2, type))
                        .as("เอกสารเฟส 2 ที่ %d", type)
                        .containsAll(DocumentFieldOwnership.officeFields(P2, type));
            }
        }

        @Test
        @DisplayName("เฟส 1 เอกสาร 1 ผู้ยื่นลงวันที่เองไม่ได้แล้ว")
        void applicantCannotSetTheDocumentDate() {
            Map<String, String> submitted = map("date", "ผู้ยื่นลงวันเอง", "applicant_name", "ชื่อจริง");
            Map<String, String> existing = map("date", "๒๑ กันยายน ๒๕๖๙");

            Map<String, String> result = DocumentFieldOwnership.merge(P1, 1, false, submitted, existing);

            assertThat(result)
                    .containsEntry("date", "๒๑ กันยายน ๒๕๖๙")
                    .containsEntry("applicant_name", "ชื่อจริง");
        }
    }

    @Nested
    @DisplayName("mergeJson สำหรับ auto-draft")
    class JsonVariant {

        @Test
        @DisplayName("กรองช่องเหมือนกันเมื่อรับมาเป็น JSON")
        void filtersTheSameWayOnRawJson() {
            String submitted = "{\"applicant_name\":\"แอดมินแอบแก้\",\"memo_no\":\"อว 1/2569\"}";

            String result = DocumentFieldOwnership.mergeJson(P1, 1, true, submitted,
                    map("applicant_name", "ผู้ยื่นกรอกไว้"));

            assertThat(result).contains("ผู้ยื่นกรอกไว้").doesNotContain("แอดมินแอบแก้");
        }

        @Test
        @DisplayName("JSON ที่พังไม่ทำให้บันทึกล้ม แต่ต้องไม่ปล่อยของที่กรองไม่ได้ผ่านไป")
        void refusesPayloadsItCannotParse() {
            assertThat(DocumentFieldOwnership.mergeJson(P1, 1, false, "ไม่ใช่ JSON", map())).isNull();
            assertThat(DocumentFieldOwnership.mergeJson(P1, 1, false, null, map())).isNull();
        }
    }
}
