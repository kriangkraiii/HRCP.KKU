package com.ecom.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MvcResult;

import com.ecom.academic.model.ExternalSignInLink.Purpose;
import com.ecom.academic.repository.ExternalSignInLinkRepository;
import com.ecom.academic.service.ExternalSignerService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.RecordingMailSender;

/**
 * ผู้ลงนามจากนอก มข. เข้าระบบด้วยลิงก์ในอีเมล — production มีแค่ปุ่ม KKU SSO ซึ่งคนนอกใช้ไม่ได้
 * (docs/PLAN-external-magic-link.md)
 */
@DisplayName("ลิงก์เข้าสู่ระบบทางอีเมลของผู้ลงนามนอก มข.")
class ExternalLinkFlowTest extends AbstractFlowTest {

    @Autowired
    private ExternalLinkService links;

    @Autowired
    private ExternalSignInLinkRepository linkRows;

    @Autowired
    private ExternalSignerService externalSigners;

    @Autowired
    private UserRepository users;

    @AfterEach
    void realTime() {
        links.useClock(Clock.systemDefaultZone());
    }

    private UserDtls outsider(String local) {
        return externalSigners.invite(new ExternalSignerService.Invite("รศ.ดร.", "วิภา", "ลิงก์",
                local + "@partner.example.invalid", "มหาวิทยาลัยเชียงใหม่"), data.admin());
    }

    /** หนังสือเชิญตอนสร้างบัญชีก็ถึงอีเมลเดียวกัน — นับเฉพาะอีเมลลิงก์เข้าสู่ระบบ */
    private List<RecordingMailSender.Sent> linkMailsTo(String email) {
        return mail().to(email).stream().filter(m -> m.subjectContains("ลิงก์เข้าสู่ระบบ")).toList();
    }

    private static String tokenOf(String url) {
        return url.substring(url.indexOf(ExternalLinkService.PATH + "/") + ExternalLinkService.PATH.length() + 1);
    }

    @Test
    @DisplayName("ออกลิงก์แล้วใช้ได้ครั้งเดียว พาไปขั้นลงนามที่ระบุ — ฐานข้อมูลเก็บแค่ hash")
    void aLinkWorksOnceAndOnlyItsHashIsStored() {
        UserDtls guest = outsider("once");
        String url = links.issue(guest, 42L, Purpose.SIGN_REQUEST);
        String token = tokenOf(url);

        assertThat(url).startsWith("http").contains("/auth/link/");
        assertThat(linkRows.findAll()).noneMatch(row -> row.getTokenHash().contains(token));
        assertThat(linkRows.findByTokenHash(ExternalLinkService.hash(token))).isPresent();

        var first = links.redeem(token, "203.0.113.7");
        assertThat(first).isPresent();
        assertThat(first.get().user().getId()).isEqualTo(guest.getId());
        assertThat(first.get().stepId()).isEqualTo(42L);
        assertThat(linkRows.findByTokenHash(ExternalLinkService.hash(token)).orElseThrow().getUsedIp())
                .isEqualTo("203.0.113.7");

        assertThat(links.redeem(token, "203.0.113.7")).as("ใช้ซ้ำไม่ได้").isEmpty();
    }

    @Test
    @DisplayName("หมดอายุแล้ว หรือแก้ token แม้ตัวเดียว — ใช้ไม่ได้")
    void expiredOrAlteredLinksAreRefused() {
        UserDtls guest = outsider("expiry");
        String expiring = tokenOf(links.issue(guest, null, Purpose.REMINDER));
        String altered = tokenOf(links.issue(guest, null, Purpose.REMINDER));
        char last = altered.charAt(altered.length() - 1);

        assertThat(links.redeem(altered.substring(0, altered.length() - 1) + (last == 'A' ? 'B' : 'A'), null)).isEmpty();

        links.useClock(Clock.offset(Clock.systemDefaultZone(), Duration.ofDays(links.ttlDays()).plusMinutes(1)));
        assertThat(links.isLive(expiring)).isFalse();
        assertThat(links.redeem(expiring, null)).isEmpty();
    }

    @Test
    @DisplayName("บัญชีถูกปิด หรือเปลี่ยนเป็นบัญชีของ มข. หลังออกลิงก์ — ลิงก์เดิมใช้ไม่ได้")
    void theAccountIsCheckedAgainWhenTheLinkIsUsed() {
        UserDtls disabled = outsider("disabled");
        String forDisabled = tokenOf(links.issue(disabled, null, Purpose.SIGN_REQUEST));
        disabled.setIsEnable(false);
        users.save(disabled);
        assertThat(links.redeem(forDisabled, null)).isEmpty();

        UserDtls promoted = outsider("promoted");
        String forPromoted = tokenOf(links.issue(promoted, null, Purpose.SIGN_REQUEST));
        promoted.setRole("ROLE_USER");
        users.save(promoted);
        assertThat(links.redeem(forPromoted, null)).isEmpty();

        UserDtls movedToKku = outsider("moved");
        String forMoved = tokenOf(links.issue(movedToKku, null, Purpose.SIGN_REQUEST));
        movedToKku.setEmail("moved.link@kku.ac.th");
        users.save(movedToKku);
        assertThat(links.redeem(forMoved, null)).isEmpty();
    }

