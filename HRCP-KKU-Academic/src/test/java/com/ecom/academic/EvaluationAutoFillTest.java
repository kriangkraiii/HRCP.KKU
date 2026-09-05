package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.DocumentDataAutoFillHelper;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ผลประเมินการสอนที่ผู้ยื่นเลือก ต้องเดินทางเข้าไปในคำร้องขอตำแหน่ง.
 *
 * <p>คำร้องขอตำแหน่งผูกกับผลประเมินผ่าน {@code linked_evaluation_id} มาตั้งแต่ต้น
 * แต่ตัวเติมฟอร์มไม่เคยอ่านมันเลย — ผู้ยื่นจึงต้องพิมพ์รหัสวิชา ชื่อวิชา และผล
 * การประเมินซ้ำอีกรอบ ทั้งที่ระบบถืออยู่แล้ว และพิมพ์ผิดได้โดยไม่มีอะไรทักท้วง
 */
@DisplayName("ดึงผลประเมินการสอนมาเติมคำร้องขอตำแหน่ง")
@org.springframework.transaction.annotation.Transactional
class EvaluationAutoFillTest extends AbstractFlowTest {

    // Transactional so the persistence context stays open across the whole test,
    // which is what the helper meets in production: these pages run under
    // open-in-view, so `linkedEvaluation` is a proxy the helper is expected to be
    // able to follow. Without it the test would be asserting against a detached
    // graph no real caller ever holds.

    @Autowired
    private DocumentDataAutoFillHelper autoFill;

    private Map<String, String> prefilledDoc1For(UserDtls applicant, AcademicRequest evaluation) {
        PositionRequest request = data.positionRequest(applicant,
                PositionRequestStatus.DRAFT, evaluation);
        return autoFill.getPreFilledPositionDocData(request, 1, null);
    }

    @Test
    @DisplayName("รายวิชาและปีการศึกษาจากผลประเมิน ถูกเติมให้พร้อมใช้")
    void theCourseTravelsIntoThePositionRequest() {
        UserDtls applicant = data.applicant();
        AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");

        assertThat(prefilledDoc1For(applicant, evaluation))
                .containsEntry("teaching_eval_course_code", "CP001101")
                .containsEntry("teaching_eval_course_name", "วิชาทดสอบ CP001101")
                .containsEntry("teaching_eval_academic_year", "2568");
    }

    @Test
    @DisplayName("ผลการประเมินและรหัสคำร้องประเมิน ติดมาด้วย")
    void theResultTravelsToo() {
        UserDtls applicant = data.applicant();
        AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");

        assertThat(prefilledDoc1For(applicant, evaluation))
                .containsEntry("teaching_eval_result_level", "ชำนาญ")
                .containsEntry("teaching_eval_request_code", evaluation.getRequestCode());
    }

    @Test
    @DisplayName("ตารางประสบการณ์การสอนแถวแรก ตั้งต้นจากวิชาที่ถูกประเมิน")
    void theTeachingExperienceTableStartsFromTheEvaluatedCourse() {
        UserDtls applicant = data.applicant();
        AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");

        assertThat(prefilledDoc1For(applicant, evaluation))
                .as("ผู้ยื่นไม่ควรต้องพิมพ์วิชาที่ตัวเองเพิ่งถูกประเมินซ้ำอีกครั้ง")
                .containsEntry("teaching_subject_1", "วิชาทดสอบ CP001101")
                .containsEntry("teaching_semester_1", "1/2568");
    }

    @Test
    @DisplayName("สิ่งที่ผู้ยื่นกรอกเองไว้แล้ว ต้องไม่ถูกทับ")
    void whatTheApplicantTypedWins() {
        UserDtls applicant = data.applicant();
        AcademicRequest evaluation = data.evaluationForCourse(applicant, "CP001101", "2568");
        PositionRequest request = data.positionRequest(applicant,
                PositionRequestStatus.DRAFT, evaluation);

        Map<String, String> filled = autoFill.getPreFilledPositionDocData(request, 1,
                "{\"teaching_subject_1\":\"วิชาที่ผู้ยื่นแก้เอง\"}");

        assertThat(filled)
                .as("ค่าที่บันทึกไว้แล้วมีสิทธิ์สูงสุดเสมอ ตามที่ตัวเติมฟอร์มทำอยู่กับทุกแหล่ง")
                .containsEntry("teaching_subject_1", "วิชาที่ผู้ยื่นแก้เอง");
    }

    @Test
    @DisplayName("คำร้องที่ไม่ได้ผูกผลประเมิน — ไม่ล้ม และไม่มีคีย์ผลประเมินโผล่มา")
    void arequestWithoutAnEvaluationIsLeftAlone() {
        PositionRequest request = data.positionRequest(data.applicant(),
                PositionRequestStatus.DRAFT, null);

        assertThat(autoFill.getPreFilledPositionDocData(request, 1, null))
                .doesNotContainKey("teaching_eval_course_code");
    }
}
