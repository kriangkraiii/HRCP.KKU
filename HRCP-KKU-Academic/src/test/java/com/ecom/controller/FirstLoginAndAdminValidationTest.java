package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ConcurrentModel;

import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;
import com.ecom.service.DataRetentionService;
import com.ecom.service.ImageSyncAuditService;
import com.ecom.service.LoginAnnouncementService;
import com.ecom.service.ProfileImageStorage;
import com.ecom.service.UserService;
import com.ecom.service.impl.UserServiceImpl;
import com.ecom.util.CommonUtil;

import jakarta.servlet.http.HttpServletRequest;

@DisplayName("ความปลอดภัยของการตั้งรหัสผ่านครั้งแรกและการสร้างบัญชีแอดมิน")
class FirstLoginAndAdminValidationTest {

    private UserService userService;
    private AdminLogService adminLogService;
    private HomeController homeController;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        adminLogService = mock(AdminLogService.class);
        homeController = new HomeController(
                userService,
                mock(CommonUtil.class),
                mock(BCryptPasswordEncoder.class),
                adminLogService,
                mock(LoginAnnouncementService.class));
    }

    @Test
    @DisplayName("การขอ OTP ใหม่ด้วยอีเมลอื่นต้องล้างสถานะ otpVerified เดิม เพื่อป้องกันการสวมรอยเปลี่ยนรหัสผ่านข้ามบัญชี")
    void switchingOtpEmailClearsVerificationAndBlocksPasswordChange() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("otpEmail", "attacker@kku.ac.th");
        when(userService.verifyOtp("attacker@kku.ac.th", "123456")).thenReturn(true);

        String verifyRedirect = homeController.processVerifyOtp("123456", session);
        assertThat(verifyRedirect).isEqualTo("redirect:/first-login/set-password");
        assertThat(session.getAttribute("otpVerified")).isEqualTo(true);
        assertThat(session.getAttribute("otpVerifiedEmail")).isEqualTo("attacker@kku.ac.th");

        // ผู้โจมตียิง POST /first-login ซ้ำด้วยอีเมลเหยื่อ
        homeController.processFirstLogin("victim@kku.ac.th", session, new ConcurrentModel());
        assertThat(session.getAttribute("otpEmail")).isEqualTo("victim@kku.ac.th");
        assertThat(session.getAttribute("otpVerified")).isNull();
        assertThat(session.getAttribute("otpVerifiedEmail")).isNull();

        // พยายามข้ามไปตั้งรหัสผ่านให้เหยื่อต้องถูกปฏิเสธ
        String setPwdRedirect = homeController.processSetPassword("StrongPass1!", "StrongPass1!", session);
        assertThat(setPwdRedirect).isEqualTo("redirect:/first-login");
        verify(userService, never()).activateAccount(anyString(), anyString());
    }

    @Test
    @DisplayName("กรอก OTP ผิดครบ 5 ครั้งต้องล้างเซสชันและบังคับให้ขอรหัสใหม่")
    void bruteForceOtpAttemptsAreLockedOutAfterFiveFailures() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("otpEmail", "user@kku.ac.th");
        when(userService.verifyOtp("user@kku.ac.th", "000000")).thenReturn(false);

        for (int i = 1; i <= 4; i++) {
            String redirect = homeController.processVerifyOtp("000000", session);
            assertThat(redirect).isEqualTo("redirect:/first-login/verify-otp");
        }

        String fifthRedirect = homeController.processVerifyOtp("000000", session);
        assertThat(fifthRedirect).isEqualTo("redirect:/first-login");
        assertThat(session.getAttribute("otpEmail")).isNull();
        assertThat((String) session.getAttribute("errorMsg")).contains("เกินกำหนด");
    }

    @Test
    @DisplayName("activateAccount ต้องปฏิเสธการเปลี่ยนรหัสผ่านหากบัญชีได้ตั้งรหัสผ่านครั้งแรกไปแล้ว (isFirstLogin = false)")
    void activateAccountRefusesAlreadyActivatedUser() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                passwordEncoder,
                mock(CommonUtil.class),
                mock(AcademicRequestRepository.class),
                mock(PositionRequestRepository.class),
                mock(ProfileImageStorage.class));

        UserDtls existing = new UserDtls();
        existing.setEmail("active@kku.ac.th");
        existing.setPassword("old-hash");
        existing.setIsFirstLogin(false);
        when(userRepository.findByEmail("active@kku.ac.th")).thenReturn(existing);

        assertThatThrownBy(() -> service.activateAccount("active@kku.ac.th", "NewStrongPass1!"))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("AdminController.saveAdmin ต้องตรวจสอบรูปแบบอีเมล ชื่อ-นามสกุล และไฟล์รูปภาพก่อนบันทึก")
    void saveAdminValidatesEmailNameAndImageFile() throws Exception {
        ProfileImageStorage profileImageStorage = mock(ProfileImageStorage.class);
        AdminController adminController = new AdminController(
                mock(HttpServletRequest.class),
                userService,
                mock(CommonUtil.class),
                mock(PasswordEncoder.class),
                adminLogService,
                mock(ImageSyncAuditService.class),
                mock(DataRetentionService.class),
                profileImageStorage);

        MockHttpSession session = new MockHttpSession();
        MockMultipartFile emptyImg = new MockMultipartFile("img", new byte[0]);

        // 1. อีเมลไม่ถูกรูปแบบ
        UserDtls invalidEmailUser = new UserDtls();
        invalidEmailUser.setEmail("invalid-email");
        invalidEmailUser.setFirstName("สมชาย");
        invalidEmailUser.setLastName("ใจดี");
        assertThat(adminController.saveAdmin(invalidEmailUser, emptyImg, session))
                .isEqualTo("redirect:/admin/add-admin");
        assertThat(session.getAttribute("errorMsg")).isEqualTo("รูปแบบอีเมลไม่ถูกต้อง");
        verify(userService, never()).saveAdmin(any());

        // 2. ชื่อ-นามสกุลว่าง
        UserDtls blankNameUser = new UserDtls();
        blankNameUser.setEmail("admin2@kku.ac.th");
        blankNameUser.setFirstName(" ");
        blankNameUser.setLastName("");
        assertThat(adminController.saveAdmin(blankNameUser, emptyImg, session))
                .isEqualTo("redirect:/admin/add-admin");
        assertThat(session.getAttribute("errorMsg")).isEqualTo("กรุณากรอกชื่อและนามสกุล");
        verify(userService, never()).saveAdmin(any());

        // 3. ไฟล์รูปปลอม (นามสกุล .exe)
        UserDtls validUser = new UserDtls();
        validUser.setEmail("admin3@kku.ac.th");
        validUser.setFirstName("สมชาย");
        validUser.setLastName("ใจดี");
        MockMultipartFile badFile = new MockMultipartFile("img", "malware.exe", "application/octet-stream", new byte[] { 1, 2, 3 });
        assertThat(adminController.saveAdmin(validUser, badFile, session))
                .isEqualTo("redirect:/admin/add-admin");
        verify(userService, never()).saveAdmin(any());
    }
}

