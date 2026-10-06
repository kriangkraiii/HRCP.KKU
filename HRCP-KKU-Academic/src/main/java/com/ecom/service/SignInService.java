package com.ecom.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;

import com.ecom.config.ClientIpUtils;
import com.ecom.config.CustomUser;
import com.ecom.model.UserDtls;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * The stretch between "we know who this is" and "a session exists", shared by
 * local password login and KKU SSO.
 *
 * <p>Both ways in have to answer the same three questions — is a second factor
 * owed, is the local account still allowed to sign in, and where does this role
 * land — and each answer used to live in whichever handler needed it first. The
 * cost was not tidiness: the 2FA check sat inside the password success handler
 * only, so an account with 2FA switched on skipped it entirely when the person
 * arrived through SSO. Anything a sign-in must do regardless of how it started
 * belongs here.
 */
@Service
public class SignInService {

    private static final Logger log = LoggerFactory.getLogger(SignInService.class);

    /**
     * E-mail of a half-authenticated user. Its presence in the session <em>is</em>
     * the pending challenge — {@code /2fa/**} refuses to do anything without it.
     */
    public static final String SESSION_PENDING_EMAIL = "2FA_USER_EMAIL";

    /** Which door the pending sign-in came through, so it can be resumed the same way. */
    public static final String SESSION_PENDING_METHOD = "2FA_LOGIN_METHOD";

    /**
     * An SSO access token held only for the duration of the challenge. The token
     * is issued before the second factor is cleared, so it has to survive the
     * detour without being handed to a session that is not signed in yet.
     */
    public static final String SESSION_PENDING_SSO_TOKEN = "2FA_PENDING_SSO_TOKEN";

    public static final String SESSION_ATTEMPTS = "2FA_FAILED_ATTEMPTS";
    public static final String SESSION_RESEND_COOLDOWN = "2FA_RESEND_COOLDOWN";

    /**
     * The provider token of a completed SSO session. Server-side only: it is
     * never written to a cookie or rendered into a page.
     */
    public static final String SESSION_SSO_ACCESS_TOKEN = "KKU_SSO_ACCESS_TOKEN";

    public static final String VERIFY_PATH = "/2fa/verify";

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ADMIN_LANDING = "/admin/academic/dashboard";
    private static final String USER_LANDING = "/user/academic/dashboard";

    /** How a sign-in started. Decides what has to be restored once OTP passes. */
    public enum Method {
        PASSWORD("รหัสผ่าน"),
        SSO("KKU SSO"),
        /** An administrator stepping into a test account in dev mode — see DevAccountSwitchService. */
        DEV_SWITCH("การสลับบัญชีทดสอบ (โหมด dev)");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final TwoFactorService twoFactorService;
    private final AdminLogService adminLogService;

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    /** Where the security filter parks the page a signed-out browser asked for. */
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public SignInService(TwoFactorService twoFactorService, AdminLogService adminLogService) {
        this.twoFactorService = twoFactorService;
        this.adminLogService = adminLogService;
    }

    /** Whether this account owes a one-time code before it gets a session. */
    /**
     * Whether this sign-in needs a one-time code.
     *
     * <p>The account's own setting decides, and nothing overrides it. A
     * privileged account used to be held to a second factor whatever its owner
     * preferred, which made sense while a password was the only thing proving
     * who was there. It is not any more: KKU SSO runs its own second factor
     * before it will issue a code at all, so the prompt this method used to force
     * was the same check twice — the same person, the same phone, a minute apart.
     *
     * <p>Anyone who wants the extra step still has it; the switch is in settings
     * and it is honoured here for every account and every way in.
     */
    public boolean requiresTwoFactor(UserDtls user) {
        return user != null && Boolean.TRUE.equals(user.getTwoFactorEnabled());
    }

