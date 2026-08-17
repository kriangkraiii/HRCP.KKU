package com.ecom.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * 2FA is the user's own choice, and an SSO sign-in is not allowed to make that
 * choice for them — neither by switching it on for a brand new account nor by
 * quietly switching it off for an account that asked for it.
 */
class SsoUserProvisionerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SsoUserProvisioner provisioner = new SsoUserProvisioner(userRepository, passwordEncoder);

    private KkuSsoClient.SsoToken token() {
        return new KkuSsoClient.SsoToken("access-token", "somchai@kku.ac.th",
                "immutable-1", "สมชาย", "ใจดี", "emp-1");
    }

    private void repositoryEchoesSaves() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(UserDtls.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("บัญชีใหม่ที่สร้างจาก SSO ต้องยังไม่เปิด 2FA")
    void aNewlyProvisionedAccountHasTwoFactorOff() {
        repositoryEchoesSaves();
        when(userRepository.findByEmail("somchai@kku.ac.th")).thenReturn(null);

        UserDtls created = provisioner.provision(token(), null);

        assertThat(created.getTwoFactorEnabled())
                .as("2FA ต้องเป็นสิ่งที่ผู้ใช้เปิดเอง ไม่ใช่ค่าที่ระบบตั้งให้ตอนสร้างบัญชี")
                .isFalse();
    }

    @Test
    @DisplayName("ผู้ใช้ที่เปิด 2FA ไว้ ล็อกอิน SSO ครั้งถัดไปต้องไม่ถูกปิดให้")
    void anExistingChoiceToUseTwoFactorSurvivesTheNextSsoLogin() {
        repositoryEchoesSaves();

        UserDtls existing = new UserDtls();
        existing.setEmail("somchai@kku.ac.th");
        existing.setRole("ROLE_USER");
        existing.setIsEnable(true);
        existing.setTwoFactorEnabled(true);
        when(userRepository.findByEmail("somchai@kku.ac.th")).thenReturn(existing);

        UserDtls refreshed = provisioner.provision(token(), null);

        assertThat(refreshed.getTwoFactorEnabled()).isTrue();
    }
}
