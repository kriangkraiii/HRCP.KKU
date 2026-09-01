package com.ecom.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestPublication;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureKind;
import com.ecom.academic.model.UserSignature;
import com.ecom.academic.service.UserSignatureService;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionDocumentRepository;
import com.ecom.academic.repository.PositionRequestPublicationRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.repository.UserRepository;

/**
 * Builds the people, requests, documents and publications the suite needs.
 *
 * <p>Exists so that a test reads as the scenario it is describing — "an
 * applicant whose teaching evaluation passed, holding two Scopus papers" —
 * rather than thirty lines of setters. Every fixture address uses a reserved
 * {@code .invalid} domain; see {@link RecordingMailSender} for why that matters.
 *
 * <p>Registered as a {@code @TestComponent} on the shared context, so
 * {@link #reset()} is what keeps tests independent rather than a fresh database
 * per class.
 */
@TestComponent
public class TestDataFactory {

    private static final Logger log = LoggerFactory.getLogger(TestDataFactory.class);

    /** Reserved by RFC 2606: can never resolve, can never belong to anyone. */
    public static final String DOMAIN = "example.invalid";

    public static final String APPLICANT_EMAIL = "somchai@" + DOMAIN;
    public static final String OTHER_APPLICANT_EMAIL = "malee@" + DOMAIN;
    public static final String ADMIN_EMAIL = "staff@" + DOMAIN;
    public static final String PASSWORD = "Test-Passw0rd!";

    private final UserRepository userRepository;
    private final AcademicRequestRepository academicRequests;
    private final AcademicDocumentRepository academicDocuments;
    private final PositionRequestRepository positionRequests;
    private final PositionDocumentRepository positionDocuments;
    private final FsFacultyRepository faculties;
    private final ScopusPublicationRepository publications;
    private final PositionRequestPublicationRepository publicationLinks;
    private final NotificationRepository notifications;
    private final JdbcTemplate jdbc;
    private final UserSignatureService signatureService;
    private final PasswordEncoder passwordEncoder;

    public TestDataFactory(UserRepository userRepository,
            AcademicRequestRepository academicRequests,
            AcademicDocumentRepository academicDocuments,
            PositionRequestRepository positionRequests,
            PositionDocumentRepository positionDocuments,
            FsFacultyRepository faculties,
            ScopusPublicationRepository publications,
            PositionRequestPublicationRepository publicationLinks,
            NotificationRepository notifications,
            JdbcTemplate jdbc,
            UserSignatureService signatureService,
            PasswordEncoder passwordEncoder) {
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.signatureService = signatureService;
        this.userRepository = userRepository;
        this.academicRequests = academicRequests;
        this.academicDocuments = academicDocuments;
        this.positionRequests = positionRequests;
        this.positionDocuments = positionDocuments;
        this.faculties = faculties;
        this.publications = publications;
        this.publicationLinks = publicationLinks;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Clears everything a test may have created, between tests.
     *
     * <p>Done in SQL, in dependency order, rather than through the repositories.
     * A request has six or seven child tables hanging off it — status history,
     * edit logs, attachments, signature envelopes and their steps and audit
     * events — and deleting the parent while any of them still points at it
     * fails on a foreign key. Naming the tables here means adding a child table
     * later shows up as one line to add, instead of an opaque integrity error in
     * whichever test happens to run second.
     *
     * <p>Accounts seeded by {@code AdminInitializer} are left alone: they belong
     * to the application, not to any one test.
     */
    @Transactional
    public void reset() {
        // Which of the tables below this schema actually has.
        //
        // Asked up front rather than discovered by letting a DELETE fail: on
        // PostgreSQL the first failed statement aborts the whole transaction and
        // every statement after it fails too ("current transaction is aborted"),
        // so the catch-and-continue that works on H2 wipes out the rest of the
        // cleanup. Both engines answer this query.
        Set<String> present = new HashSet<>(jdbc.queryForList(
                "SELECT LOWER(table_name) FROM information_schema.tables", String.class));

        for (String table : List.of(
                // Signature workflow: events → steps → envelopes
                "signature_audit_event", "signature_step", "signature_request",
                // Position side: children → request
                "position_document_edit_log", "position_attachment",
                "position_status_history", "position_document",
                "position_request_publication", "position_request",
                // Academic side: children → request
                "academic_document_edit_log", "academic_attachment",
                "request_status_history", "academic_document", "academic_request",
                // Standalone
                "user_signature", "notifications", "scopus_publication", "fs_faculty")) {
            if (present.contains(table)) {
                jdbc.execute("DELETE FROM " + table);
            } else {
                // Not every schema has every table — the suite runs against both
                // the H2 and the PostgreSQL builds.
                log.debug("Skipping cleanup of {}: not in this schema", table);
            }
        }
        userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().endsWith("@" + DOMAIN))
                .forEach(userRepository::delete);
    }

