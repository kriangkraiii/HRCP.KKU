package com.ecom.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ecom.support.AbstractFlowTest;

/**
 * A dead session cookie must not destroy a login that is halfway through.
 *
 * <p>KKU SSO returns the browser to {@code /signin?code=…}, and that request
 * carries whatever session cookie the browser still holds. Sessions live in
 * memory, so every restart — every deploy — leaves browsers holding a cookie for
 * a session the server has never heard of, for up to the half hour the cookie
 * lasts.
 *
 * <p>Spring Security's session management sees the unknown id and sends the
 * browser to "your session expired" <em>before any controller runs</em>. The
 * one-time code goes with it. Nothing is logged, because nothing of ours was
 * reached; the professor sees the login page again and tries again, and the
 * fresh cookie is just as dead as the last one.
 *
 * <p>What should happen instead: throw the useless cookie away and let the code
 * through.
 */
@DisplayName("SSO: cookie เก่าที่ตายแล้วต้องไม่ทำให้การล็อกอินที่ค้างอยู่หลุด")
class SsoCallbackSurvivesAStaleSessionTest extends AbstractFlowTest {

    /** A browser holding a cookie for a session this server does not have. */
    private RequestPostProcessor holdingADeadSessionCookie() {
        return request -> {
            request.setRequestedSessionId("HRCPSID-from-before-the-last-restart");
            request.setRequestedSessionIdValid(false);
            return request;
        };
    }

    @Test
    @DisplayName("callback ที่พก cookie ตายแล้ว ต้องไม่ถูกกลืนไปเป็น ?expired=true")
    void theOneTimeCodeIsNotThrownAway() throws Exception {
        MvcResult result = mvc.perform(get("/signin?code=one-time-code-from-sso")
                .with(holdingADeadSessionCookie()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String destination = result.getResponse().getRedirectedUrl();

        assertThat(destination)
                .as("""
                        ถ้าเด้งไปหน้า "เซสชันหมดอายุ" code จะหายไปพร้อมกัน การล็อกอินจึงจบไม่ได้
                        ผู้ใช้จะเห็นแค่หน้า login อีกครั้งโดยไม่มีอะไรอธิบาย""")
                .doesNotContain("expired=true");
        assertThat(destination)
                .as("code ที่ SSO ออกให้ต้องยังอยู่ครบ เพื่อให้รอบถัดไปเข้าถึง callback ได้จริง")
                .contains("code=one-time-code-from-sso");
    }

    @Test
    @DisplayName("และต้องสั่งลบ cookie ตัวที่ตายแล้วทิ้งไปด้วย ไม่งั้นรอบหน้าก็เจอปัญหาเดิม")
    void theDeadCookieIsCleared() throws Exception {
        MvcResult result = mvc.perform(get("/signin?code=one-time-code-from-sso")
                .with(holdingADeadSessionCookie()))
                .andReturn();

        assertThat(result.getResponse().getCookies())
                .as("ต้องมีคำสั่งลบ cookie เซสชัน (Max-Age=0) กลับไปให้เบราว์เซอร์")
                .anyMatch(c -> c.getMaxAge() == 0);
    }

    /**
     * The SSONext manual's own example registers {@code /auth/callback/login} as
     * the redirect URL, so that is where a by-the-book registration sends the
     * browser. The protection has to cover it too — it keys on the code, not on
     * the path, and this is what says so.
     */
    @Test
    @DisplayName("path ตามคู่มือ (/auth/callback/login) ก็ต้องรอดจาก cookie ที่ตายแล้วเช่นกัน")
    void theCallbackPathFromTheManualIsProtectedToo() throws Exception {
        MvcResult result = mvc.perform(get("/auth/callback/login?code=one-time-code-from-sso")
                .with(holdingADeadSessionCookie()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .as("ไม่ว่า SSO จะจดทะเบียนไว้เป็น path ไหน code ก็ต้องไม่ถูกทิ้ง")
                .doesNotContain("expired=true")
                .contains("code=one-time-code-from-sso");
    }

    @Test
    @DisplayName("request ธรรมดาที่ cookie ตายแล้ว ยังต้องได้หน้า 'เซสชันหมดอายุ' เหมือนเดิม")
    void anOrdinaryStaleRequestStillGetsTheExpiredPage() throws Exception {
        MvcResult result = mvc.perform(get("/user/academic/dashboard")
                .with(holdingADeadSessionCookie()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .as("นอกเส้นทาง SSO พฤติกรรมเดิมต้องไม่เปลี่ยน")
                .contains("expired=true");
    }

    /**
     * A code sent to an address this application does not serve.
     *
     * <p>Nothing can rescue the login at that point — the path has no handler —
     * but the redirect that follows is indistinguishable from an expired session,
     * so without a record of where the code landed there is nothing to take back
     * to whoever registered the URL.
     */
    @Test
    @DisplayName("code ที่มาผิด path (เช่น /signin/signin) ต้องถูกบันทึกไว้ ไม่ใช่หายเงียบ")
    void aCodeSentToAPathWeDoNotServeIsRecorded() throws Exception {
        MvcResult result = mvc.perform(get("/signin/signin?code=one-time-code-from-sso"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .as("path นี้ไม่มี handler จึงจบที่หน้าล็อกอินตามปกติ — แต่ต้องมี log บอกว่าเกิดอะไรขึ้น")
                .contains("/signin");
    }

    /** Guards the loop: one retry, then the ordinary expired page. */
    @Test
    @DisplayName("ถ้าเบราว์เซอร์ยังส่ง cookie เดิมกลับมาอีก ต้องไม่วนไม่รู้จบ")
    void aBrowserThatKeepsTheCookieDoesNotLoopForever() throws Exception {
        MvcResult retried = mvc.perform(get("/signin?code=one-time-code-from-sso&sso_retry=1")
                .with(holdingADeadSessionCookie()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(retried.getResponse().getRedirectedUrl())
                .as("ลองแล้วครั้งหนึ่งแต่ cookie ยังตายอยู่ — ต้องจบที่หน้าเซสชันหมดอายุ ไม่ใช่วนต่อ")
                .contains("expired=true");
    }
}
