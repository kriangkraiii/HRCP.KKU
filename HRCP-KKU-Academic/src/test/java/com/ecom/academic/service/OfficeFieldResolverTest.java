package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;

/**
 * เลขที่หนังสือกับวันที่เอกสารเติมตอน render
 *
 * <p>เอกสารที่ลงนามครบแล้ว render จาก {@code frozenJson} ไม่ใช่จากแถวเอกสารปัจจุบัน
 * เลขที่หนังสือที่สารบรรณมากรอกทีหลังจึงไม่มีทางขึ้นเองได้ ต้องเติมทับตอน render —
 * เส้นทางเดียวกับที่ {@link SignerNameResolver} ใช้เติมชื่อผู้ลงนาม โดยไม่แตะ
 * {@code frozenJson} หรือแฮชของมัน
 *
 * <p>ต่างจาก {@code SignerNameResolver} ตรงที่ต้อง <em>เขียนทับ</em> ค่าเดิม ไม่ใช่เติมเฉพาะ
 * ตอนที่ว่าง เพราะซองมักถูกแช่แข็งตอนที่มีเลขเก่าหรือค่าตั้งต้นอยู่แล้ว
 */
@DisplayName("เลขที่หนังสือและวันที่บนเอกสารที่ลงนามแล้ว")
class OfficeFieldResolverTest {

    private AcademicRequestService academicService;
    private PositionRequestService positionService;
    private OfficeFieldResolver resolver;

    private SignatureRequest envelope;

    @BeforeEach
    void setUp() {
        academicService = mock(AcademicRequestService.class);
        positionService = mock(PositionRequestService.class);
        resolver = new OfficeFieldResolver(academicService, positionService);

        envelope = new SignatureRequest();
        envelope.setId(10L);
        envelope.setModule(SignatureModule.ACADEMIC);
        envelope.setRequestId(7L);
        envelope.setDocumentType(1);
        envelope.setStatus(SignatureRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("ซองที่ยังไม่ปิดรอบไม่ถูกเติม — ภาพของรอบที่ล้มเหลวต้องตรงกับตอนนั้น")
    void onlyTouchesCompletedEnvelopes() {
        when(academicService.getLatestDocumentData(7L, 1))
                .thenReturn(Map.of("memo_no", "อว 660301.26.8/13"));
        String frozen = "{\"memo_no\":\"อว 660301.26.8/\"}";

        for (SignatureRequestStatus status : SignatureRequestStatus.values()) {
            if (status == SignatureRequestStatus.COMPLETED) {
                continue;
            }
            envelope.setStatus(status);
            assertThat(resolver.fillInto(envelope, frozen))
                    .as("ซองสถานะ %s", status)
                    .isEqualTo(frozen);
        }
    }

    @Test
    @DisplayName("เลขที่หนังสือที่กรอกทีหลังต้องทับค่าที่แช่แข็งไว้")
    void overwritesTheFrozenMemoNumber() {
        when(academicService.getLatestDocumentData(7L, 1))
                .thenReturn(Map.of("memo_no", "อว 660301.26.8/13", "applicant_name", "ชื่อที่แก้ทีหลัง"));

        String json = resolver.fillInto(envelope,
                "{\"memo_no\":\"อว 660301.26.8/\",\"applicant_name\":\"สมชาย ใจดีวิชาการ\"}");

        assertThat(json).contains("อว 660301.26.8/13");
        // ช่องอื่นต้องเป็นฉบับที่ลงนามไว้เสมอ ไม่ใช่ค่าปัจจุบัน
        assertThat(json).contains("สมชาย ใจดีวิชาการ").doesNotContain("ชื่อที่แก้ทีหลัง");
    }

    @Test
    @DisplayName("วันที่เอกสารก็ทับได้เช่นกัน")
    void overwritesTheDocumentDate() {
        when(academicService.getLatestDocumentData(7L, 1))
                .thenReturn(Map.of("date", "๒๑ กันยายน ๒๕๖๙"));

        String json = resolver.fillInto(envelope, "{\"date\":\"๑ มกราคม ๒๕๖๙\"}");

        assertThat(json).contains("๒๑ กันยายน ๒๕๖๙").doesNotContain("๑ มกราคม ๒๕๖๙");
    }

    @Test
    @DisplayName("ค่าว่างไม่ลบเลขที่ลงนามไว้ทิ้ง")
    void blankValuesDoNotEraseWhatWasSigned() {
        when(academicService.getLatestDocumentData(7L, 1))
                .thenReturn(Map.of("memo_no", "  ", "date", ""));

        String json = resolver.fillInto(envelope, "{\"memo_no\":\"อว 1/2569\",\"date\":\"๑ มกราคม\"}");

        assertThat(json).contains("อว 1/2569").contains("๑ มกราคม");
    }

    @Test
    @DisplayName("เอกสารที่ไม่มีช่องสารบรรณ ไม่ถูกแตะเลย")
    void leavesDocumentsWithoutOfficeFieldsAlone() {
        envelope.setDocumentType(2);
        when(academicService.getLatestDocumentData(7L, 2))
                .thenReturn(Map.of("memo_no", "ไม่ควรถูกใช้", "hr_staff_name", "ชื่อใหม่"));

        String original = "{\"hr_staff_name\":\"ชื่อที่ลงนามไว้\"}";

        assertThat(resolver.fillInto(envelope, original)).isEqualTo(original);
    }

    @Test
    @DisplayName("เฟส 2 อ่านจากบริการของเฟส 2")
    void readsPhaseTwoFromThePositionService() {
        envelope.setModule(SignatureModule.POSITION);
        envelope.setDocumentType(6);
        when(positionService.getLatestDocumentData(7L, 6))
                .thenReturn(Map.of("memo_no", "อว 660301.26.3/99"));

        String json = resolver.fillInto(envelope, "{\"memo_no\":\"อว 660301.26.3/\"}");

        assertThat(json).contains("อว 660301.26.3/99");
    }

    @Test
    @DisplayName("ไม่มีแถวเอกสาร หรือ JSON พัง ก็ต้องคืนของเดิม ไม่โยน exception")
    void survivesMissingRowsAndBrokenJson() {
        when(academicService.getLatestDocumentData(7L, 1)).thenReturn(null);
        assertThat(resolver.fillInto(envelope, "{\"memo_no\":\"อว 1/2569\"}"))
                .isEqualTo("{\"memo_no\":\"อว 1/2569\"}");

        when(academicService.getLatestDocumentData(7L, 1)).thenReturn(Map.of("memo_no", "อว 9/2569"));
        assertThat(resolver.fillInto(envelope, "ไม่ใช่ JSON")).isEqualTo("ไม่ใช่ JSON");
        assertThat(resolver.fillInto(null, "{}")).isEqualTo("{}");
        assertThat(resolver.fillInto(envelope, null)).isNull();
    }
}
