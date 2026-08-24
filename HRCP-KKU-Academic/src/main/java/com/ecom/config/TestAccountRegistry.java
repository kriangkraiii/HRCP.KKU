package com.ecom.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The accounts used to exercise the system without involving real people.
 *
 * <p>Demonstrating the workflow means signing in as an applicant and as an
 * administrator and walking a request through it. Every step of that sends
 * notifications, and the recipients are looked up from the database — so a
 * rehearsal reaches the actual head of department and the actual dean. This
 * registry marks the rehearsal accounts so mail raised on their behalf can be
 * held back, while everything a genuine user does is delivered as normal.
 *
 * <p>Matching is by exact address. Matching whole domains would be convenient
 * but silently unreachable is a bad failure mode for anyone whose real address
 * happens to look like a test one.
 */
@Component
public class TestAccountRegistry {

    private static final Logger log = LoggerFactory.getLogger(TestAccountRegistry.class);

    /** Fallback for code reached before Spring has wired this bean. */
    private static final Set<String> BUILT_IN = Set.of("user@user.com", "admin@admin.com");

    private static volatile TestAccountRegistry instance;

    private final Set<String> addresses;

    public TestAccountRegistry(
            @Value("${app.notification.test-accounts:user@user.com,admin@admin.com}") String configured) {
        Set<String> parsed = new LinkedHashSet<>();
        if (configured != null) {
            Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .forEach(parsed::add);
        }
        this.addresses = parsed.isEmpty() ? BUILT_IN : Set.copyOf(parsed);
        instance = this;
        log.info("Notification test accounts (real email suppressed): {}", this.addresses);
    }

    /** The configured rehearsal addresses, lower-cased. */
    public Set<String> addresses() {
        return addresses;
    }

    /** Whether this address belongs to a rehearsal account. Blank is not one. */
    public boolean isTestAccount(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return addresses.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Whether the person who triggered the current request is a test account.
     *
     * <p>This is what stops a rehearsal from reaching outsiders: the recipient
     * of "a new request was submitted" is every administrator on file, most of
     * whom are real. The address being written to says nothing about that; who
     * set the action off does.
     *
     * <p>Returns false for scheduled jobs, which run with no authentication —
     * those are filtered by recipient alone.
     */
    public boolean isTestActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) {
            return false;
        }
        return isTestAccount(name);
    }

    /** Static view for {@code EmailTemplateHelper}, which callers use statically. */
    public static boolean isConfiguredTestAccount(String email) {
        TestAccountRegistry registry = instance;
        if (registry != null) {
            return registry.isTestAccount(email);
        }
        return email != null && BUILT_IN.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