    // ------------------------------------------------------------------
    // People
    // ------------------------------------------------------------------

    public UserDtls applicant() {
        return user(APPLICANT_EMAIL, "สมชาย", "ใจดี", "ROLE_USER");
    }

    public UserDtls otherApplicant() {
        return user(OTHER_APPLICANT_EMAIL, "มาลี", "ตั้งใจ", "ROLE_USER");
    }

    public UserDtls admin() {
        return user(ADMIN_EMAIL, "จิราภรณ์", "หอมอ่อน", "ROLE_ADMIN");
    }

    public UserDtls user(String email, String firstName, String lastName, String role) {
        UserDtls existing = userRepository.findByEmail(email);
        if (existing != null) {
            return existing;
        }
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setRole(role);
        u.setPassword(passwordEncoder.encode(PASSWORD));
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        u.setFailedAttempt(0);
        u.setIsFirstLogin(false);
        u.setEmailNotificationEnabled(true);
        u.setAcademicPosition("อาจารย์");
        u.setCreatedDate(new Date());
        return userRepository.save(u);
    }

    /** Persists a change a test made to an evaluation it already holds. */
    public AcademicRequest saveEvaluation(AcademicRequest request) {
        return academicRequests.save(request);
    }

    /** Persists a change a test made to a user it already holds. */
    public UserDtls saveUser(UserDtls user) {
        return userRepository.save(user);
    }

    // ------------------------------------------------------------------
    // Phase 1 — teaching evaluation
    // ------------------------------------------------------------------

    public AcademicRequest evaluation(UserDtls applicant, RequestStatus status) {
        AcademicRequest r = new AcademicRequest();
        r.setApplicant(applicant);
        r.setCurrentStatus(status);
        if (status != RequestStatus.DRAFT) {
            r.setSubmissionDate(LocalDateTime.now().minusDays(30));
        }
        r = academicRequests.save(r);
        r.generateRequestCode();
        return academicRequests.save(r);
    }

    /**
     * A finished evaluation with the document 8 the position flow reads.
     *
     * @param expirationDate value for the {@code expiration_date} key, exactly as
     *                       a real document 8 would carry it — Thai month names
     *                       included, which is the case {@code isExpired} gets
     *                       wrong (GAP-21). Pass null to omit the key.
     */
    public AcademicRequest completedEvaluation(UserDtls applicant, RequestStatus status,
            String expirationDate) {
        AcademicRequest r = evaluation(applicant, status);
        String json = expirationDate == null
                ? "{\"evaluation_result\":\"ผ่าน\"}"
                : "{\"evaluation_result\":\"ผ่าน\",\"expiration_date\":\"" + expirationDate + "\"}";
        academicDocument(r, 8, json);
        return r;
    }

    public AcademicDocument academicDocument(AcademicRequest request, int type, String json) {
        AcademicDocument d = new AcademicDocument();
        d.setRequest(request);
        d.setDocumentType(type);
        d.setCopyNumber(0);
        d.setJsonData(json);
        d.setIsDraft(false);
        d.setDocumentLabel("เอกสารทดสอบที่ " + type);
        return academicDocuments.save(d);
    }

    // ------------------------------------------------------------------
    // Phase 2 — position request
    // ------------------------------------------------------------------

    public PositionRequest positionRequest(UserDtls applicant, PositionRequestStatus status,
            AcademicRequest linkedEvaluation) {
        PositionRequest r = new PositionRequest();
        r.setApplicant(applicant);
        r.setCurrentStatus(status);
        r.setLinkedEvaluation(linkedEvaluation);
        if (status != PositionRequestStatus.DRAFT) {
            r.setSubmissionDate(LocalDateTime.now());
        }
        r = positionRequests.save(r);
        r.setRequestCode("KKU-POS-TEST-" + r.getId());
        return positionRequests.save(r);
    }

