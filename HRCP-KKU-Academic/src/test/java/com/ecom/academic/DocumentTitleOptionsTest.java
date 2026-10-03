package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * คำนำหน้าในเอกสารที่ 1 และ 2 ของการประเมินการสอนใช้แค่ตำแหน่งวิชาการ ไม่มี ดร. ต่อท้าย
 *
 * <p>คำนำหน้าจากทะเบียนบุคลากรมักมี ดร. ติดมา (เช่น ผศ.ดร.) ฟอร์มต้องเลือกตำแหน่งเปล่าให้เอง
 */
@DisplayName("คำนำหน้าในเอกสารประเมินการสอน — เฉพาะตำแหน่ง ไม่มี ดร.")
class DocumentTitleOptionsTest extends AbstractFlowTest {

    @ParameterizedTest(name = "{0} บน {1}")
    @CsvSource({
            "ผศ.ดร., document-1, ผู้ช่วยศาสตราจารย์",
            "ผศ.ดร., document-2, ผู้ช่วยศาสตราจารย์",
            "ผู้ช่วยศาสตราจารย์ ดร., document-2, ผู้ช่วยศาสตราจารย์",
            "อ.ดร., document-2, อาจารย์",
            "ดร., document-2, อาจารย์"
    })
    void theTitleIsTheRankWithoutTheDoctorate(String hrTitle, String page, String expected) throws Exception {
        UserDtls applicant = data.applicant();
        applicant.setTitle(hrTitle);
        data.saveUser(applicant);
        AcademicRequest draft = data.evaluation(applicant, RequestStatus.DRAFT);

        String html = mvc.perform(get("/user/academic/request/" + draft.getId() + "/" + page)
                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document doc = Jsoup.parse(html);
        Element select = doc.selectFirst("select[name=title]");
        if (select != null) {
            assertThat(select.outerHtml()).doesNotContain("ดร.");
            Element selectedOption = select.selectFirst("option[selected]");
            assertThat(selectedOption).as("ต้องมีตัวเลือกที่ถูกเลือก").isNotNull();
            assertThat(selectedOption.val()).isEqualTo(expected);
        } else {
            Element input = doc.selectFirst("input[name=title]");
            assertThat(input).as("ไม่พบช่องคำนำหน้า (ทั้ง select และ input)").isNotNull();
            assertThat(input.val()).doesNotContain("ดร.");
            assertThat(input.val()).isEqualTo(expected);
        }
    }
}
