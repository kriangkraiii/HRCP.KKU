package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;

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

        String select = titleSelect(html);
        assertThat(select).doesNotContain("ดร.");
        assertThat(select).containsPattern("<option value=\"" + expected + "\"[^>]*selected");
    }

    private static String titleSelect(String html) {
        Matcher m = Pattern.compile("<select name=\"title\".*?</select>", Pattern.DOTALL).matcher(html);
        assertThat(m.find()).as("ไม่พบช่องคำนำหน้า").isTrue();
        return m.group();
    }
}
