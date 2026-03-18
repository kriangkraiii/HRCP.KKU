package com.ecom.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Autowired
        private RateLimitFilter rateLimitFilter;

        @Autowired
        @Lazy
        private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

        @Bean
        @Primary
        public AuthenticationSuccessHandler authenticationSuccessHandler() {
                return customAuthenticationSuccessHandler;
        }

        @Autowired
        @Lazy
        private AuthFailureHandlerImpl authenticationFailureHandler;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Autowired
        @Lazy
        private UserDetailsServiceImpl userDetailsServiceImpl;

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
        public SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider authenticationProvider)
                        throws Exception {
                http
                                // Rate limit filter runs before authentication
                                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                                // CSRF protection with CookieCsrfTokenRepository for AJAX and form-based POST
                                .csrf(csrf -> {
                                        // Spring Security 6 uses deferred tokens by default.
                                        // Force eager loading so the XSRF-TOKEN cookie is always set.
                                        var handler = new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler();
                                        handler.setCsrfRequestAttributeName(null); // force eager token resolution
                                        csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                                        .csrfTokenRequestHandler(handler)
                                                        .ignoringRequestMatchers(
                                                                        "/admin/toggle-image-mode",
                                                                        "/admin/activity-logs/export");
                                })
                                // Session management
                                .sessionManagement(session -> session
                                                .maximumSessions(1)
                                                .maxSessionsPreventsLogin(false))

                                .authenticationProvider(authenticationProvider)
                                .authorizeHttpRequests(authz -> authz
                                                .requestMatchers("/", "/signin",
                                                                "/static/**", "/css/**", "/js/**", "/img/**",
                                                                "/img/profile_img/**",
                                                                "/admin/css/**", "/admin/js/**", "/admin/img/**",
                                                                "/forgot-password", "/reset-password",
                                                                "/first-login", "/first-login/**",
                                                                "/2fa/**",
                                                                "/favicon.ico", "/error")
                                                .permitAll()
                                                // Lock down Actuator endpoints
                                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/user/**").hasRole("USER")
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                .loginPage("/signin")
                                                .loginProcessingUrl("/login")
                                                .usernameParameter("email")
                                                .passwordParameter("password")
                                                .successHandler(authenticationSuccessHandler())
                                                .failureHandler(authenticationFailureHandler)
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/signin?logout=true")
                                                .invalidateHttpSession(true)
                                                .deleteCookies("JSESSIONID")
                                                .clearAuthentication(true)
                                                .permitAll())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.sendRedirect("/signin?expired=true");
                                                }));
                return http.build();
        }
}
