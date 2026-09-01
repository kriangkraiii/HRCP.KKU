package com.ecom.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.ecom.support.AbstractFlowTest;

/**
 * Coming back from the provider's logout page.
 *
 * <p>Signing out leaves for KKU SSO so that its session ends too, and the
 * provider then returns the browser to the address registered as the logout
 * URL. That return arrives with no session of ours left, so it has to be a
 * public path — and it has to be mapped under whichever spelling the provider
 * uses. This one has already been seen adding a trailing slash on the way in.
 *
 * <p>When either is missing the browser lands on something with no handler,
 * security redirects it away, and from the outside the button simply appears to
 * do nothing.
 */
@DisplayName("SSO: ขากลับจากการออกจากระบบต้องมีปลายทางรองรับทุกรูปแบบ")
class SsoLogoutReturnPathTest extends AbstractFlowTest {

    private void expectLandsOnSignedOutPage(String path) throws Exception {
        MvcResult result = mvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .as("%s ต้องพากลับหน้าเข้าสู่ระบบพร้อมบอกว่าออกจากระบบแล้ว", path)
                .isEqualTo("/signin?logout=true");
    }

    @Test
    @DisplayName("/logout — รูปแบบที่แจ้งจดทะเบียนไว้")
    void theRegisteredLogoutUrlIsHandled() throws Exception {
        expectLandsOnSignedOutPage("/logout");
    }

    @Test
    @DisplayName("/logout/ — รูปแบบมี slash ปิดท้าย ซึ่ง SSO ตัวนี้ใช้บน path ขาเข้ามาแล้ว")
    void theTrailingSlashFormIsHandledToo() throws Exception {
        expectLandsOnSignedOutPage("/logout/");
    }

    @Test
    @DisplayName("/auth/callback/logout — path ตามคู่มือ SSONext")
    void theCallbackPathFromTheManualIsHandled() throws Exception {
        expectLandsOnSignedOutPage("/auth/callback/logout");
    }
}
