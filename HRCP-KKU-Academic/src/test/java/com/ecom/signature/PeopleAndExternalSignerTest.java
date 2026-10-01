package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.SignInService;
import com.ecom.sso.SsoAccessPolicy;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ตัวค้นหาชื่อผู้ลงนาม (/api/people) และผู้ลงนามภายนอก มข. (ROLE_EXTERNAL)
 * ดู docs/PLAN-signer-picker.md และ docs/PLAN-external-signer.md
 */
@DisplayName("รายชื่อผู้ลงนาม และบัญชีผู้ลงนามภายนอก")
class PeopleAndExternalSignerTest extends AbstractFlowTest {

    @Autowired
    private StaffMemberRepository staffMembers;

    @Autowired
    private UserRepository users;

    @Autowired
    private SignInService signInService;

    @Autowired
    private SsoAccessPolicy ssoAccessPolicy;

    private final ObjectMapper json = new ObjectMapper();

    private UserDtls officer;
    private UserDtls applicant;
    private UserDtls dean;
    private UserDtls lecturer;

    @BeforeEach
    void people() {
        officer = data.admin();
        applicant = data.applicant();
        dean = data.user("dean@" + TestDataFactory.DOMAIN, "ประสิทธิ์", "คณบดี", "ROLE_USER");
        lecturer = data.user("lecturer@" + TestDataFactory.DOMAIN, "ประสาน", "อาจารย์", "ROLE_USER");
        StaffMember s = new StaffMember();
        s.setFirstName(dean.getFirstName());
        s.setLastName(dean.getLastName());
        s.setStaffRole("DEAN");
        s.setIsActive(true);
        s.setUser(dean);
        staffMembers.save(s);
        dean.setPositionTitle("คณบดีวิทยาลัยการคอมพิวเตอร์");
        users.save(dean);
    }

    @AfterEach
    void removeStaff() {
        staffMembers.deleteAll();
    }

    private static RequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private List<Map<String, Object>> search(String q, String role, UserDtls who) throws Exception {
        var req = get("/api/people").param("q", q).with(as(who));
        if (role != null) {
            req.param("role", role);
        }
        String body = mvc.perform(req).andReturn().getResponse().getContentAsString();
        return json.readValue(body, new TypeReference<>() {
        });
    }

    @Test
    @DisplayName("ค้นหาได้ทุกบัญชีในระบบ — ผู้ที่มีบทบาทตรงขึ้นก่อน พร้อมตำแหน่งที่บันทึกไว้")
    void everyAccountIsSearchableAndTheRoleComesFirst() throws Exception {
        var found = search("ประส", "DEAN", officer);

        assertThat(found).extracting(p -> p.get("userId")).containsExactly(dean.getId(), lecturer.getId());
        assertThat(found.get(0)).containsEntry("recommended", true)
                .containsEntry("position", "คณบดีวิทยาลัยการคอมพิวเตอร์")
                .containsEntry("email", dean.getEmail());
    }

    @Test
    @DisplayName("ผู้ยื่นค้นหาได้ แต่อีเมลของคนอื่นถูกปิดบางส่วน")
    void applicantsSeeMaskedEmails() throws Exception {
        var found = search("ประสิทธิ์", null, applicant);

        assertThat(found).hasSize(1);
        assertThat((String) found.get(0).get("email")).startsWith("de***@").isNotEqualTo(dean.getEmail());
    }

