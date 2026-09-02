package com.ecom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.ecom.sso.KkuSsoProperties;

@Configuration
@EnableWebSecurity
// Turns @PreAuthorize into real enforcement. Without it the annotation is
// silently ignored, which reads as a guard in review while protecting nothing.
@EnableMethodSecurity
public class SecurityConfig {

        private final RateLimitFilter rateLimitFilter;
        private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
        private final AuthFailureHandlerImpl authenticationFailureHandler;
        private final UserDetailsServiceImpl userDetailsServiceImpl;
        private final AuthModeProperties authProperties;
        private final KkuSsoProperties ssoProperties;

        /**
         * ชื่อคุกกี้ session — อ่านจากค่าเดียวกับที่ตั้ง container ไว้
         *
         * <p>เคยเขียน "JSESSIONID" ตายตัวไว้ตรงตอน logout ถ้าเปลี่ยนชื่อคุกกี้ใน
         * properties แล้วลืมแก้ตรงนี้ logout จะไม่ลบคุกกี้ให้ โดยไม่มีอะไรฟ้อง
         */
        private final String sessionCookieName;

        private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

        /** @see #securityFilterChain the reasoning sits with the repository setup */
        private static final String CSRF_COOKIE_NAME = "HRCPCT";
        private static final String CSRF_HEADER_NAME = "X-HRCP-CT";

