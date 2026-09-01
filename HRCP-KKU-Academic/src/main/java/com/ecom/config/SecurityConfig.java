package com.ecom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

                        if (code == null || code.isBlank() || alreadyRetried) {
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
                                                .requestMatchers("/", "/signin",
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
                                                .logoutSuccessHandler((request, response,
                                                                authentication) -> response
                                                                                .sendRedirect(logoutDestination()))
                                                .permitAll())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) -> {
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
        private String logoutDestination() {
                if (authProperties.isSsoMode() && ssoProperties.isConfigured()) {
                        return ssoProperties.logoutUrl();
                }
                return "/signin?logout=true";
        }
}
