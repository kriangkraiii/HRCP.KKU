package com.ecom.sso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.external.model.FsFaculty;
import com.ecom.model.UserDtls;
import com.ecom.service.SignInService;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * The KKU SSO login and logout flow.
 *
 * <p>Sequence, per the university's guide:
 * <ol>
 *   <li>{@code /auth/sso/login} sends the browser to SSO.</li>
 *   <li>SSO returns to {@code /auth/callback/login?code=…}.</li>
 *   <li>The code is exchanged server-side for a token and an e-mail address.</li>
 *   <li>{@link SsoAccessPolicy} decides whether that address may enter.</li>
 *   <li>A local account is provisioned and {@link SignInService} finishes the
 *       sign-in — including the one-time code when the account asks for one.</li>
 * </ol>
 *
 * <p>Step five is deliberately not written out here. Proving an identity at the
 * university is the first factor and nothing more: an account that has 2FA
 * switched on must be challenged whichever door it came through, or the setting
 * would quietly mean "except over SSO".
 *
 * <p><b>Known limitation of the provider:</b> the login endpoint accepts only
 * {@code app=<AppID>} — there is no {@code state} parameter to bind the callback
 * to the browser that started the flow, so a login-CSRF cannot be fully ruled
 * out at the protocol level. What is mitigated here: the code is exchanged
 * server-side using the client secret, the session id is rotated on sign-in so a
 * pre-seeded session cannot be reused, and a code may only ever produce a
 * session for an allowlisted address.
 */