    /**
     * Whether the local account may still sign in.
     *
     * <p>The password path gets this for free — {@code DaoAuthenticationProvider}
     * consults {@link CustomUser#isEnabled()} before the success handler ever
     * runs. Any path that builds its own {@code Authentication} has to ask on its
     * own, or an administrator deactivating an account would find it had no
     * effect over SSO.
     *
     * <p>The temporary brute-force lock is deliberately not part of this. It is
     * set by failed <em>password</em> attempts and lifts itself after a wait; an
     * SSO login never presented that password, so refusing it would punish the
     * account for someone else hammering the form.
     */
    public boolean isLocallyUsable(UserDtls user) {
        return user != null && Boolean.TRUE.equals(user.getIsEnable());
    }

    /**
     * Parks a sign-in that has cleared its first factor and mails a code.
     *
     * <p>Any authenticated context is torn out of the session as well as out of
     * the holder. That is not belt-and-braces: the form-login filter persists the
     * context <em>before</em> it calls its success handler, so a challenge that
     * only cleared the thread-local would leave a fully signed-in session behind
     * and the OTP screen could be walked past by typing a protected URL.
     *
     * @param ssoAccessToken the provider token for {@link Method#SSO}, else {@code null}
     */
    public void startTwoFactor(HttpServletRequest request, UserDtls user,
            Method method, String ssoAccessToken) {

        HttpSession session = request.getSession();
        session.setAttribute(SESSION_PENDING_EMAIL, user.getEmail());
        session.setAttribute(SESSION_PENDING_METHOD, method.name());
        // A fresh challenge starts with a fresh budget of attempts, so a previous
        // half-finished one cannot leave the next login pre-exhausted.
        session.removeAttribute(SESSION_ATTEMPTS);
        session.removeAttribute(SESSION_RESEND_COOLDOWN);

        if (ssoAccessToken != null) {
            session.setAttribute(SESSION_PENDING_SSO_TOKEN, ssoAccessToken);
        } else {
            session.removeAttribute(SESSION_PENDING_SSO_TOKEN);
        }

        String otp = twoFactorService.generateOtp(user);
        twoFactorService.sendOtpEmail(user, otp, "LOGIN");

        session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SecurityContextHolder.clearContext();
    }

    /** The method a parked sign-in came through, defaulting to password. */
    public Method pendingMethod(HttpSession session) {
        Object stored = session.getAttribute(SESSION_PENDING_METHOD);
        if (stored instanceof String name) {
            try {
                return Method.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Fall through: an unreadable value must not strand the sign-in.
            }
        }
        return Method.PASSWORD;
    }

    /** The token stashed for a parked SSO sign-in, or {@code null}. */
    public String pendingSsoToken(HttpSession session) {
        return (String) session.getAttribute(SESSION_PENDING_SSO_TOKEN);
    }

    /** Drops every trace of a pending challenge without touching the session itself. */
    public void abandonTwoFactor(HttpSession session) {
        session.removeAttribute(SESSION_PENDING_EMAIL);
        session.removeAttribute(SESSION_PENDING_METHOD);
        session.removeAttribute(SESSION_PENDING_SSO_TOKEN);
        session.removeAttribute(SESSION_ATTEMPTS);
        session.removeAttribute(SESSION_RESEND_COOLDOWN);
    }

    /**
     * Signs the user in and returns where to send them.
     *
     * <p>The context is written through the repository rather than left in
     * {@link SecurityContextHolder}: since Spring Security 6 the holder is
     * per-request and nothing persists it for us, so a context that is only set
     * there evaporates on the redirect that follows.
     *
     * <p>The session id is rotated afterwards, which both defeats session
     * fixation and keeps the attributes we still need.
     *
     * @param ssoAccessToken kept for the session's lifetime when signing in via SSO
     */
    public String completeSignIn(HttpServletRequest request, HttpServletResponse response,
            UserDtls user, Method method, String ssoAccessToken) {

        CustomUser principal = new CustomUser(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(user.getRole())));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        HttpSession session = request.getSession();
        abandonTwoFactor(session);
        if (ssoAccessToken != null) {
            session.setAttribute(SESSION_SSO_ACCESS_TOKEN, ssoAccessToken);
        }

