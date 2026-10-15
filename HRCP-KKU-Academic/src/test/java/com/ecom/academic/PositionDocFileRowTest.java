package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * หน้าคำร้องเฟส 2 ของเจ้าหน้าที่: ปุ่มดาวน์โหลด DOCX/PDF ของเอกสารที่เสร็จแล้วอยู่แถวย่อยใต้การ์ด แบบเดียวกับเฟส 1
 * — การ์ดเหลือแค่ดูตัวอย่างและแก้ไข
 */
@DisplayName("หน้าคำร้องเฟส 2: แถวไฟล์ DOCX/PDF ใต้การ์ดเอกสาร เหมือนเฟส 1")
class PositionDocFileRowTest extends AbstractFlowTest {

    @Test
    @DisplayName("เอกสารที่ลงนามครบ: DOCX/PDF อยู่แถวย่อย ไม่อยู่ในปุ่มของการ์ด")
    void downloadsSitInTheFileRow() throws Exception {
        UserDtls applicant = data.applicant();
        UserDtls officer = data.admin();
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_VERIFICATION, null);
        String json = "{\"date\":\"๑ กันยายน ๒๕๖๙\",\"name1\":\"ศ.ดร.ผู้ทรงคุณวุฒิ ทดสอบ\"}";
        data.positionDocument(request, 8, json);
        signEveryStep(circulate(SignatureModule.POSITION, request.getId(), 8, json, officer,
                List.of(new SignerAssignment("hr", officer.getId()))), officer);

        String html = mvc.perform(get("/admin/position/request/" + request.getId())
                .with(user(officer.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Document page = Jsoup.parse(html);
        String docx = "a[href$=/document/8/download]";

        assertThat(page.select("#docListRegion .doc-grid-sub " + docx)).hasSize(1);
        assertThat(page.select("#docListRegion .doc-grid-sub .btn-pdf-print[data-doc-type=8]")).hasSize(1);
        assertThat(page.select("#docListRegion .doc-grid-item-actions " + docx)).isEmpty();
    }
}
