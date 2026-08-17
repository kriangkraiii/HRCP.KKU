package com.ecom.sso;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.external.model.FsFaculty;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Maps an SSO identity onto a local {@link UserDtls} row.
 *
 * <p>The rest of the application authorises on {@code UserDtls} — roles,
 * request ownership, storage quotas — so an SSO sign-in still needs one. First
 * login creates it from the faculty directory; later logins refresh the name
 * and leave everything else alone.
 *
 * <p>Provisioning happens only after {@link SsoAccessPolicy} has allowed the
 * address, so this class never has to decide who may enter.
 */
@Service
public class SsoUserProvisioner {

    private static final Logger log = LoggerFactory.getLogger(SsoUserProvisioner.class);

    private static final String ROLE_USER = "ROLE_USER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SsoUserProvisioner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserDtls provision(KkuSsoClient.SsoToken token, FsFaculty faculty) {
        String email = token.email().trim().toLowerCase();

        UserDtls user = userRepository.findByEmail(email);
        if (user == null) {
            user = createFrom(token, faculty, email);
            log.info("Provisioned a new local account for an SSO sign-in");
        } else {
            refresh(user, token, faculty);
        }

        user.setLastLoginDate(LocalDateTime.now());
        // An SSO account never signs in with a password, so the first-login
        // password wizard must not be triggered for it.
        user.setIsFirstLogin(false);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private UserDtls createFrom(KkuSsoClient.SsoToken token, FsFaculty faculty, String email) {
        UserDtls user = new UserDtls();
        user.setEmail(email);
        user.setRole(ROLE_USER);
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);

        // Local password login is not available to SSO accounts. A random,
        // un-recorded value keeps the column non-null without creating a
        // credential anyone could use.
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        applyIdentity(user, token, faculty);
        return user;
    }

    private void refresh(UserDtls user, KkuSsoClient.SsoToken token, FsFaculty faculty) {
        // Only the display name is refreshed. Role, enabled state and quotas are
        // local decisions that an upstream directory must not silently override.
        applyIdentity(user, token, faculty);
    }

    private void applyIdentity(UserDtls user, KkuSsoClient.SsoToken token, FsFaculty faculty) {
        if (faculty != null) {
            if (faculty.getFirstName() != null) {
                user.setFirstName(faculty.getFirstName());
            }
            if (faculty.getLastName() != null) {
                user.setLastName(faculty.getLastName());
            }
            if (faculty.getPrefix() != null) {
                user.setTitle(faculty.getPrefix());
            }
            if (faculty.getPositionTitle() != null) {
                user.setAcademicPosition(faculty.getPositionTitle());
            }
            if (faculty.getTel() != null && user.getMobileNumber() == null) {
                user.setMobileNumber(faculty.getTel());
            }
            return;
        }

        // No directory record (an allowlisted admin): fall back to what SSO gave us.
        if (token.firstName() != null) {
            user.setFirstName(token.firstName());
        }
        if (token.lastName() != null) {
            user.setLastName(token.lastName());
        }
    }
}