        request.changeSessionId();

        recordSignIn(request, user, method);
        return destinationFor(request, response, user);
    }

    /**
     * Where to send someone who has just signed in: the page they were trying to
     * open, else their role's landing page.
     *
     * <p>Every button in the system's e-mails points at a protected page. Opened
     * from a mail client the browser has no session yet, so the security filter
     * parks the address and sends it to sign in — and until this existed nobody
     * ever read it back, so every such button ended on the dashboard. The parked
     * address survives the SSO round trip and the OTP screen because both keep the
     * session.
     *
     * <p>Only the path and query are reused, never a host, so this cannot be
     * turned into an open redirect. A page the role cannot open falls back to the
     * landing page rather than a 403.
     */
    public String destinationFor(HttpServletRequest request, HttpServletResponse response, UserDtls user) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved == null) {
            return landingPageFor(user);
        }
        requestCache.removeRequest(request, response);
        if (!(saved instanceof DefaultSavedRequest parked) || !"GET".equals(parked.getMethod())) {
            return landingPageFor(user);
        }
        String path = parked.getRequestURI();
        if (!mayReturnTo(path, user)) {
            return landingPageFor(user);
        }
        String query = parked.getQueryString();
        return query == null || query.isBlank() ? path : path + "?" + query;
    }

    private static boolean mayReturnTo(String path, UserDtls user) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\")) {
            return false;
        }
        if (user == null) {
            return false;
        }
        if (user.isExternal()) {
            // ผู้ลงนามภายนอกมาเพื่อลงนามอย่างเดียว — อีเมลที่ส่งถึงเขาชี้ไปที่ /esign เท่านั้น
            return path.startsWith("/esign/");
        }
        // /admin/** ต้องเป็น ROLE_ADMIN และ /user/** ต้องเป็น ROLE_USER (SecurityConfig) แอดมินไม่มี ROLE_USER
        // จึงพากลับไปหน้า /user/ ที่ค้างไว้ไม่ได้ — เช่นตอนสลับบัญชีทดสอบกลับมาเป็นแอดมิน จะเจอ 403
        boolean admin = ROLE_ADMIN.equals(user.getRole());
        return admin ? !path.startsWith("/user/") : !path.startsWith("/admin/");
    }

    /** Where a role lands after signing in. One answer for every entry point. */
    public String landingPageFor(UserDtls user) {
        if (user != null && user.isExternal()) {
            // ผู้ลงนามภายนอกไม่มีแดชบอร์ด/คำร้อง — มาเพื่อลงนามอย่างเดียว
            return EXTERNAL_LANDING;
        }
        return user != null && ROLE_ADMIN.equals(user.getRole()) ? ADMIN_LANDING : USER_LANDING;
    }

    private static final String EXTERNAL_LANDING = "/esign/inbox";

    /**
     * Writes the audit trail entry for a completed sign-in.
     *
     * <p>Completed is the operative word: a login parked at the OTP screen is not
     * one, and recording it there would make the log claim access that was never
     * granted.
     */
    public void recordSignIn(HttpServletRequest request, UserDtls user, Method method) {
        try {
            adminLogService.logWithDetails(user.getEmail(),
                    user.getName() != null ? user.getName() : user.getEmail(),
                    "LOGIN_SUCCESS",
                    "เข้าสู่ระบบสำเร็จด้วย " + method.label() + " (" + user.getRole() + ")",
                    ClientIpUtils.resolveClientIp(request),
                    "/signin",
                    request.getHeader("User-Agent"));
        } catch (Exception e) {
            // Audit logging must never break the sign-in, but it must leave a trace.
            log.warn("Failed to write sign-in audit log: {}", e.toString());
        }
    }
}