        public SecurityConfig(RateLimitFilter rateLimitFilter,
                        CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                        AuthFailureHandlerImpl authenticationFailureHandler,
                        UserDetailsServiceImpl userDetailsServiceImpl,
                        AuthModeProperties authProperties,
                        KkuSsoProperties ssoProperties,
                        @org.springframework.beans.factory.annotation.Value(
                                        "${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) {
                this.rateLimitFilter = rateLimitFilter;
                this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
                this.authenticationFailureHandler = authenticationFailureHandler;
                this.userDetailsServiceImpl = userDetailsServiceImpl;
                this.authProperties = authProperties;
                this.ssoProperties = ssoProperties;
                this.sessionCookieName = sessionCookieName;
        }

        /**
         * What to do when a browser presents a session this server has never heard of.
         *
         * <p>Ordinarily: send it to the "your session expired" page, which is what
         * {@code invalidSessionUrl} used to do for everything.
         *
         * <p>The exception is a login coming back from KKU SSO. Sessions live in
         * memory, so every restart leaves browsers holding a cookie for a session
         * that no longer exists — for as long as the cookie lasts, half an hour
         * here. The provider returns to {@code /signin?code=…} and that request
         * carries the dead cookie, so session management intercepted it and
         * redirected <em>before any controller ran</em>. The one-time code went
         * with it. Nothing was logged, because nothing of ours was reached: the
         * professor simply landed back on the login page, and trying again
         * produced exactly the same nothing.
         *
         * <p>So when a code is present the dead cookie is cleared and the browser
         * is sent back to the same address with the code intact. The second time
         * around there is no stale id to object to, and the login continues.
         *
         * <p>{@code sso_retry} bounds it to one attempt: a browser that ignores
         * the delete gets the expired page rather than a redirect loop.
         */
        private org.springframework.security.web.session.InvalidSessionStrategy staleCookieMustNotBreakAnSsoLogin() {
                var expiredPage = new org.springframework.security.web.session.SimpleRedirectInvalidSessionStrategy(
                                "/signin?expired=true");

                return (request, response) -> {
                        String code = request.getParameter("code");
                        boolean alreadyRetried = request.getParameter("sso_retry") != null;

                        if (code == null || code.isBlank() || alreadyRetried
                                        || !isKnownCallbackPath(request.getRequestURI())) {
                                expiredPage.onInvalidSessionDetected(request, response);
                                return;
                        }

                        jakarta.servlet.http.Cookie dead = new jakarta.servlet.http.Cookie(sessionCookieName, "");
                        dead.setPath("/");
                        dead.setMaxAge(0);
                        dead.setHttpOnly(true);
                        dead.setSecure(request.isSecure());
                        response.addCookie(dead);

                        String query = request.getQueryString();
                        String again = request.getRequestURI()
                                        + "?" + (query == null || query.isBlank() ? "code=" + code : query)
                                        + "&sso_retry=1";
                        response.sendRedirect(again);
                };
        }

        /**
         * The addresses a login may legitimately come back to.
         *
         * <p>An allowlist rather than a check that the path merely looks local.
         * The retry above sends the browser back to the address it asked for, and
         * a request for {@code //evil.example/x?code=1} yields the request URI
         * {@code //evil.example/x} — which {@code sendRedirect} treats as
         * protocol-relative and follows off-site. Naming the four paths that can
         * carry a code leaves nothing to reason about.
         */
        private static boolean isKnownCallbackPath(String uri) {
                return "/signin".equals(uri)
                                || "/signin/".equals(uri)
                                || "/auth/callback/login".equals(uri)
                                || "/auth/callback/login/".equals(uri);
        }

        /**
         * Screens that exist only to serve local password login. They stay open in
         * dev mode and are refused outright once SSO is the only way in.
         *
         * <p>
         * {@code /2fa/**} is not one of them. The one-time code is the second
         * factor for <em>both</em> ways in, so closing it under SSO would turn the
         * user's own 2FA switch into a lockout.
         */
        private static String[] passwordLoginPaths() {
                return new String[] {
                                "/forgot-password", "/reset-password",
                                "/first-login", "/first-login/**"
                };
        }

        @Bean
        @Primary
        public AuthenticationSuccessHandler authenticationSuccessHandler() {
                return customAuthenticationSuccessHandler;
        }

        @Bean
        public UserDetailsService userDetailsService() {
                return userDetailsServiceImpl;
        }

        @Bean
        public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder);
                return provider;
        }

        @Bean
        public org.springframework.security.web.firewall.HttpFirewall httpFirewall() {
                // Allow all methods through the firewall so SecurityHeadersFilter
                // (which runs at HIGHEST_PRECEDENCE, before Spring Security)
                // can uniformly reject TRACE/TRACK/OPTIONS with identical 405 responses.
                // If the firewall blocks them, it produces a DIFFERENT error response
                // (JSON body + Allow header listing all methods + JSESSIONID cookie),
                // creating the observable response discrepancy ZAP detects as Proxy Disclosure.
                org.springframework.security.web.firewall.StrictHttpFirewall firewall =
                                new org.springframework.security.web.firewall.StrictHttpFirewall();
                firewall.setUnsafeAllowAnyHttpMethod(true);
                return firewall;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider authenticationProvider)
                        throws Exception {
                http
                                // Rate limit filter runs before authentication
                                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                                // CSRF protection with CookieCsrfTokenRepository for AJAX and form-based POST
                                .csrf(csrf -> {
                                        // Spring Security 6 uses deferred tokens by default.
                                        // Force eager loading so the token is always available.
                                        var handler = new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler();
                                        handler.setCsrfRequestAttributeName(null); // force eager token resolution

                                        // The cookie is HttpOnly. It used to be readable by script
                                        // (withHttpOnlyFalse) purely so AJAX could copy the token
                                        // into a header — which also handed the token to any XSS on
                                        // the page. Scripts now read it from the <meta name="_csrf">
                                        // tag the server renders, so the cookie itself can stay
                                        // closed. SameSite=Lax stops it riding along on
                                        // cross-site requests.
                                        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();

                                        // Named after this application rather than left at the
                                        // defaults. "XSRF-TOKEN" with an "X-XSRF-TOKEN" header is the
                                        // shape Spring Security ships with, and it is the first thing
                                        // a fingerprinting tool reads off a response: the cookie name
                                        // alone announces the framework before anything else is
                                        // examined. The session cookie was renamed for the same
                                        // reason and these now match it.
                                        //
                                        // Nothing else needs to know the names: forms render
                                        // ${_csrf.parameterName} and scripts read the header name
                                        // from the <meta name="_csrf_header"> tag, both of which
                                        // follow whatever is set here.
                                        repository.setCookieName(CSRF_COOKIE_NAME);
                                        repository.setHeaderName(CSRF_HEADER_NAME);
                                        repository.setCookieCustomizer(cookie -> cookie
                                                        .httpOnly(true)
                                                        .sameSite("Lax")
                                                        .secure(true)
                                                        .path("/"));

                                        csrf.ignoringRequestMatchers(
                                                        "/static/**", "/css/**", "/js/**", "/img/**", "/vendor/**",
                                                        "/admin/css/**", "/admin/js/**", "/admin/img/**",
                                                        "/favicon.ico", "/robots.txt", "/sitemap.xml",
                                                        "/css", "/js", "/img", "/static", "/vendor");
                                        csrf.csrfTokenRepository(repository)
                                                        .csrfTokenRequestHandler(handler);
                                })
                                // X-Frame-Options is NOT dropped — SecurityHeadersFilter sets it
                                // to SAMEORIGIN on every response. Spring Security's writer is
                                // switched off here so there is exactly one owner. Two owners
                                // meant two values (this one's default DENY overwrote the
                                // filter's SAMEORIGIN on normal requests, but not on the
                                // filter's 405 short-circuit, which Spring Security never sees).
                                // The filter wins the job because it covers both paths and its
                                // SAMEORIGIN agrees with the frame-ancestors 'self' in its CSP.
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.disable()))

                                // Session management
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                                .sessionFixation(fixation -> fixation.changeSessionId())
                                                .invalidSessionStrategy(staleCookieMustNotBreakAnSsoLogin())
                                                .maximumSessions(1)
                                                .maxSessionsPreventsLogin(false))

                                .authenticationProvider(authenticationProvider)
                                .authorizeHttpRequests(authz -> authz
                                                // "/signin/" เป็นคนละ path กับ "/signin" ในสายตา Spring 6
                                                // และ KKU SSO ส่ง code กลับมาที่ตัวมี slash ปิดท้าย
                                                //
                                                // "/logout" ต้องเปิดด้วย: .logout().permitAll() ครอบเฉพาะ POST
                                                // แต่ SSO ส่งผู้ใช้กลับมาด้วย GET หลังจากปิด session ฝั่งเขาแล้ว
                                                // ซึ่งตอนนั้นเราไม่มี session เหลือ จึงถูกมองเป็นคนแปลกหน้า
                                                .requestMatchers("/", "/signin", "/signin/", "/logout", "/logout/",
                                                                "/static/**", "/css/**", "/js/**", "/img/**",
                                                                "/vendor/**",
                                                                "/img/profile_img/**",
                                                                "/admin/css/**", "/admin/js/**", "/admin/img/**",
                                                                "/favicon.ico", "/error", "/403")
                                                .permitAll()
                                                // SSO entry and the provider's callbacks must be
                                                // reachable before a session exists.
                                                .requestMatchers("/auth/sso/**", "/auth/callback/**")
                                                .permitAll()
                                                // The one-time code screen sits between the first
                                                // factor and a session, so it is by definition
                                                // reached unauthenticated. It guards itself: with
                                                // no parked sign-in in the session every handler
                                                // there bounces back to /signin.
                                                .requestMatchers("/2fa/**")
                                                .permitAll()
                                                // Password-recovery and first-login screens only make
                                                // sense while local password login is enabled. In SSO
                                                // mode they are dead weight and extra attack surface,
                                                // so they are not opened up at all.
                                                .requestMatchers(passwordLoginPaths())
                                                .access((auth, ctx) -> new org.springframework.security.authorization.AuthorizationDecision(
                                                                authProperties.isPasswordLoginEnabled()))
                                                // Everything on actuator is closed. The health endpoint
                                                // used to be public and leaked the group list to an
                                                // unauthenticated scanner; container probes should use
                                                // a port-level check instead.
                                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                                // คลังไฟล์ส่วนตัวของผู้ยื่นถูกยกเลิกทั้งฟีเจอร์
                                                //
                                                // UserFileManagerController ถูกคอมเมนต์ทิ้งแล้ว จึงไม่มี
                                                // handler ที่ /user/academic/storage อีก คำขอที่เข้ามาจะ
                                                // ตกไปที่กฎ "/user/**" แล้วจบด้วย 404 ตามปกติ
                                                // ไม่ต้องมีกฎเฉพาะกิจกันไว้อีก
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/user/**").hasRole("USER")
                                                .anyRequest().authenticated())
                                .formLogin(form -> {
                                        form.loginPage("/signin")
                                                        .loginProcessingUrl("/login")
                                                        .usernameParameter("email")
                                                        .passwordParameter("password")
                                                        .successHandler(authenticationSuccessHandler())
                                                        .failureHandler(authenticationFailureHandler)
                                                        .permitAll();
                                        if (!authProperties.isPasswordLoginEnabled()) {
                                                // In SSO mode /login must not accept credentials at
                                                // all. Hiding the form but leaving the endpoint live
                                                // would keep password guessing available.
                                                form.loginProcessingUrl("/login-disabled");
                                        }
                                })
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .invalidateHttpSession(true)
                                                .deleteCookies(sessionCookieName)
                                                .clearAuthentication(true)
                                                .logoutSuccessHandler((request, response, authentication) -> {
                                                        // Where the browser goes next is decided here and nowhere
                                                        // else. In SSO mode it leaves for the provider, and if that
                                                        // journey stalls there is otherwise nothing on our side that
                                                        // says a logout was ever asked for.
                                                        String destination = logoutDestination();
                                                        log.info("Local session ended — sending the browser to {}", destination);
                                                        response.sendRedirect(destination);
                                                })
                                                .permitAll())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        // A login coming back from SSO must never end up here. If it
                                                        // does, the provider was told to return to an address this
                                                        // application does not serve — and because the redirect happens
                                                        // before any controller, nothing else in the system would ever
                                                        // mention it. The one-time code is discarded either way, so
                                                        // this is the only place the path can be recorded.
                                                        String strayCode = request.getParameter("code");
                                                        if (strayCode != null && !strayCode.isBlank()) {
                                                                log.warn("An SSO code arrived at {} — a path this application "
                                                                                + "does not serve, so the login cannot complete. The redirect "
                                                                                + "URL registered for this app-id must be exactly the sign-in "
                                                                                + "address, with no extra path segments.",
                                                                                request.getRequestURI());
                                                        }
                                                        response.sendRedirect("/signin?expired=true");
                                                })
                                                // Without this a signed-in user whose role falls short
                                                // dropped through to the container's error page, which
                                                // is both unstyled and a fingerprinting surface.
                                                .accessDeniedPage("/403"));
                return http.build();
        }

        /**
         * Where a logout lands.
         *
         * <p>
         * Under SSO it has to reach the provider. Dropping only our own cookie
         * would leave the university's session live, so the next press of "sign in"
         * would walk straight back in without a prompt — which is not what anyone
         * clicking "ออกจากระบบ" on a shared machine is asking for. Handling it here
         * rather than in the template means the one logout button behaves correctly
         * in both modes.
         */
        /**
         * Where the browser goes the moment the local session ends.
         *
         * <p>Always our own signed-out page, even in SSO mode. It used to hand the
         * browser to the provider so that its session ended too, but that address
         * serves a single-page app: the person sat watching a spinner while
         * someone else's JavaScript bundle loaded, decided nothing had happened,
         * and clicked again.
         *
         * <p>The provider's session is still ended — the signed-out page loads
         * that same logout address in a hidden frame, which does the work without
         * anyone waiting for it. Both hosts sit under {@code kku.ac.th}, so the
         * browser treats the frame as same-site and sends the provider's cookie
         * with it; the sign-out therefore takes effect exactly as it did before.
         */
        private String logoutDestination() {
                return "/signin?logout=true";
        }
}
