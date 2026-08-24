package com.ecom.config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * The one place every outgoing email passes through.
 *
 * <p>{@code JavaMailSender} is injected into six separate services with eight
 * dispatch sites between them, each of which used to carry its own copy of the
 * "skip test accounts" check. One of them was missed, which is the failure mode
 * duplicated checks always have. Wrapping the sender itself means a service
 * added tomorrow is covered without anyone remembering to do anything.
 *
 * <p>Two rules apply, in order:
 * <ol>
 *   <li>If the person driving the request is a test account, nothing goes out
 *       at all. A rehearsal must not reach the real head of department merely
 *       because the notification happens to be addressed to them.</li>
 *   <li>Otherwise test-account recipients are dropped from the address list,
 *       and genuine recipients are delivered to as normal.</li>
 * </ol>
 */
public class GuardedJavaMailSender implements JavaMailSender {

    private static final Logger log = LoggerFactory.getLogger(GuardedJavaMailSender.class);

    private final JavaMailSender delegate;
    private final TestAccountRegistry testAccounts;

    public GuardedJavaMailSender(JavaMailSender delegate, TestAccountRegistry testAccounts) {
        this.delegate = delegate;
        this.testAccounts = testAccounts;
    }

    // ------------------------------------------------------------------
    // Message construction is passed straight through
    // ------------------------------------------------------------------

    @Override
    public MimeMessage createMimeMessage() {
        return delegate.createMimeMessage();
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return delegate.createMimeMessage(contentStream);
    }

    // ------------------------------------------------------------------
    // Dispatch is filtered
    // ------------------------------------------------------------------

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        List<MimeMessage> allowed = new ArrayList<>();
        for (MimeMessage message : mimeMessages) {
            if (permit(message)) {
                allowed.add(message);
            }
        }
        if (!allowed.isEmpty()) {
            delegate.send(allowed.toArray(new MimeMessage[0]));
        }
    }

    @Override
    public void send(MimeMessagePreparator... preparators) throws MailException {
        // Prepared messages are built by the caller's callback, so materialise
        // them here and put them through the same filter as everything else.
        List<MimeMessage> prepared = new ArrayList<>();
        for (MimeMessagePreparator preparator : preparators) {
            MimeMessage message = createMimeMessage();
            try {
                preparator.prepare(message);
            } catch (MailException e) {
                throw e;
            } catch (Exception e) {
                throw new MailPreparationException(e);
            }
            prepared.add(message);
        }
        send(prepared.toArray(new MimeMessage[0]));
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        if (blockedByActor("simple message")) {
            return;
        }
        List<SimpleMailMessage> allowed = new ArrayList<>();
        for (SimpleMailMessage message : simpleMessages) {
            String[] to = keepDeliverable(message.getTo());
            String[] cc = keepDeliverable(message.getCc());
            String[] bcc = keepDeliverable(message.getBcc());
            if (isEmpty(to) && isEmpty(cc) && isEmpty(bcc)) {
                log.info("[MAIL SUPPRESSED] every recipient is a test account (subject='{}')",
                        message.getSubject());
                continue;
            }
            message.setTo(to);
            message.setCc(cc);
            message.setBcc(bcc);
            allowed.add(message);
        }
        if (!allowed.isEmpty()) {
            delegate.send(allowed.toArray(new SimpleMailMessage[0]));
        }
    }

    // ------------------------------------------------------------------
    // Filtering
    // ------------------------------------------------------------------

    /** Whether this message may go out, stripping test recipients in passing. */
    private boolean permit(MimeMessage message) {
        if (blockedByActor(subjectOf(message))) {
            return false;
        }
        try {
            boolean anyRemaining = false;
            boolean anyDropped = false;
            for (Message.RecipientType type : List.of(Message.RecipientType.TO,
                    Message.RecipientType.CC, Message.RecipientType.BCC)) {

                Address[] current = message.getRecipients(type);
                if (current == null || current.length == 0) {
                    continue;
                }
                List<Address> keep = new ArrayList<>();
                for (Address address : current) {
                    if (testAccounts.isTestAccount(address.toString())) {
                        anyDropped = true;
                    } else {
                        keep.add(address);
                    }
                }
                if (keep.size() != current.length) {
                    message.setRecipients(type, keep.toArray(new Address[0]));
                }
                anyRemaining |= !keep.isEmpty();
            }
            if (!anyRemaining) {
                log.info("[MAIL SUPPRESSED] every recipient is a test account (subject='{}')",
                        subjectOf(message));
                return false;
            }
            if (anyDropped) {
                message.saveChanges();
            }
            return true;
        } catch (MessagingException e) {
            // Refusing to guess is safer than guessing wrong in either
            // direction, so let the real sender deal with the malformed message.
            log.warn("Could not inspect recipients, passing message through: {}", e.toString());
            return true;
        }
    }

    /** True when the current actor is a test account, so nothing may go out. */
    private boolean blockedByActor(String subject) {
        if (!testAccounts.isTestActor()) {
            return false;
        }
        log.info("[MAIL SUPPRESSED] triggered by a test account, no real email sent (subject='{}')",
                subject);
        return true;
    }

    private String[] keepDeliverable(String[] addresses) {
        if (addresses == null) {
            return null;
        }
        List<String> keep = new ArrayList<>();
        for (String address : addresses) {
            if (!testAccounts.isTestAccount(address)) {
                keep.add(address);
            }
        }
        return keep.toArray(new String[0]);
    }

    private static boolean isEmpty(String[] values) {
        return values == null || values.length == 0;
    }

    private static String subjectOf(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            return "(unknown)";
        }
    }
}
