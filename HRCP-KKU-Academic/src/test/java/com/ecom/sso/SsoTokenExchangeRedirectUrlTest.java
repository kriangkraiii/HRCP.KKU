package com.ecom.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.ecom.service.SignInService;

import jakarta.servlet.RequestDispatcher;

/**
 * What the token exchange is told the redirect URL was.
 *
 * <p>{@code auth.token} compares it with the address registered for the app id
 * and refuses a mismatch with "cannot find a matching credential", which names
 * neither of the two values it compared. The registration is the authority, so
 * the configured copy of it is what gets sent.
 *
 * <p>The address the browser arrived at is <em>not</em> used, and the reason is
 * concrete: KKU SSO returns to {@code /signin/} while the registration reads
 * {@code /signin}. Echoing back the address it chose was rejected.
 */
@DisplayName("SSO: redirectUrl ที่ส่งไปแลก token ต้องเป็นค่าที่จดทะเบียนไว้")
class SsoTokenExchangeRedirectUrlTest {

    private final KkuSsoProperties props = new KkuSsoProperties();
    private final KkuSsoClient client = mock(KkuSsoClient.class);
    private final SsoAccessPolicy accessPolicy = mock(SsoAccessPolicy.class);
    private final SsoUserProvisioner provisioner = mock(SsoUserProvisioner.class);
    private final SignInService signInService = mock(SignInService.class);

    private final SsoAuthController controller =
            new SsoAuthController(props, client, accessPolicy, provisioner, signInService);

    private String redirectUrlSentFor(MockHttpServletRequest request) {
        when(client.exchangeCode(anyString(), anyString())).thenReturn(Optional.empty());

        controller.loginCallback("one-time-code", null, null,
                request, new MockHttpServletResponse(), new RedirectAttributesModelMap());

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(client).exchangeCode(anyString(), sent.capture());
        return sent.getValue();
    }

    private MockHttpServletRequest arrivingAt(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setScheme("https");
        request.setServerName("hrd.computing.kku.ac.th");
        request.setServerPort(443);
        request.setRequestURI(path);
        return request;
    }

    @Test
    @DisplayName("ค่าที่จดทะเบียนไว้ต้องชนะเสมอ แม้ SSO จะพากลับมาที่ path ที่ต่างออกไป")
    void theRegisteredValueIsWhatGetsSent() {
        props.setRedirectLoginUrl("https://hrd.computing.kku.ac.th/signin");

        // SSO พากลับมาที่ /signin/ (มี slash) ซึ่งไม่ตรงกับทะเบียน — ต้องไม่เอาค่านี้ไปใช้
        assertThat(redirectUrlSentFor(arrivingAt("/signin/")))
                .as("ส่งค่าที่ SSO ใช้เด้งกลับ จะได้ error 'cannot find a matching credential'")
                .isEqualTo("https://hrd.computing.kku.ac.th/signin");
    }

    /**
     * A callback registered as {@code /signin} reaches this controller through an
     * internal forward, and after a forward the request reports where it was sent
     * rather than where it arrived. Read the wrong one and the exchange claims an
     * address the provider never used.
     */
    /**
     * With nothing configured there is no registration to copy, so the address the
     * callback arrived at is the only evidence available — better than sending
     * nothing at all.
     */
    @Test
    @DisplayName("ถ้าไม่ได้ตั้งค่าไว้เลย จึงค่อยใช้ที่อยู่ที่ callback มาถึงจริง")
    void withNothingConfiguredTheArrivalAddressIsUsed() {
        props.setRedirectLoginUrl("");

        MockHttpServletRequest forwarded = arrivingAt("/auth/callback/login");
        forwarded.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "/signin");

        assertThat(redirectUrlSentFor(forwarded))
                .as("หลัง forward ต้องอ่านที่อยู่เดิมจาก jakarta.servlet.forward.request_uri")
                .isEqualTo("https://hrd.computing.kku.ac.th/signin");
    }

    @Test
    @DisplayName("ถ้าไม่ได้ตั้งค่าไว้ พอร์ตที่ไม่ใช่ 443 ต้องติดไปด้วย")
    void aNonStandardPortIsKept() {
        props.setRedirectLoginUrl("");

        MockHttpServletRequest request = arrivingAt("/auth/callback/login");
        request.setServerPort(8081);

        assertThat(redirectUrlSentFor(request))
                .isEqualTo("https://hrd.computing.kku.ac.th:8081/auth/callback/login");
    }
}