    public PositionDocument positionDocument(PositionRequest request, int type, String json) {
        PositionDocument d = new PositionDocument();
        d.setRequest(request);
        d.setDocumentType(type);
        d.setCopyNumber(0);
        d.setJsonData(json);
        d.setIsDraft(false);
        d.setDocumentLabel("เอกสารตำแหน่งทดสอบที่ " + type);
        return positionDocuments.save(d);
    }

    // ------------------------------------------------------------------
    // Signatures
    // ------------------------------------------------------------------

    /**
     * Gives someone a saved signature, the way drawing one on the canvas would.
     *
     * <p>The image is generated rather than checked in: the storage layer
     * decodes and re-encodes the PNG and records its dimensions, so it has to be
     * a real image, but nothing about this test depends on what it looks like.
     */
    public UserSignature signatureFor(UserDtls owner) {
        UserSignatureService.SaveResult result = signatureService.create(owner,
                "data:image/png;base64," + tinyPngBase64(),
                SignatureKind.DRAW, "ลายเซ็นทดสอบ", null, null, true);
        if (!result.ok()) {
            throw new IllegalStateException("สร้างลายเซ็นทดสอบไม่สำเร็จ: " + result.error());
        }
        return result.signature();
    }

    private static String tinyPngBase64() {
        try {
            BufferedImage image = new BufferedImage(120, 48, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.BLACK);
            g.drawLine(8, 40, 112, 8);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("สร้างรูปลายเซ็นทดสอบไม่สำเร็จ", e);
        }
    }

    // ------------------------------------------------------------------
    // Publications
    // ------------------------------------------------------------------

    /**
     * Links a local account to an upstream faculty record.
     *
     * <p>E-mail is the only identifier the two systems share, and the upstream
     * feed is known to send addresses with a trailing space — so that is what is
     * stored here, to keep the normalising query honest.
     */
    public FsFaculty faculty(long fsUserId, String email) {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(fsUserId);
        f.setEmail(email + " ");
        f.setScopusId("scopus-" + fsUserId);
        f.setSyncedAt(LocalDateTime.now());
        return faculties.save(f);
    }

    /**
     * Records that a position request puts a publication forward — the same row
     * saving เอกสารที่ 1 with a Scopus id in it would write.
     *
     * <p>Goes straight to the table rather than through the form so that the rule
     * tests state the rule and nothing else. The path from picker to link row has
     * its own test ({@code ResearchReuseLockSpec.savingTheFormRecordsTheLink}).
     */
    public PositionRequestPublication recordPublicationUse(PositionRequest request,
            ScopusPublication publication) {
        return publicationLinks.save(new PositionRequestPublication(
                request, publication.getId(), 1, 1));
    }

    /**
     * A publication as the multi-source harvest writes them (V14): no Scopus
     * {@code eid}, no citation count, and a {@code dataSource} naming where it
     * came from.
     *
     * <p>Worth its own factory because those three differences are exactly what
     * could quietly break the picker — it was built when every row came from
     * Scopus and carried all three.
     */
    public ScopusPublication harvestedPublication(long fsUserId, String title, int year, String source) {
        ScopusPublication p = new ScopusPublication();
        p.setFsUserId(fsUserId);
        p.setTitle(title);
        p.setPublicationName("Journal of Multi-source Testing");
        p.setPublicationYear(year);
        p.setAuthorNames("Somchai J. | Malee T.");
        p.setDoi("10.1234/" + Math.abs(title.hashCode()));
        p.setDataSource(source);
        p.setExternalId("ext-" + Math.abs(title.hashCode()));
        p.setSyncedAt(LocalDateTime.now());
        return publications.save(p);
    }

    public ScopusPublication publication(long fsUserId, String title, int year, int citedBy) {
        ScopusPublication p = new ScopusPublication();
        p.setFsUserId(fsUserId);
        p.setEid("2-s2.0-" + Math.abs(title.hashCode()));
        p.setTitle(title);
        p.setPublicationName("Journal of Testing");
        p.setPublicationYear(year);
        p.setCitedBy(citedBy);
        p.setDoi("10.0000/" + Math.abs(title.hashCode()));
        p.setAuthorNames("Somchai J.");
        p.setSyncedAt(LocalDateTime.now());
        return publications.save(p);
    }
}