@Controller
public class SsoAuthController {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthController.class);

    private final KkuSsoProperties props;
    private final KkuSsoClient client;
    private final SsoAccessPolicy accessPolicy;
    private final SsoUserProvisioner provisioner;
    private final SignInService signInService;

    public SsoAuthController(KkuSsoProperties props,
            KkuSsoClient client,
            SsoAccessPolicy accessPolicy,
            SsoUserProvisioner provisioner,
            SignInService signInService) {
        this.props = props;
        this.client = client;
        this.accessPolicy = accessPolicy;
        this.provisioner = provisioner;
        this.signInService = signInService;
    }

    /** Starts a login by handing the browser to KKU SSO. */
    @GetMapping("/auth/sso/login")
    public String startLogin(RedirectAttributes redirect) {
        log.info("SSO login starting — stage={} appId={} redirectUrl={} loginUrl={}",
                props.getStage(), props.getAppId(), props.getRedirectLoginUrl(), props.loginUrl());

        if (!props.isConfigured()) {
            log.error("SSO login attempted but configuration is incomplete: {}", props.describeMissing());
            redirect.addFlashAttribute("errorMsg",
                    "ระบบ SSO ยังไม่ได้ตั้งค่าให้สมบูรณ์ กรุณาติดต่อผู้ดูแลระบบ");
            return "redirect:/signin";
        }
        return "redirect:" + props.loginUrl();
    }

    /**
     * Where KKU SSO returns after a successful login.
     *
     * <p>The path must match the Redirect URL registered on the SSO request form.
     *
     * <p>Mapped with and without a trailing slash: Spring 6 treats the two as
     * different paths, and the provider has been observed to use the slashed
     * form. A callback that matches no mapping is indistinguishable from an
     * expired session by the time the browser sees it.
     */
    @GetMapping({"/auth/callback/login", "/auth/callback/login/"})
    public String loginCallback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirect) {

        log.info("SSO callback received — uri={} code={} error={} ({})",
                request.getRequestURI(), abbreviate(code), error, errorDescription);

        if (error != null && !error.isBlank()) {
            log.warn("SSO provider returned an error: {} — {}", error, errorDescription);
            return denied(redirect, "KKU SSO แจ้งข้อผิดพลาด: " + (errorDescription != null ? errorDescription : error));
        }

        if (code == null || code.isBlank()) {
            log.warn("SSO callback arrived without a code parameter — "
                    + "the provider bounced the browser back without completing a login. "
                    + "Check that kku.sso.stage matches the environment this app-id is registered on, "
                    + "and that the registered redirect URL is exactly {}", props.getRedirectLoginUrl());
            return denied(redirect, "การเข้าสู่ระบบไม่สมบูรณ์ ไม่ได้รับ code จาก KKU SSO");
        }

        var token = client.exchangeCode(code, registeredRedirectUrl(request));
        if (token.isEmpty()) {
            log.warn("SSO token exchange failed — no token for the code returned by the provider");
            return denied(redirect, "ยืนยันตัวตนกับระบบ SSO ไม่สำเร็จ (ไม่สามารถแลก Token ได้) กรุณาลองใหม่อีกครั้ง");
        }

        KkuSsoClient.SsoToken ssoToken = token.get();
        log.info("SSO token exchanged for {}", ssoToken.email());

        SsoAccessPolicy.Decision decision = accessPolicy.evaluate(ssoToken.email());
        if (!decision.allowed()) {
            log.warn("SSO access denied for {}: {}", ssoToken.email(), decision.reason());
            return denied(redirect, decision.reason());
        }

        UserDtls user = provisionAllowingForARace(ssoToken, decision.faculty());
        if (user == null) {
            log.error("SSO sign-in could not provision a local account for {}", ssoToken.email());
            return denied(redirect, "เกิดข้อผิดพลาดในการเตรียมบัญชีผู้ใช้ในระบบ");
        }

        if (signInService.requiresTwoFactor(user)) {
            log.info("SSO sign-in for {} needs a second factor", user.getEmail());
            signInService.startTwoFactor(request, user, SignInService.Method.SSO, ssoToken.accessToken());
            return "redirect:" + SignInService.VERIFY_PATH;
        }

        String landing = signInService.completeSignIn(request, response, user,
                SignInService.Method.SSO, ssoToken.accessToken());

        log.info("SSO sign-in complete for {} (role {}) — sending them to {}",
                user.getEmail(), user.getRole(), landing);
        return "redirect:" + landing;
    }

    /** Where KKU SSO returns after logging the user out of the identity provider. */
    @GetMapping({"/auth/callback/logout", "/auth/callback/logout/", "/logout"})
    public String logoutCallback(HttpServletRequest request) {
        endLocalSession(request);
        return "redirect:/signin?logout=true";
    }

    /**
     * Ends our session, then the SSO session.
     *
     * <p>Reachable directly, but the sidebar's {@code POST /logout} arrives at the
     * same destination: the filter chain's logout success handler forwards to the
     * provider whenever SSO is the way in. Clearing only our cookie would leave
     * the SSO session live, so the next "login" would silently re-enter without
     * any credential prompt.
     */
    @GetMapping("/auth/sso/logout")
    public String logout(HttpServletRequest request) {
        endLocalSession(request);

        if (!props.isConfigured()) {
            return "redirect:/signin?logout=true";
        }
        return "redirect:" + props.logoutUrl();
    }

    /**
     * Provisions the account, tolerating a concurrent sign-in for the same person.
     *
     * <p>Two callbacks for one address — a double-clicked button, a retried
     * request — can both find no local row and both try to insert one. The unique
     * constraint on the e-mail refuses the second, and the right answer is not to
     * fail the login but to use the row the first one created: a second call finds
     * it and refreshes it.
     *
     * <p>The retry sits here rather than inside the provisioner because the
     * violation is raised when its transaction commits, by which point the method
     * has returned; calling it again is the only way to get a fresh transaction.
     *
     * @return the account, or null if it could not be provisioned even on retry
     */
    private UserDtls provisionAllowingForARace(KkuSsoClient.SsoToken token, FsFaculty faculty) {
        try {
            return provisioner.provision(token, faculty);
        } catch (DataIntegrityViolationException firstAttempt) {
            log.info("Concurrent SSO sign-in for the same account; reusing the row that won");
            try {
                return provisioner.provision(token, faculty);
            } catch (DataIntegrityViolationException secondAttempt) {
                // Twice means it is not a race, it is data that will not fit.
                log.error("Provisioning failed twice on a constraint: {}", secondAttempt.getMessage());
                return null;
            }
        }
    }

    private void endLocalSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * What {@code auth.token} is told the redirect URL was.
     *
     * <p>The provider matches this against the URL registered for the app id, and
     * answers a mismatch with nothing more useful than "cannot find a matching
     * credential". The configured value wins because it is a copy of what was
     * written on the request form — the registration itself.
     *
     * <p>It is deliberately <em>not</em> taken from the address the browser
     * arrived at, tempting though that is. The provider returns to
     * {@code /signin/} while the registration reads {@code /signin}: the two
     * differ by the trailing slash, the provider compares them literally, and the
     * address it chose is therefore the wrong one to echo back.
     *
     * @return the configured redirect URL, or the address the callback arrived at
     *         when nothing is configured
     */
    private String registeredRedirectUrl(HttpServletRequest request) {
        String configured = props.getRedirectLoginUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return arrivalUrl(request);
    }

    /**
     * The address the browser was actually returned to, without the query string.
     *
     * <p>Only used when nothing is configured. The forward attribute comes first
     * because a callback registered as {@code /signin} is handed on to this
     * controller internally — after that forward {@code getRequestURI()} reports
     * the destination, and the address the provider used is only still available
     * under {@code jakarta.servlet.forward.request_uri}.
     */
    private String arrivalUrl(HttpServletRequest request) {
        String forwardedFrom = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        String path = forwardedFrom != null ? forwardedFrom : request.getRequestURI();
        if (path == null || path.isBlank()) {
            return props.getRedirectLoginUrl();
        }
        // getRequestURL() carries scheme, host and port as the client saw them —
        // server.forward-headers-strategy=native keeps that true behind the proxy.
        StringBuffer full = request.getRequestURL();
        int pathStart = full.indexOf(request.getRequestURI());
        String origin = pathStart > 0 ? full.substring(0, pathStart) : full.toString();
        return origin + path;
    }

    /** Enough of a code to correlate with the provider's logs, never the whole thing. */
    private static String abbreviate(String code) {
        if (code == null || code.isBlank()) {
            return "NONE";
        }
        return code.length() > 10 ? code.substring(0, 10) + "…" : code;
    }

    private String denied(RedirectAttributes redirect, String message) {
        redirect.addFlashAttribute("errorMsg", message);
        return "redirect:/signin?sso_error";
    }
}
