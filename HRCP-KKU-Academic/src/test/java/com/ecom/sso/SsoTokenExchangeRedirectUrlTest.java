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
 * <p>{@code auth.token} checks the {@code redirectUrl} it receives against the
 * address registered for the app id and refuses the exchange when they differ,
 * answering only {@code ok=false} — which never says which of the two is wrong.
 * Sending a configured value means guessing what somebody wrote on the request
 * form months ago; sending the address the browser was actually returned to
 * cannot be wrong, because the provider is the one that chose it.
 */
@DisplayName("SSO: redirectUrl ที่ส่งไปแลก token ต้องเป็นที่อยู่ที่ SSO พากลับมาจริง")
class SsoTokenExchangeRedirectUrlTest {

    private final KkuSsoProperties props = new KkuSsoProperties();
    private final KkuSsoClient client = mock(KkuSsoClient.class);
    private final SsoAccessPolicy accessPolicy = mock(SsoAccessPolicy.class);
    private final SsoUserProvisioner provisioner = mock(SsoUserProvisioner.class);
    private final SignInService signInService = mock(SignInService.class);

    private final SsoAuthController controller =
            new SsoAuthController(props, client, accessPolicy, provisioner, signInService);

    private String redirectUrlSentFor(MockHttpServletRequest request) {
        props.setRedirectLoginUrl("https://configured-but-possibly-stale.example.invalid/signin");
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
    @DisplayName("จดทะเบียนไว้เป็น /auth/callback/login — ต้องรายงาน path นั้น")
    void theCallbackPathIsReportedAsItself() {
        assertThat(redirectUrlSentFor(arrivingAt("/auth/callback/login")))
                .isEqualTo("https://hrd.computing.kku.ac.th/auth/callback/login");
    }

    /**
     * A callback registered as {@code /signin} reaches this controller through an
     * internal forward, and after a forward the request reports where it was sent
     * rather than where it arrived. Read the wrong one and the exchange claims an
     * address the provider never used.
     */
    @Test
    @DisplayName("จดทะเบียนไว้เป็น /signin แล้ว forward ต่อ — ต้องรายงาน /signin ไม่ใช่ปลายทางของ forward")
    void aForwardedCallbackReportsTheAddressTheProviderUsed() {
        MockHttpServletRequest forwarded = arrivingAt("/auth/callback/login");
        forwarded.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "/signin");

        assertThat(redirectUrlSentFor(forwarded))
                .isEqualTo("https://hrd.computing.kku.ac.th/signin");
    }

    @Test
    @DisplayName("พอร์ตที่ไม่ใช่ 443 ต้องติดไปด้วย ไม่งั้นค่าที่ส่งไม่ตรงกับที่จดทะเบียน")
    void aNonStandardPortIsKept() {
        MockHttpServletRequest request = arrivingAt("/auth/callback/login");
        request.setServerPort(8081);

        assertThat(redirectUrlSentFor(request))
                .isEqualTo("https://hrd.computing.kku.ac.th:8081/auth/callback/login");
    }
}
