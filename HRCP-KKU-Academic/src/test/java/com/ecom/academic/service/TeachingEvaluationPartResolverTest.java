package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ส่วนที่ ๓ ของแบบ ก.พ.ว. มข. ๐๓ ดึงมาจากผลประเมินการสอนของ Phase 1
 */
@DisplayName("ส่วนที่ ๓ แบบประเมินผลการสอน — ดึงจากผลประเมินที่คำร้องผูกไว้")
class TeachingEvaluationPartResolverTest {

    private static final long EVALUATION_ID = 7L;

    private AcademicRequestService academicService;
    private PositionRequestService positionService;
    private SignatureRequestRepository envelopeRepository;
    private SignatureStepRepository stepRepository;
    private SignerNameResolver signerNameResolver;
    private TeachingEvaluationPartResolver resolver;

    private PositionRequest request;
    private Map<String, String> doc8;

    @BeforeEach
    void setUp() {
        academicService = mock(AcademicRequestService.class);
        positionService = mock(PositionRequestService.class);
        envelopeRepository = mock(SignatureRequestRepository.class);
        stepRepository = mock(SignatureStepRepository.class);
        signerNameResolver = mock(SignerNameResolver.class);
        resolver = new TeachingEvaluationPartResolver(academicService, positionService,
                envelopeRepository, stepRepository, signerNameResolver);

        AcademicRequest evaluation = new AcademicRequest();
        evaluation.setId(EVALUATION_ID);
        request = new PositionRequest();
        request.setId(3L);
        request.setLinkedEvaluation(evaluation);

        doc8 = new HashMap<>();
        doc8.put("meeting_no", "3/2568");
        doc8.put("meeting_date", "15 มกราคม 2569");
        doc8.put("course_code", "CP353001");
        doc8.put("course_name", "โครงสร้างข้อมูล");
        doc8.put("final_level_1", " ");
        doc8.put("final_level_2", "✓");
        doc8.put("final_level_3", " ");
        doc8.put("committee_president_name", "ศ.ดร.ประธาน อนุกรรมการ");
        doc8.put("sign_date", "20 มกราคม 2569");
        when(academicService.getLatestDocumentData(EVALUATION_ID, 8)).thenReturn(doc8);
        when(academicService.summarize(any())).thenReturn(summary("ชำนาญพิเศษ"));
        when(envelopeRepository.findBlockingEnvelopes(eq(SignatureModule.ACADEMIC), anyLong(), eq(8)))
                .thenReturn(List.of());
    }

    private static EvaluationSummary summary(String resultLevel) {
        return new EvaluationSummary(EVALUATION_ID, "EV-1", "CP353001", "โครงสร้างข้อมูล", "2568",
                "1/2568", resultLevel, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("ทุกช่องมาจากเอกสารที่ 8 ของ Phase 1 และระดับที่ติ๊กไว้ แปลว่าอยู่ในเกณฑ์")
    void everyFieldComesFromTheEvaluation() {
        Map<String, String> fields = resolver.partThreeFields(request);

        assertThat(fields)
                .containsEntry("s3_meeting_no", "3/2568")
                .containsEntry("s3_meeting_date", "15 มกราคม 2569")
                .containsEntry("s3_university", "มหาวิทยาลัยขอนแก่น")
                .containsEntry("s3_course_code", "CP353001")
                .containsEntry("s3_course_name", "โครงสร้างข้อมูล")
                .containsEntry("s3_level", "ชำนาญพิเศษ")
                .containsEntry("s3_quality", "อยู่")
                .containsEntry("s3_chair_name", "ศ.ดร.ประธาน อนุกรรมการ")
                .containsEntry("s3_sign_date", "20 มกราคม 2569");
    }

    @Test
    @DisplayName("ผลประเมินไม่ผ่าน คุณภาพ \"ไม่อยู่\" ในหลักเกณฑ์ และไม่พิมพ์ระดับ")
    void aFailedEvaluationIsNotInTheCriteria() {
        doc8.put("final_level_2", " ");
        when(academicService.summarize(any())).thenReturn(summary("ไม่ผ่าน"));

        Map<String, String> fields = resolver.partThreeFields(request);

        assertThat(fields).containsEntry("s3_quality", "ไม่อยู่").doesNotContainKey("s3_level");
    }

    @Test
    @DisplayName("คำร้องที่ไม่ได้ผูกผลประเมินไว้ ไม่มีอะไรให้เติม — แบบฟอร์มขึ้นจุดไข่ปลาเอง")
    void nothingWithoutALinkedEvaluation() {
        request.setLinkedEvaluation(null);

        assertThat(resolver.partThreeFields(request)).isEmpty();
    }

    @Test
    @DisplayName("ชื่อและวันที่ของประธานที่เอกสารไม่ได้ระบุ ใช้จากขั้นลงนามของประธานแทน")
    void chairFallsBackToTheSignedStep() {
        doc8.remove("committee_president_name");
        doc8.remove("sign_date");
        SignatureRequest envelope = new SignatureRequest();
        envelope.setId(55L);
        when(envelopeRepository.findBlockingEnvelopes(SignatureModule.ACADEMIC, EVALUATION_ID, 8))
                .thenReturn(List.of(envelope));
        when(signerNameResolver.namesForEnvelope(envelope))
                .thenReturn(Map.of("committee_president_name", "รศ.ดร.ผู้ลงนาม จริง"));
        SignatureStep chair = new SignatureStep();
        chair.setSlotKey("committee_chair");
        chair.setStatus(SignatureStepStatus.SIGNED);
        chair.setSignedAt(LocalDateTime.of(2026, 1, 20, 10, 0));
        when(stepRepository.findSignedSteps(55L)).thenReturn(List.of(chair));

        Map<String, String> fields = resolver.partThreeFields(request);

        assertThat(fields)
                .containsEntry("s3_chair_name", "รศ.ดร.ผู้ลงนาม จริง")
                .containsEntry("s3_sign_date", "20 มกราคม 2569");
    }

    @Test
    @DisplayName("เติมเฉพาะเอกสารที่ 1 และไม่ทับค่าที่มีอยู่แล้ว")
    void fillsOnlyDocumentOneAndOnlyBlanks() throws Exception {
        String json = "{\"applicant_name\":\"สมชาย\",\"s3_meeting_no\":\"9/2569\"}";

        assertThat(resolver.fillInto(request, 2, json)).isEqualTo(json);

        @SuppressWarnings("unchecked")
        Map<String, String> filled = new ObjectMapper().readValue(resolver.fillInto(request, 1, json), Map.class);
        assertThat(filled)
                .containsEntry("applicant_name", "สมชาย")
                .containsEntry("s3_meeting_no", "9/2569")
                .containsEntry("s3_course_name", "โครงสร้างข้อมูล");
    }

    @Test
    @DisplayName("ซองลงนามของเอกสารที่ 1 เติมส่วนที่ ๓ ได้ ซองของเอกสารอื่นไม่ถูกแตะ")
    void envelopesOfOtherDocumentsAreLeftAlone() {
        when(positionService.findById(3L)).thenReturn(Optional.of(request));
        String json = "{\"applicant_name\":\"สมชาย\"}";

        SignatureRequest other = new SignatureRequest();
        other.setModule(SignatureModule.POSITION);
        other.setDocumentType(4);
        other.setRequestId(3L);
        assertThat(resolver.fillInto(other, json)).isEqualTo(json);

        SignatureRequest full = new SignatureRequest();
        full.setModule(SignatureModule.POSITION);
        full.setDocumentType(1);
        full.setRequestId(3L);
        assertThat(resolver.fillInto(full, json)).contains("s3_course_name");
    }
}
