package com.ecom.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.ecom.academic.service.CommitteeDocumentMailer;
import com.ecom.academic.service.ExternalSignerService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.SignInService;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * กรรมการที่ยังไม่ได้เข้าระบบ กดปุ่มในอีเมล → เข้าสู่ระบบ → ต้องไปถึงหน้าที่ปุ่มชี้ ไม่ใช่หน้าแรก
 *
 * <p>รวมกรณีที่เจ้าหน้าที่เพิ่มคนของ มข. ต่างคณะเป็น "บุคคลภายนอก" ด้วยอีเมล {@code @kku.ac.th}:
 * บัญชีเป็น ROLE_EXTERNAL และเข้าด้วย KKU SSO ตามปกติ
 *
 * <p>ตัว controller ของ SSO ประกอบเองจาก bean จริงทั้งหมด ยกเว้นตัวคุยกับ KKU SSO — การ mock bean
 * ใน context ที่ใช้ร่วมกันจะทำให้ต้องบูต context ใหม่ทั้งชุด
 */
@DisplayName("กดปุ่มในอีเมลตอนยังไม่เข้าระบบ → เข้าระบบแล้วกลับไปหน้าที่ปุ่มชี้")
class ExternalSignerSsoReturnTest extends AbstractFlowTest {

    /** ขึ้นต้นด้วย "test" — ระบบถือเป็นบัญชีทดสอบ ไม่ส่งหนังสือเชิญออกไปจริง แต่ยังเป็นโดเมน kku.ac.th */
    private static final String KKU_EMAIL = "test.wipa.eng@kku.ac.th";

    @Autowired
    private ExternalSignerService externalSigners;

    @Autowired
    private SsoAccessPolicy accessPolicy;

    @Autowired
    private SsoUserProvisioner provisioner;

    @Autowired
    private SignInService signInService;

    @Autowired
    private UserRepository users;

    private final KkuSsoClient client = mock(KkuSsoClient.class);
    private SsoAuthController sso;
    private UserDtls guest;
    private String filesLink;

    @BeforeEach
    void anInvitedOutsiderWithAKkuAddress() {
        KkuSsoProperties props = new KkuSsoProperties();
        props.setRedirectLoginUrl("https://hrd.computing.kku.ac.th/signin");
        sso = new SsoAuthController(props, client, accessPolicy, provisioner, signInService);

        guest = externalSigners.invite(new ExternalSignerService.Invite("รศ.ดร.", "วิภา", "ต่างคณะ",
                KKU_EMAIL, "คณะวิศวกรรมศาสตร์ มหาวิทยาลัยขอนแก่น"), data.admin());
        filesLink = CommitteeDocumentMailer.filesPath(42L);

        when(client.exchangeCode(anyString(), anyString())).thenReturn(Optional.of(
                new KkuSsoClient.SsoToken("sso-access-token", KKU_EMAIL, "immutable-1", "วิภา", "ต่างคณะ", null)));
        when(client.fetchProfile(anyString())).thenReturn(Optional.empty());
    }

    /** KKU SSO พากลับมาที่ callback ในเบราว์เซอร์เดิม (session เดิม) */
    private String ssoReturnsTo(MockHttpSession browser) {
        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/signin");
        callback.setSession(browser);
        return sso.loginCallback("one-time-code", null, null, callback, new MockHttpServletResponse(),
                new RedirectAttributesModelMap());
    }

    @Test
    @DisplayName("บุคคลภายนอกที่ใช้อีเมล @kku.ac.th — เข้าด้วย KKU SSO ได้ บัญชีเดิม สิทธิ์ภายนอกเหมือนเดิม")
    void aKkuAddressInvitedAsExternalSignsInThroughSso() {
        assertThat(guest.isExternal()).isTrue();
        assertThat(accessPolicy.evaluate(KKU_EMAIL).allowed()).isTrue();

        String landing = ssoReturnsTo(new MockHttpSession());

        assertThat(landing).isEqualTo("redirect:/esign/inbox");
        UserDtls after = users.findByEmailIgnoreCase(KKU_EMAIL);
        assertThat(after.getId()).as("ใช้บัญชีที่เชิญไว้ ไม่สร้างบัญชีซ้ำ").isEqualTo(guest.getId());
        assertThat(after.isExternal()).as("SSO ไม่เปลี่ยนบทบาทที่ระบบกำหนดไว้").isTrue();
    }

    @Test
    @DisplayName("กรรมการกดลิงก์หน้าเอกสารในอีเมล → เข้าด้วย SSO → ไปหน้าเอกสาร ไม่ใช่หน้าแรก")
    void theCommitteeMemberLandsOnTheFilesPageAfterSso() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        String toSignIn = mvc.perform(get(filesLink).session(browser)).andReturn().getResponse().getRedirectedUrl();
        assertThat(toSignIn).startsWith("/signin");

        assertThat(ssoReturnsTo(browser)).isEqualTo("redirect:" + filesLink);
    }

    /**
     * session อยู่ในหน่วยความจำ ทุกครั้งที่ deploy หรือ session หมดอายุ เบราว์เซอร์ยังถือ cookie ตัวเก่าอยู่
     * คำขอแบบนั้นถูก session management ตีกลับก่อนถึงขั้นจำหน้าปลายทาง — กรณีที่พบบ่อยที่สุดของคนที่
     * กดปุ่มในอีเมล เพราะเคยเข้าระบบมาก่อนแล้ว
     */
    @Test
    @DisplayName("เบราว์เซอร์ถือ cookie ของ session ที่ตายแล้ว — เข้าระบบแล้วยังต้องกลับไปหน้าที่ปุ่มชี้")
    void aDeadSessionCookieDoesNotLoseTheLink() throws Exception {
        UserDtls member = data.user("member@" + TestDataFactory.DOMAIN, "อรุณี", "กรรมการ", "ROLE_USER");

        MvcResult opened = mvc.perform(get(filesLink).with(request -> {
            request.setRequestedSessionId("HRCPSID-from-before-the-last-restart");
            request.setRequestedSessionIdValid(false);
            return request;
        })).andReturn();
        assertThat(opened.getResponse().getRedirectedUrl()).startsWith("/signin");
        MockHttpSession browser = (MockHttpSession) opened.getRequest().getSession(false);
        assertThat(browser).as("ต้องเปิด session ใหม่ไว้จำหน้าปลายทาง").isNotNull();

        String afterSignIn = mvc.perform(post("/login").session(browser).with(csrf())
                .param("email", member.getEmail()).param("password", TestDataFactory.PASSWORD))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(afterSignIn).isEqualTo(filesLink);
    }
}
