package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;

@DisplayName("ชื่อในวงเล็บใต้ลายเซ็น — ผู้ลงนามเติมเองตอนเซ็นถ้ายังว่าง")
class SignerNamePrintingTest {

    private static final SignatureSlot HR = new SignatureSlot("hr", "นักทรัพยากรบุคคล", "hr_staff_name", "HR", 2);

    private static SignatureStep signedBy(String name) {
        SignatureStep step = new SignatureStep();
        step.setSignerNameSnapshot(name);
        return step;
    }

    @Test
    @DisplayName("เอกสารที่ 2: ช่องชื่อนักทรัพยากรบุคคลว่าง — ใส่ชื่อผู้ตรวจที่ลงนาม")
    void blankNameIsFilledWithTheSigner() {
        Map<String, String> own = new LinkedHashMap<>();

        IncrementalSigningService.putNameIfBlank(own, HR, signedBy("สมหญิง สายตรวจการ"),
                Set.of("sig_hr", "hr_staff_name"), Map.of("hr_staff_name", ""));

        assertThat(own).containsEntry("hr_staff_name", "สมหญิง สายตรวจการ");
    }

    @Test
    @DisplayName("ชื่อที่เจ้าหน้าที่กรอกไว้แล้ว หรือไฟล์ไม่ได้เผื่อช่องชื่อ — ไม่แตะ")
    void filledOrMissingFieldIsLeftAlone() {
        Map<String, String> own = new LinkedHashMap<>();

        IncrementalSigningService.putNameIfBlank(own, HR, signedBy("สมหญิง สายตรวจการ"),
                Set.of("sig_hr", "hr_staff_name"), Map.of("hr_staff_name", "นายสมชาย กรอกเอง"));
        IncrementalSigningService.putNameIfBlank(own, HR, signedBy("สมหญิง สายตรวจการ"),
                Set.of("sig_hr"), Map.of());

        assertThat(own).isEmpty();
    }
}