    @Test
    @DisplayName("ออกลิงก์ให้คน มข. ไม่ได้ — ทั้งผู้ใช้ทั่วไป และบุคลากรต่างคณะที่เป็นผู้ลงนามภายนอก")
    void kkuAccountsNeverGetALink() {
        UserDtls kkuGuest = externalSigners.invite(new ExternalSignerService.Invite("ผศ.", "สมศรี", "ต่างคณะ",
                "test.somsri.link@kku.ac.th", "คณะวิศวกรรมศาสตร์ มข."), data.admin());

        assertThat(kkuGuest.isExternal()).isTrue();
        assertThatThrownBy(() -> links.issue(kkuGuest, null, Purpose.SIGN_REQUEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> links.issue(data.applicant(), null, Purpose.SIGN_REQUEST))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("เปิดลิงก์ (GET) ไม่ใช้ token — กดปุ่ม (POST) แล้วเข้าระบบ ไปหน้าลงนาม session สั้น และเปิดหน้าของผู้ยื่นไม่ได้")
    void openingTheLinkDoesNotSpendItPressingTheButtonSignsIn() throws Exception {
        UserDtls guest = outsider("button");
        String token = tokenOf(links.issue(guest, 77L, Purpose.SIGN_REQUEST));

        // ระบบสแกนอีเมลของหน่วยงานเปิดลิงก์ก่อน — ต้องยังใช้ได้
        for (int i = 0; i < 2; i++) {
            String page = mvc.perform(get("/auth/link/" + token)).andReturn().getResponse().getContentAsString();
            assertThat(page).contains("linkSignInForm").doesNotContain("linkExpired");
        }

        MvcResult signedIn = mvc.perform(post("/auth/link/" + token).with(csrf())).andReturn();
        assertThat(signedIn.getResponse().getRedirectedUrl()).isEqualTo("/esign/sign/77");
        MockHttpSession session = (MockHttpSession) signedIn.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(30 * 60);
        SecurityContext context = (SecurityContext) session
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getName()).isEqualTo(guest.getEmail());
        assertThat(context.getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly(UserDtls.ROLE_EXTERNAL);

        assertThat(mvc.perform(get("/user/academic/dashboard").session(session)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mvc.perform(get("/esign/inbox").session(session)).andReturn().getResponse().getStatus())
                .isEqualTo(200);

        String reused = mvc.perform(post("/auth/link/" + token).with(csrf())).andReturn().getResponse()
                .getContentAsString();
        assertThat(reused).contains("linkExpired").contains("/auth/link");
    }

    @Test
    @DisplayName("POST ที่ไม่มี CSRF token ไม่ใช้ลิงก์")
    void aPostWithoutCsrfIsRefused() throws Exception {
        String token = tokenOf(links.issue(outsider("csrf"), null, Purpose.SIGN_REQUEST));

        MvcResult refused = mvc.perform(post("/auth/link/" + token)).andReturn();

        assertThat(refused.getResponse().getRedirectedUrl()).doesNotStartWith("/esign");
        assertThat(links.isLive(token)).as("ลิงก์ยังไม่ถูกใช้").isTrue();
    }

    @Test
    @DisplayName("ขอลิงก์ใหม่: เขียวเฉพาะอีเมลผู้ลงนามภายนอกที่มีในระบบ — อีเมลอื่นบอกว่าเพราะอะไร และไม่ส่งซ้ำในช่วงรอ")
    void askingForANewLinkChecksTheEmailAgainstTheSystem() throws Exception {
        UserDtls guest = outsider("resend");
        UserDtls closed = outsider("closed");
        closed.setIsEnable(false);
        users.save(closed);

        assertThat(requestLink(" " + guest.getEmail().toUpperCase() + " "))
                .contains("data-outcome=\"SENT\"").contains("alert-success").contains(guest.getEmail().toUpperCase());
        assertThat(requestLink(guest.getEmail())).contains("data-outcome=\"RECENTLY_SENT\"");
        assertThat(requestLink("nobody@partner.example.invalid"))
                .contains("data-outcome=\"NOT_FOUND\"").contains("alert-danger").doesNotContain("alert-success")
                .contains("value=\"nobody@partner.example.invalid\"");
        assertThat(requestLink("someone@kku.ac.th")).contains("data-outcome=\"KKU_ACCOUNT\"").contains("KKU SSO");
        assertThat(requestLink(data.applicant().getEmail())).contains("data-outcome=\"NOT_EXTERNAL\"");
        assertThat(requestLink(closed.getEmail())).contains("data-outcome=\"DISABLED\"").contains("alert-danger");

        awaitCondition("ลิงก์ใหม่ถึงผู้ลงนาม", () -> !linkMailsTo(guest.getEmail()).isEmpty());
        settle();
        assertThat(linkMailsTo(guest.getEmail())).as("ขอซ้ำในช่วงรอต้องไม่ส่งฉบับที่สอง").hasSize(1);
        assertThat(linkMailsTo(guest.getEmail()).get(0).body()).contains("/auth/link/");
        assertThat(linkMailsTo(closed.getEmail())).isEmpty();
        assertThat(mail().to("nobody@partner.example.invalid")).isEmpty();
    }

    private String requestLink(String email) throws Exception {
        return mvc.perform(post("/auth/link").with(csrf()).param("email", email))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("หน้าเข้าสู่ระบบมีทางเข้าสำรองของผู้ลงนามนอก มข. และหน้าลิงก์เปิดได้โดยไม่ต้องเข้าระบบ")
    void theSignInPageOffersTheEmailLink() throws Exception {
        String signin = mvc.perform(get("/signin")).andReturn().getResponse().getContentAsString();
        assertThat(signin).contains("emailLinkEntryBtn").contains("href=\"/auth/link\"");

        String request = mvc.perform(get("/auth/link")).andReturn().getResponse().getContentAsString();
        assertThat(request).contains("linkRequestForm").contains("kkuUsesSso");

        String unknownToken = mvc.perform(get("/auth/link/not-a-real-token")).andReturn().getResponse()
                .getContentAsString();
        assertThat(unknownToken).contains("linkExpired");
    }
}
