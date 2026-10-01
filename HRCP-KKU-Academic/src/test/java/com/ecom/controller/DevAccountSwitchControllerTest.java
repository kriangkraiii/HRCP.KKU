package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.ecom.config.AuthModeProperties;
import com.ecom.config.TestAccountRegistry;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.DevAccountSwitchService;
import com.ecom.service.SignInService;

class DevAccountSwitchControllerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SignInService signInService = mock(SignInService.class);
    private final TestAccountRegistry testAccounts = new TestAccountRegistry("user@user.com,admin@admin.com");

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Principal admin = () -> "admin@admin.com";

    private UserDtls adminUser;
    private UserDtls testUser;

    @BeforeEach
    void setUp() {
        adminUser = new UserDtls();
        adminUser.setEmail("admin@admin.com");
        adminUser.setRole("ROLE_ADMIN");
        adminUser.setIsEnable(true);

        testUser = new UserDtls();
        testUser.setEmail("user@user.com");
        testUser.setRole("ROLE_USER");
        testUser.setIsEnable(true);

        when(userRepository.findByEmail("admin@admin.com")).thenReturn(adminUser);
        when(userRepository.findByEmailIgnoreCase("user@user.com")).thenReturn(testUser);
        when(signInService.landingPageFor(any())).thenReturn("/admin/academic/dashboard");
        when(signInService.completeSignIn(any(), any(), eq(testUser), any(), any()))
                .thenReturn("/user/academic/dashboard");
    }

    private DevAccountSwitchController controller(String mode) {
        var switcher = new DevAccountSwitchService(new AuthModeProperties(mode), testAccounts, userRepository);
        return new DevAccountSwitchController(switcher, signInService, userRepository);
    }

    @Test
    @DisplayName("โหมด dev: แอดมินสลับเข้าบัญชีทดสอบได้โดยไม่ต้องใช้รหัสผ่าน")
    void adminSwitchesIntoATestAccount() {
        String view = controller("dev").switchAccount("user@user.com", admin, request, response,
                new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/user/academic/dashboard");
        verify(signInService).completeSignIn(any(), any(), eq(testUser),
                eq(SignInService.Method.DEV_SWITCH), isNull());
        assertThat(request.getSession().getAttribute(DevAccountSwitchService.SESSION_ORIGINAL_EMAIL))
                .isEqualTo("admin@admin.com");
    }

    @Test
    @DisplayName("โหมด production: endpoint ตอบ 404 และไม่มีการล็อกอินเกิดขึ้น")
    void notFoundUnderSso() {
        assertThatThrownBy(() -> controller("production").switchAccount("user@user.com", admin, request,
                response, new RedirectAttributesModelMap()))
                .isInstanceOf(ResponseStatusException.class);
        verify(signInService, never()).completeSignIn(any(), any(), any(), any(), any());
    }
}
