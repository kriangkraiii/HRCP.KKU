package com.ecom.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;

/**
 * A mail sender that records instead of sending, and refuses real addresses.
 *
 * <p>Two separate protections, because they guard against two different
 * mistakes:
 *
 * <ol>
 *   <li><b>Nothing leaves the JVM.</b> Every dispatch path in
 *       {@link JavaMailSenderImpl} — simple messages, MIME messages and
 *       preparators alike — funnels through {@code doSend}, which is overridden
 *       here to capture and return. No SMTP socket is ever opened, so a test run
 *       cannot mail anybody even if the host properties happen to point at a
 *       live server.</li>
 *   <li><b>No real address reaches a fixture.</b> Delivery being impossible does
 *       not make it acceptable for a test to seed a genuine professor's address:
 *       it ends up in assertion output, in CI logs, and in whatever someone
 *       copies out of them. So a recipient outside {@link #TEST_DOMAINS} fails
 *       the test loudly rather than being quietly recorded.</li>
 * </ol>
 *
 * <p>This is installed as the <em>delegate</em> the production
 * {@code GuardedMailSenderConfig} wraps, not as a replacement for it. That
 * ordering is deliberate: what lands here is what the real
 * {@code GuardedJavaMailSender} decided to let through, so the suppression rules
 * are exercised for real rather than mocked away.
 */
public class RecordingMailSender extends JavaMailSenderImpl {

    /**
     * Domains a test fixture may address.
     *
     * <p>{@code .invalid} and {@code .test} are reserved by RFC 2606 and can
     * never be registered, which makes them the correct choice for new fixtures.
     * {@code user.com} and {@code admin.com} are here only because the
     * application's own built-in accounts already use them
     * ({@code AdminInitializer}); prefer {@code .invalid} for anything new.
     */
    private static final Set<String> TEST_DOMAINS = Set.of(
            "example.com", "example.org", "example.net", "invalid", "test",
            "localhost", "user.com", "admin.com");

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    /** One captured message, flattened into the parts assertions care about. */
    public record Sent(List<String> to, String subject, String body) {

        public boolean wentTo(String address) {
            return to.stream().anyMatch(a -> a.equalsIgnoreCase(address));
        }

        public boolean subjectContains(String fragment) {
            return subject != null && subject.contains(fragment);
        }

        public boolean bodyContains(String fragment) {
            return body != null && body.contains(fragment);
        }
    }

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (MimeMessage message : mimeMessages) {
            sent.add(capture(message));
        }
        // Deliberately no super.doSend(...) — recording is the whole point.
    }

    private Sent capture(MimeMessage message) {
        List<String> recipients = new ArrayList<>();
        String subject = null;
        String body = null;
        try {
            for (Message.RecipientType type : List.of(Message.RecipientType.TO,
                    Message.RecipientType.CC, Message.RecipientType.BCC)) {
                Address[] addresses = message.getRecipients(type);
                if (addresses == null) {
                    continue;
                }
                for (Address address : addresses) {
                    recipients.add(address.toString());
                }
            }
            subject = message.getSubject();
            body = extractBody(message);
        } catch (Exception e) {
            throw new AssertionError("Could not read the captured message", e);
        }
        recipients.forEach(RecordingMailSender::rejectRealAddress);
        return new Sent(List.copyOf(recipients), subject, body);
    }

    /**
     * Reads the text of a message, however deeply it is wrapped.
     *
     * <p>Recursive because these messages nest: the templates attach inline
     * logos, so the HTML body ends up inside a {@code multipart/related} inside
     * a {@code multipart/mixed}. A single-level walk found only the wrapper and
     * reported an empty body — which looked exactly like "no e-mail was sent".
     *
     * <p>Best effort on purpose: a body that cannot be read is not a reason to
     * fail a test that is asserting on recipients.
     */
    private static String extractBody(MimeMessage message) {
        try {
            return textOf(message.getContent());
        } catch (Exception e) {
            return null;
        }
    }

    private static String textOf(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof jakarta.mail.Multipart multipart) {
            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                jakarta.mail.BodyPart part = multipart.getBodyPart(i);
                // Skip attachments: a base64 logo is not body text, and decoding
                // it only slows the test down.
                if (jakarta.mail.Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    continue;
                }
                String nested = textOf(part.getContent());
                if (nested != null) {
                    combined.append(nested);
                }
            }
            return combined.toString();
        }
        return content == null ? null : String.valueOf(content);
    }

    private static void rejectRealAddress(String address) {
        String bare = address;
        int open = bare.indexOf('<');
        if (open >= 0) {
            // "Somchai <somchai@kku.ac.th>" — the display name is not the address.
            bare = bare.substring(open + 1, bare.indexOf('>', open) < 0 ? bare.length() : bare.indexOf('>', open));
        }
        int at = bare.lastIndexOf('@');
        if (at < 0) {
            return;
        }
        String domain = bare.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        String tld = domain.contains(".") ? domain.substring(domain.lastIndexOf('.') + 1) : domain;
        if (TEST_DOMAINS.contains(domain) || TEST_DOMAINS.contains(tld)) {
            return;
        }
        throw new AssertionError("""
                A test addressed a message to '%s', which is outside the reserved test domains %s.

                Nothing was sent — this sender never opens a socket — but a fixture must not carry a
                real person's address. Use an RFC 2606 reserved domain instead, e.g. \
                somchai@example.invalid.""".formatted(address, TEST_DOMAINS));
    }

    // ------------------------------------------------------------------
    // Assertions
    // ------------------------------------------------------------------

    /** Every message captured so far, oldest first. */
    public List<Sent> captured() {
        return List.copyOf(sent);
    }

    public List<Sent> to(String address) {
        return sent.stream().filter(s -> s.wentTo(address)).toList();
    }

    public int count() {
        return sent.size();
    }

    public void clear() {
        sent.clear();
    }
}