    private String addExternal(UserDtls who, String email) throws Exception {
        return mvc.perform(post("/api/people/external").with(csrf()).with(as(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"รศ.ดร.\",\"firstName\":\"วิภา\",\"lastName\":\"ภายนอก\","
                                + "\"email\":\"" + email + "\",\"affiliation\":\"มหาวิทยาลัยเชียงใหม่\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("เพิ่มผู้ลงนามนอก มข. — ได้บัญชี ROLE_EXTERNAL พร้อมหน่วยงาน อีเมลเดิมไม่สร้างซ้ำ")
    void anExternalSignerGetsAnAccount() throws Exception {
        Map<String, Object> person = json.readValue(addExternal(applicant, "Wipa@Partner.example.invalid"), new TypeReference<>() {
        });

        UserDtls created = users.findByEmailIgnoreCase("wipa@partner.example.invalid");
        assertThat(created.getRole()).isEqualTo(UserDtls.ROLE_EXTERNAL);
        assertThat(created.getAffiliation()).isEqualTo("มหาวิทยาลัยเชียงใหม่");
        assertThat(person).containsEntry("external", true).containsEntry("userId", created.getId());

        addExternal(officer, "wipa@partner.example.invalid");
        assertThat(users.findAll()).filteredOn(u -> "wipa@partner.example.invalid".equalsIgnoreCase(u.getEmail())).hasSize(1);
        assertThat(addExternal(officer, "ไม่ใช่อีเมล")).contains("อีเมลไม่ถูกต้อง");
    }

    @Test
    @DisplayName("บัญชีภายนอก: เข้าด้วย KKU SSO ได้ ลงนามได้ แต่ไม่มีคำร้อง แดชบอร์ด และประวัติคำร้อง")
    void anExternalAccountOnlySigns() throws Exception {
        addExternal(officer, "guest@partner.example.invalid");
        UserDtls guest = users.findByEmailIgnoreCase("guest@partner.example.invalid");

        assertThat(ssoAccessPolicy.evaluate("guest@partner.example.invalid").allowed()).as("เข้าด้วย KKU SSO").isTrue();
        assertThat(signInService.landingPageFor(guest)).isEqualTo("/esign/inbox");

        for (String open : List.of("/esign/inbox", "/esign/my-signatures", "/user/profile", "/user/notifications")) {
            assertThat(mvc.perform(get(open).with(as(guest))).andReturn().getResponse().getStatus())
                    .as(open).isNotEqualTo(403);
        }
        for (String closed : List.of("/user/academic/dashboard", "/user/academic/history",
                "/user/academic/new-request", "/user/position/dashboard")) {
            assertThat(mvc.perform(get(closed).with(as(guest))).andReturn().getResponse().getStatus())
                    .as(closed).isEqualTo(403);
        }
        assertThat(mvc.perform(post("/api/people/external").with(csrf()).with(as(guest))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus()).as("บัญชีภายนอกเชิญคนอื่นไม่ได้").isEqualTo(403);
    }

    @Test
    @DisplayName("แอดมินเห็นรายชื่อผู้ลงนามภายนอก แก้ตำแหน่งที่พิมพ์ในเอกสารได้ และบันทึกแล้วบทบาทยังเป็นภายนอก")
    void adminsManageExternalSigners() throws Exception {
        addExternal(officer, "guest@partner.example.invalid");
        UserDtls guest = users.findByEmailIgnoreCase("guest@partner.example.invalid");

        String list = mvc.perform(get("/admin/users").param("type", "3").with(as(officer)))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("guest@partner.example.invalid").doesNotContain(applicant.getEmail());

        String edit = mvc.perform(get("/admin/edit-user").param("id", guest.getId().toString()).with(as(officer)))
                .andReturn().getResponse().getContentAsString();
        assertThat(edit).contains("value=\"ROLE_EXTERNAL\" selected");

        String redirect = mvc.perform(multipart("/admin/update-user")
                        .file(new MockMultipartFile("img", new byte[0]))
                        .param("id", guest.getId().toString()).param("type", "3")
                        .param("firstName", guest.getFirstName()).param("lastName", guest.getLastName())
                        .param("email", guest.getEmail()).param("role", UserDtls.ROLE_EXTERNAL)
                        .param("positionTitle", " ผู้ทรงคุณวุฒิภายนอก ").param("affiliation", "มหาวิทยาลัยเชียงใหม่")
                        .with(csrf()).with(as(officer)))
                .andReturn().getResponse().getRedirectedUrl();

        UserDtls saved = users.findById(guest.getId()).orElseThrow();
        assertThat(redirect).endsWith("type=3");
        assertThat(saved.getRole()).isEqualTo(UserDtls.ROLE_EXTERNAL);
        assertThat(saved.getPositionTitle()).isEqualTo("ผู้ทรงคุณวุฒิภายนอก");
        assertThat(saved.getApplicantId()).as("บัญชีภายนอกไม่ได้รหัสผู้ยื่น").isNull();
    }
}
