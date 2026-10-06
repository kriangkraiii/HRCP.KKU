package com.ecom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Selects how people sign in.
 *
 * <p>{@code dev} keeps the existing e-mail/password form so the system stays
 * usable on a laptop with no access to the university identity provider.
 * {@code production} turns that off entirely and leaves a single "Sign in with
 * KKU SSO" button.
 *
 * <p>The two are mutually exclusive on purpose. Shipping SSO while leaving the
 * password form reachable would keep every credential-stuffing path open and
 * make the SSO requirement cosmetic.
 */
@Component
public class AuthModeProperties {

    public static final String MODE_DEV = "dev";
    public static final String MODE_PRODUCTION = "production";

    private final String mode;

    public AuthModeProperties(@Value("${app.auth.mode:dev}") String mode) {
        this.mode = mode == null ? MODE_DEV : mode.trim().toLowerCase();
    }

    public String getMode() {
        return mode;
    }

    public boolean isSsoMode() {
        return MODE_PRODUCTION.equals(mode) || "sso".equals(mode);
    }

    /**
     * Exactly {@code dev} — not merely "not SSO". Developer-only tools such as the
     * test-data form filler key off this, so an unrecognised mode value never
     * switches them on.
     */
    public boolean isDevMode() {
        return MODE_DEV.equals(mode);
    }

    /** Local password login is available only outside SSO mode. */
    public boolean isPasswordLoginEnabled() {
        return !isSsoMode();
    }
}
