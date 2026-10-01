package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.ecom.config.AuthModeProperties;
import com.ecom.config.TestAccountRegistry;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * The switch skips the password, so what it refuses is the point: nothing
 * under SSO, nothing for a non-admin, and nothing but test accounts.
 */
class DevAccountSwitchServiceTest {

    private static final String REAL_ADMIN = "somchai@kku.ac.th";
    private static final String REAL_USER = "somying@kku.ac.th";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TestAccountRegistry testAccounts = new TestAccountRegistry("user@user.com,admin@admin.com");
    private final MockHttpSession session = new MockHttpSession();

    private UserDtls realAdmin;
    private UserDtls realUser;
    private UserDtls testUser;
    private UserDtls testAdmin;

    @BeforeEach
    void setUp() {
        realAdmin = user(REAL_ADMIN, "ROLE_ADMIN");
        realUser = user(REAL_USER, "ROLE_USER");
        testUser = user("user@user.com", "ROLE_USER");
        testAdmin = user("admin@admin.com", "ROLE_ADMIN");
    }

    private UserDtls user(String email, String role) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setName(email);
        u.setRole(role);
        u.setIsEnable(true);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(u);
        return u;
    }

    private DevAccountSwitchService service(String mode) {
        return new DevAccountSwitchService(new AuthModeProperties(mode), testAccounts, userRepository);
    }

    @Test
    @DisplayName("โหมด production ต้องไม่มีเครื่องมือสลับบัญชีเลย แม้เป็นแอดมิน")
    void goneUnderSso() {
        DevAccountSwitchService switcher = service("production");

        assertThat(switcher.isAvailable(realAdmin, session)).isFalse();
        assertThat(switcher.resolveTarget(realAdmin, session, "user@user.com")).isEmpty();
    }

    @Test
    @DisplayName("ผู้ใช้ทั่วไปที่ไม่ได้สลับมาจากแอดมิน ใช้เครื่องมือไม่ได้")
    void notForAnOrdinaryUser() {
        DevAccountSwitchService switcher = service("dev");

        assertThat(switcher.isAvailable(realUser, session)).isFalse();
        assertThat(switcher.resolveTarget(realUser, session, "admin@admin.com")).isEmpty();
    }

    @Test
    @DisplayName("แอดมินสลับเข้าบัญชีทดสอบได้ แต่เข้าบัญชีจริงของคนอื่นไม่ได้")
    void onlyTestAccountsCanBeEntered() {
        DevAccountSwitchService switcher = service("dev");

        assertThat(switcher.resolveTarget(realAdmin, session, " user@user.com ")).contains(testUser);
        assertThat(switcher.resolveTarget(realAdmin, session, REAL_USER)).isEmpty();
    }

    @Test
    @DisplayName("บัญชีที่อีเมลขึ้นต้นด้วย test นับเป็นบัญชีทดสอบ สลับเข้าได้และขึ้นในรายการ")
    void testPrefixedAccountsCount() {
        UserDtls testDean = user("test.dean@kku.ac.th", "ROLE_USER");
        when(userRepository.findByEmailStartingWithIgnoreCase("test")).thenReturn(java.util.List.of(testDean));
        DevAccountSwitchService switcher = service("dev");

        assertThat(switcher.resolveTarget(realAdmin, session, "test.dean@kku.ac.th")).contains(testDean);
        assertThat(switcher.options(realAdmin, session))
                .extracting(DevAccountSwitchService.Option::email)
                .contains("test.dean@kku.ac.th");
    }

    @Test
    @DisplayName("บัญชีทดสอบที่ถูกปิดใช้งาน สลับเข้าไม่ได้")
    void disabledAccountIsRefused() {
        testUser.setIsEnable(false);

        assertThat(service("dev").resolveTarget(realAdmin, session, "user@user.com")).isEmpty();
    }

    @Test
    @DisplayName("สลับไปบัญชีผู้ใช้แล้ว ยังสลับต่อและกลับบัญชีแอดมินเดิมได้ แม้บัญชีเดิมไม่ใช่บัญชีทดสอบ")
    void theWayBackStaysOpen() {
        DevAccountSwitchService switcher = service("dev");

        switcher.rememberOrigin(session, realAdmin, testUser);

        assertThat(switcher.originalEmail(session)).isEqualTo(REAL_ADMIN);
        assertThat(switcher.isAvailable(testUser, session)).isTrue();
        assertThat(switcher.resolveTarget(testUser, session, "admin@admin.com")).contains(testAdmin);
        assertThat(switcher.resolveTarget(testUser, session, REAL_ADMIN)).contains(realAdmin);
        assertThat(switcher.options(testUser, session))
                .extracting(DevAccountSwitchService.Option::email)
                .containsExactlyInAnyOrder("admin@admin.com", REAL_ADMIN, "user@user.com");
    }

    @Test
    @DisplayName("สลับต่อหลายทอด ต้องจำแอดมินคนแรก และกลับถึงบ้านแล้วล้างสถานะ")
    void originIsTheFirstAdminAndClearsOnReturn() {
        DevAccountSwitchService switcher = service("dev");

        switcher.rememberOrigin(session, realAdmin, testUser);
        switcher.rememberOrigin(session, testUser, testAdmin);
        assertThat(switcher.originalEmail(session)).isEqualTo(REAL_ADMIN);

        switcher.rememberOrigin(session, testAdmin, realAdmin);
        assertThat(switcher.originalEmail(session)).isNull();
    }
}
