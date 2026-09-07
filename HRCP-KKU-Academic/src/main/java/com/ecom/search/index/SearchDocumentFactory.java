package com.ecom.search.index;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicCommitteeMember;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.AdminFile;
import com.ecom.academic.model.PositionAttachment;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.RequestStatusHistory;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.StaffMember;
import com.ecom.external.model.KkuRegulationDoc;
import com.ecom.external.model.ScopusPublication;
import com.ecom.model.Notification;
import com.ecom.model.UserDtls;
import com.ecom.search.model.ExtractionState;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;
import com.ecom.search.model.SearchVisibility;

/**
 * Turns a source row into the flattened document the search index stores.
 *
 * <p>One method per source type, each deciding four things: what text is
 * searchable, who may see it, where a hit links to, and how it is labelled.
 * Keeping those together per type is what stops the scoping drifting away from
 * the content — the failure mode of the old per-repository search, where each
 * new source was a fresh chance to forget the owner check.
 *
 * <p><b>Names are listed exhaustively.</b> Every people-bearing entity carries
 * paired Thai and {@code *_en} columns, and the bug this whole change starts
 * from was a query that looked at one name column out of nine. Here the keyword
 * field is built from all of them, so the same mistake cannot be made by
 * omission.
 */
@Component
public class SearchDocumentFactory {

    /** {@code AcademicAttachment.fileType} for a link rather than an upload. */
    private static final String LINK_TYPE = "LINK";

    /**
     * The only URL shapes allowed to become a result's destination.
     *
     * <p>Anchored at the start so {@code javascript:alert(1)//https://x} cannot
     * slip through on a substring match.
     */
    private static final java.util.regex.Pattern HTTP_URL =
            java.util.regex.Pattern.compile("^https?://", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final JsonFormTextExtractor formTextExtractor;

    public SearchDocumentFactory(JsonFormTextExtractor formTextExtractor) {
        this.formTextExtractor = formTextExtractor;
    }

    // ------------------------------------------------------------------
    // Phase 1 — teaching evaluation
    // ------------------------------------------------------------------

    public SearchDocument fromAcademicRequest(AcademicRequest r) {
        UserDtls applicant = r.getApplicant();
        String code = r.getRequestCode() != null ? r.getRequestCode() : "#" + r.getId();

        SearchDocument d = base(SearchEntityType.ACADEMIC_REQUEST, r.getId(), "MAIN");
        d.setTitle("คำร้องขอประเมินผลการสอน (" + code + ")");
        d.setSubtitle("สถานะ: " + thaiStatus(r));
        d.setKeywords(keywords(r.getRequestCode(), String.valueOf(r.getId()), allNamesOf(applicant)));
        d.setBody(r.getMeetingLocation());
        d.setStatus(r.getCurrentStatus() != null ? r.getCurrentStatus().name() : null);
        d.setBadge(thaiStatus(r));
        d.setBadgeClass("bg-primary");
        d.setOccurredAt(firstNonNull(r.getUpdatedAt(), r.getSubmissionDate(), r.getCreatedAt()));
        d.setSourceUpdatedAt(r.getUpdatedAt());
        scopeToApplicant(d, applicant, r.getId());
        return d;
    }

    public SearchDocument fromAcademicDocument(AcademicDocument doc) {
        AcademicRequest request = doc.getRequest();
        Long parentId = request != null ? request.getId() : null;

        SearchDocument d = base(SearchEntityType.ACADEMIC_DOCUMENT, doc.getId(), "MAIN");
        d.setTitle(label(doc.getDocumentLabel(), doc.getDocumentType()));
        d.setSubtitle(request != null && request.getRequestCode() != null
                ? "คำร้อง: " + request.getRequestCode()
                : null);
        d.setKeywords(keywords(
                request != null ? request.getRequestCode() : null,
                doc.getRevisionNote(),
                request != null ? allNamesOf(request.getApplicant()) : null));
        // The form answers — the single largest body of text in the system, and
        // the one the previous search never looked at.
        d.setBody(formTextExtractor.extract(doc.getJsonData()));
        d.setOccurredAt(firstNonNull(doc.getUpdatedAt(), doc.getCreatedAt()));
        d.setSourceUpdatedAt(doc.getUpdatedAt());
        d.setDeleted(Boolean.TRUE.equals(doc.getIsDeleted()));
        scopeToApplicant(d, request != null ? request.getApplicant() : null, parentId);
        return d;
    }

    // ------------------------------------------------------------------
    // Phase 2 — academic position request
    // ------------------------------------------------------------------

    public SearchDocument fromPositionRequest(PositionRequest r) {
        UserDtls applicant = r.getApplicant();
        String code = r.getRequestCode() != null ? r.getRequestCode() : "#" + r.getId();

        SearchDocument d = base(SearchEntityType.POSITION_REQUEST, r.getId(), "MAIN");
        d.setTitle("คำร้องขอกำหนดตำแหน่งทางวิชาการ (" + code + ")"
                + (isBlank(r.getTargetPosition()) ? "" : " " + r.getTargetPosition()));
        d.setSubtitle("สาขา: " + orDash(r.getMajor()) + " | สถานะ: " + thaiStatus(r));
        d.setKeywords(keywords(r.getRequestCode(), String.valueOf(r.getId()),
                r.getTargetPosition(), r.getMajor(), r.getMajorCode(),
                r.getSubMajor(), r.getSubMajorCode(), r.getEvaluationMethod(),
                allNamesOf(applicant)));
        d.setStatus(r.getCurrentStatus() != null ? r.getCurrentStatus().name() : null);
        d.setBadge(thaiStatus(r));
        d.setBadgeClass("bg-info");
        d.setOccurredAt(firstNonNull(r.getUpdatedAt(), r.getSubmissionDate(), r.getCreatedAt()));
        d.setSourceUpdatedAt(r.getUpdatedAt());
        scopeToApplicant(d, applicant, r.getId());
        return d;
    }

    public SearchDocument fromPositionDocument(PositionDocument doc) {
        PositionRequest request = doc.getRequest();
        Long parentId = request != null ? request.getId() : null;

        SearchDocument d = base(SearchEntityType.POSITION_DOCUMENT, doc.getId(), "MAIN");
        d.setTitle(label(doc.getDocumentLabel(), doc.getDocumentType()));
        d.setSubtitle(request != null && request.getRequestCode() != null
                ? "คำร้อง: " + request.getRequestCode()
                : null);
        d.setKeywords(keywords(
                request != null ? request.getRequestCode() : null,
                doc.getFilledBy(),
                request != null ? allNamesOf(request.getApplicant()) : null));
        d.setBody(formTextExtractor.extract(doc.getJsonData()));
        d.setOccurredAt(firstNonNull(doc.getUpdatedAt(), doc.getCreatedAt()));
        d.setSourceUpdatedAt(doc.getUpdatedAt());
        d.setDeleted(Boolean.TRUE.equals(doc.getIsDeleted()));
        scopeToApplicant(d, request != null ? request.getApplicant() : null, parentId);
        return d;
    }

    // ------------------------------------------------------------------
    // People
    // ------------------------------------------------------------------

    public SearchDocument fromCommitteeMember(AcademicCommitteeMember m) {
        SearchDocument d = base(SearchEntityType.COMMITTEE_MEMBER, m.getId(), "MAIN");
        d.setTitle(join(" ", m.getTitle(), m.getFirstName(), m.getLastName()));
        d.setSubtitle("สังกัด: " + orDash(m.getAffiliation()));
        d.setKeywords(keywords(m.getFirstName(), m.getLastName(), m.getFullName(),
                m.getTitleEn(), m.getFirstNameEn(), m.getLastNameEn(), m.getFullNameEn(),
                m.getAcademicPosition(), m.getAcademicPositionEn(),
                m.getAffiliation(), m.getAffiliationEn(),
                m.getExpertiseField(), m.getExpertiseFieldEn(),
                m.getEmail(), m.getPhoneNumber()));
        d.setBody(m.getExpertiseField());
        d.setStatus(m.getCommitteeType() != null ? m.getCommitteeType().name() : null);
        d.setBadge(Boolean.FALSE.equals(m.getIsActive()) ? "ไม่ใช้งาน" : "กรรมการ");
        d.setBadgeClass(Boolean.FALSE.equals(m.getIsActive()) ? "bg-secondary" : "bg-success");
        d.setOccurredAt(firstNonNull(m.getUpdatedAt(), m.getCreatedAt()));
        d.setSourceUpdatedAt(m.getUpdatedAt());
        d.setVisibility(SearchVisibility.ADMIN);
        d.setUrl(requireUrl(SearchEntityType.COMMITTEE_MEMBER, m.getId(), null));
        d.setAdminUrl(SearchEntityType.COMMITTEE_MEMBER.adminUrl(m.getId(), null));
        return d;
    }

    public SearchDocument fromStaffMember(StaffMember s) {
        SearchDocument d = base(SearchEntityType.STAFF_MEMBER, s.getId(), "MAIN");
        d.setTitle(join(" ", s.getAcademicTitle(), s.getFirstName(), s.getLastName()));
        d.setSubtitle("สังกัด: " + orDash(s.getDepartment()) + " | ประเภท: " + orDash(s.getStaffType()));
        d.setKeywords(keywords(s.getFirstName(), s.getLastName(), s.getFullName(),
                s.getFirstNameEn(), s.getLastNameEn(), s.getFullNameEn(),
                s.getAcademicTitle(), s.getAcademicTitleEn(),
                s.getDepartment(), s.getStaffType(), s.getStaffRole()));
        d.setStatus(s.getStaffRole());
        d.setBadge(Boolean.FALSE.equals(s.getIsActive()) ? "ไม่ใช้งาน" : "บุคลากร");
        d.setBadgeClass(Boolean.FALSE.equals(s.getIsActive()) ? "bg-secondary" : "bg-success");
        d.setOccurredAt(firstNonNull(s.getUpdatedAt(), s.getCreatedAt()));
        d.setSourceUpdatedAt(s.getUpdatedAt());
        d.setVisibility(SearchVisibility.ADMIN);
        d.setUrl(requireUrl(SearchEntityType.STAFF_MEMBER, s.getId(), null));
        d.setAdminUrl(SearchEntityType.STAFF_MEMBER.adminUrl(s.getId(), null));
        return d;
    }

    public SearchDocument fromUser(UserDtls u) {
        boolean isAdmin = "ROLE_ADMIN".equals(u.getRole());
        String roleThai = isAdmin ? "ผู้ดูแลระบบ" : "ผู้ยื่นคำร้อง";

        SearchDocument d = base(SearchEntityType.SYSTEM_USER, u.getId().longValue(), "MAIN");
        d.setTitle(join(" ", u.getTitle(), u.getFirstName(), u.getLastName()) + " (" + u.getEmail() + ")");
        d.setSubtitle("สิทธิ์: " + roleThai + " | โทร: " + orDash(u.getMobileNumber()));
        d.setKeywords(keywords(allNamesOf(u)));
        d.setStatus(u.getRole());
        d.setBadge(roleThai);
        d.setBadgeClass("bg-secondary");
        d.setVisibility(SearchVisibility.ADMIN);
        d.setUrl((isAdmin ? "/admin/users?type=2" : "/admin/users?type=1") + "#user-" + u.getId());
        d.setAdminUrl((isAdmin ? "/admin/users?type=2" : "/admin/users?type=1") + "#user-" + u.getId());
        return d;
    }

    // ------------------------------------------------------------------
    // Everything else
    // ------------------------------------------------------------------

    public SearchDocument fromNotification(Notification n) {
        SearchDocument d = base(SearchEntityType.NOTIFICATION, n.getId(), "MAIN");
        d.setTitle(n.getTitle() != null ? n.getTitle() : "การแจ้งเตือน");
        d.setSubtitle(n.getMessage());
        d.setBody(n.getMessage());
        d.setStatus(n.getType() != null ? n.getType().name() : null);
        d.setBadge("แจ้งเตือน");
        d.setBadgeClass("bg-warning text-dark");
        d.setOccurredAt(n.getCreatedAt());
        d.setSourceUpdatedAt(n.getCreatedAt());
        d.setDeleted(Boolean.TRUE.equals(n.getIsDeleted()));
        // OWNER, not OWNER_OR_ADMIN: an admin has their own notifications and
        // has no business reading anyone else's.
        d.setVisibility(SearchVisibility.OWNER);
        d.setOwnerUserId(n.getRecipient() != null ? n.getRecipient().getId() : null);
        d.setUrl(requireUrl(SearchEntityType.NOTIFICATION, n.getId(), null));
        d.setAdminUrl(SearchEntityType.NOTIFICATION.adminUrl(n.getId(), null));
        return d;
    }

    public SearchDocument fromAdminFile(AdminFile f) {
        SearchDocument d = base(SearchEntityType.ADMIN_FILE, f.getId(), "MAIN");
        d.setTitle(f.getOriginalFilename() != null ? f.getOriginalFilename() : "ไฟล์");
        d.setSubtitle("ขนาด: " + orDash(f.getFormattedSize()));
        d.setKeywords(keywords(f.getOriginalFilename(), f.getContentType(), f.getCreatedBy(),
                f.getFolder() != null ? f.getFolder().getName() : null));
        d.setOccurredAt(f.getCreatedAt());
        d.setSourceUpdatedAt(f.getCreatedAt());
        d.setDeleted(Boolean.TRUE.equals(f.getIsDeleted()));
        d.setBadge("Storage");
        d.setBadgeClass("bg-warning text-dark");
        d.setVisibility(SearchVisibility.ADMIN);
        d.setUrl(requireUrl(SearchEntityType.ADMIN_FILE, f.getId(), null));
        d.setAdminUrl(SearchEntityType.ADMIN_FILE.adminUrl(f.getId(), null));
        return d;
    }

    public SearchDocument fromRegulationDoc(KkuRegulationDoc doc) {
        SearchDocument d = base(SearchEntityType.REGULATION_DOC, doc.getId(), "MAIN");
        d.setTitle(doc.getTitle() != null ? doc.getTitle() : "เอกสาร");
        d.setSubtitle("หมวด: " + orDash(doc.getCategory()));
        d.setKeywords(keywords(doc.getCategory(), doc.getPublishedYear()));
        d.setStatus(doc.getCategory());
        d.setBadge(Boolean.TRUE.equals(doc.getIsNew()) ? "ใหม่" : "ข้อบังคับ");
        d.setBadgeClass(Boolean.TRUE.equals(doc.getIsNew()) ? "bg-danger" : "bg-primary");
        d.setOccurredAt(firstNonNull(doc.getUpdatedAt(), doc.getCreatedAt()));
        d.setSourceUpdatedAt(doc.getUpdatedAt());
        // Regulations are published to the whole university; there is nothing
        // to scope and everyone signed in may read them.
        d.setVisibility(SearchVisibility.PUBLIC);
        d.setUrl(requireUrl(SearchEntityType.REGULATION_DOC, doc.getId(), null));
        d.setAdminUrl(SearchEntityType.REGULATION_DOC.adminUrl(doc.getId(), null));
        return d;
    }

    // ------------------------------------------------------------------
    // Attachments — the only place an applicant-supplied URL enters the index
    // ------------------------------------------------------------------

    public SearchDocument fromAcademicAttachment(AcademicAttachment a) {
        AcademicRequest request = a.getRequest();
        SearchDocument d = base(SearchEntityType.ACADEMIC_ATTACHMENT, a.getId(), "MAIN");
        fillAttachment(d, a.getOriginalFilename(), a.getStoredFilePath(), a.getFileType(),
                request != null ? request.getRequestCode() : null,
                a.getUploadedAt());
        d.setDeleted(Boolean.TRUE.equals(a.getIsDeleted()));
        scopeToApplicant(d, request != null ? request.getApplicant() : null,
                request != null ? request.getId() : null);
        // scopeToApplicant sets the route; a genuine external link overrides it.
        applyExternalLink(d, a.getStoredFilePath(), a.getFileType());
        return d;
    }

    public SearchDocument fromPositionAttachment(PositionAttachment a) {
        PositionRequest request = a.getRequest();
        SearchDocument d = base(SearchEntityType.POSITION_ATTACHMENT, a.getId(), "MAIN");
        fillAttachment(d, a.getOriginalFilename(), a.getStoredFilePath(), a.getFileType(),
                request != null ? request.getRequestCode() : null,
                a.getUploadedAt());
        d.setDeleted(Boolean.TRUE.equals(a.getIsDeleted()));
        scopeToApplicant(d, request != null ? request.getApplicant() : null,
                request != null ? request.getId() : null);
        applyExternalLink(d, a.getStoredFilePath(), a.getFileType());
        return d;
    }

    private void fillAttachment(SearchDocument d, String filename, String path,
            String fileType, String requestCode, LocalDateTime uploadedAt) {
        d.setTitle(isBlank(filename) ? "ไฟล์แนบ" : filename);
        d.setSubtitle(isBlank(requestCode) ? null : "คำร้อง: " + requestCode);
        d.setKeywords(keywords(filename, requestCode, fileType));
        d.setStatus(fileType);
        d.setBadge(LINK_TYPE.equalsIgnoreCase(fileType) ? "ลิงก์" : "ไฟล์แนบ");
        d.setBadgeClass("bg-secondary");
        d.setOccurredAt(uploadedAt);
        d.setSourceUpdatedAt(uploadedAt);
        // The file's own text is pulled out later, by the extraction worker.
        d.setFilePath(LINK_TYPE.equalsIgnoreCase(fileType) ? null : path);
        d.setExtractionState(LINK_TYPE.equalsIgnoreCase(fileType) || isBlank(path)
                ? ExtractionState.NONE
                : ExtractionState.PENDING);
    }

    /**
     * Points a link attachment at its destination — but only if that
     * destination is really a web address.
     *
     * <p>{@code storedFilePath} is 2048 characters of whatever the applicant
     * typed when they attached a link (V20). Left unchecked it becomes the
     * {@code href} an administrator clicks, so {@code javascript:} and
     * {@code data:} would be script execution in their browser. The omnibox
     * screens this again at render time; both layers are cheap and the value
     * passes through too many hands to trust either alone.
     */
    private void applyExternalLink(SearchDocument d, String path, String fileType) {
        if (!LINK_TYPE.equalsIgnoreCase(fileType) || isBlank(path)) {
            return;
        }
        if (!HTTP_URL.matcher(path.trim()).find()) {
            // Keep the row — the filename is still worth finding — but send the
            // reader to the request rather than to an address like that.
            return;
        }
        d.setUrl(path.trim());
        d.setAdminUrl(path.trim());
        d.setExternal(true);
    }

    // ------------------------------------------------------------------
    // History, publications and signing
    // ------------------------------------------------------------------

    public SearchDocument fromStatusHistory(RequestStatusHistory h) {
        AcademicRequest request = h.getRequest();
        SearchDocument d = base(SearchEntityType.STATUS_HISTORY, h.getId(), "MAIN");
        d.setTitle(h.getNewStatus() != null
                ? "เปลี่ยนสถานะเป็น " + h.getNewStatus().getThaiLabel()
                : "บันทึกประวัติสถานะ");
        d.setSubtitle(request != null && request.getRequestCode() != null
                ? "คำร้อง: " + request.getRequestCode()
                : null);
        d.setKeywords(keywords(
                request != null ? request.getRequestCode() : null,
                h.getOldStatus() != null ? h.getOldStatus().getThaiLabel() : null,
                h.getNewStatus() != null ? h.getNewStatus().getThaiLabel() : null,
                h.getChangedBy() != null ? allNamesOf(h.getChangedBy()) : null));
        // The note is the only free text a person wrote here, and often the only
        // record of why something was sent back.
        d.setBody(h.getNote());
        d.setStatus(h.getNewStatus() != null ? h.getNewStatus().name() : null);
        d.setBadge("ประวัติ");
        d.setBadgeClass("bg-light text-dark border");
        d.setOccurredAt(h.getChangedAt());
        d.setSourceUpdatedAt(h.getChangedAt());
        scopeToApplicant(d, request != null ? request.getApplicant() : null,
                request != null ? request.getId() : null);
        return d;
    }

    /**
     * A harvested publication.
     *
     * @param localOwnerId the local account matching {@code fsUserId}, or null
     *                     when the professor has no account here yet — resolved
     *                     by the caller, because that mapping is by e-mail and
     *                     needs the user table
     */
    public SearchDocument fromPublication(ScopusPublication p, Integer localOwnerId) {
        SearchDocument d = base(SearchEntityType.PUBLICATION, p.getId(), "MAIN");
        d.setTitle(isBlank(p.getTitle()) ? "ผลงานตีพิมพ์" : p.getTitle());
        d.setSubtitle(join(" · ", p.getPublicationName(),
                p.getPublicationYear() == null ? null : String.valueOf(p.getPublicationYear())));
        d.setKeywords(keywords(p.getAuthorNames(), p.getAuthKeywords(), p.getDoi(),
                p.getPublicationName(), p.getDataSource(),
                p.getPublicationYear() == null ? null : String.valueOf(p.getPublicationYear())));
        d.setBody(p.getAbstractText());
        d.setStatus(p.getDataSource());
        d.setBadge(isBlank(p.getDataSource()) ? "ผลงาน" : p.getDataSource());
        d.setBadgeClass("bg-primary");
        d.setOccurredAt(p.getCoverDate() == null ? null : p.getCoverDate().toLocalDateTime());
        d.setSourceUpdatedAt(p.getSyncedAt());
        if (localOwnerId != null) {
            d.setVisibility(SearchVisibility.OWNER_OR_ADMIN);
            d.setOwnerUserId(localOwnerId);
        } else {
            // Nobody here owns it yet, so only an administrator should meet it.
            // The mapping is redone on every reconcile, so an account created
            // later picks its publications up that night.
            d.setVisibility(SearchVisibility.ADMIN);
        }
        d.setUrl(requireUrl(SearchEntityType.PUBLICATION, p.getId(), null));
        d.setAdminUrl(SearchEntityType.PUBLICATION.adminUrl(p.getId(), null));
        return d;
    }

    public SearchDocument fromSignatureRequest(SignatureRequest r) {
        SearchDocument d = base(SearchEntityType.SIGNATURE_REQUEST, r.getId(), "MAIN");
        d.setTitle(isBlank(r.getDocumentLabel()) ? "เอกสารลงนาม" : r.getDocumentLabel());
        d.setSubtitle(r.getStatus() != null ? "สถานะ: " + r.getStatus().name() : null);
        d.setKeywords(keywords(r.getVerificationCode(),
                r.getModule() != null ? r.getModule().name() : null,
                r.getInitiatedBy() != null ? allNamesOf(r.getInitiatedBy()) : null));
        d.setStatus(r.getStatus() != null ? r.getStatus().name() : null);
        d.setBadge("ลงนาม");
        d.setBadgeClass("bg-info");
        d.setOccurredAt(firstNonNull(r.getCompletedAt(), r.getCreatedAt()));
        d.setSourceUpdatedAt(firstNonNull(r.getCompletedAt(), r.getCreatedAt()));
        // Envelopes are staff-facing: the applicant sees the request, not the
        // circulation behind it.
        d.setVisibility(SearchVisibility.ADMIN);
        d.setUrl(requireUrl(SearchEntityType.SIGNATURE_REQUEST, r.getId(), null));
        d.setAdminUrl(SearchEntityType.SIGNATURE_REQUEST.adminUrl(r.getId(), null));
        return d;
    }

    // ------------------------------------------------------------------
    // Shared pieces
    // ------------------------------------------------------------------

    private SearchDocument base(SearchEntityType type, Long entityId, String docPart) {
        SearchDocument d = new SearchDocument();
        d.setEntityType(type);
        d.setEntityId(entityId);
        d.setDocPart(docPart);
        d.setCategory(type.getCategory());
        d.setIcon(type.getIcon());
        d.setWeight(type.getDefaultWeight());
        d.setIndexedAt(LocalDateTime.now());
        return d;
    }

    /**
     * An applicant sees their own; an admin sees everyone's.
     *
     * <p>A request whose applicant has somehow gone is indexed as admin-only
     * rather than dropped: an orphaned request is exactly the thing an
     * administrator needs to be able to find.
     */
    private void scopeToApplicant(SearchDocument d, UserDtls applicant, Long routeId) {
        if (applicant != null) {
            d.setVisibility(SearchVisibility.OWNER_OR_ADMIN);
            d.setOwnerUserId(applicant.getId());
        } else {
            d.setVisibility(SearchVisibility.ADMIN);
        }
        d.setUrl(requireUrl(d.getEntityType(), d.getEntityId(), routeId));
        d.setAdminUrl(d.getEntityType().adminUrl(d.getEntityId(), routeId));
    }

    /**
     * The url column is NOT NULL, and a result with no link is useless anyway.
     * Falls back to the admin route, then to the dashboard, rather than letting
     * a null reach the database and fail the whole index write.
     */
    private String requireUrl(SearchEntityType type, Long id, Long parentId) {
        String url = type.userUrl(id, parentId);
        if (url == null) {
            url = type.adminUrl(id, parentId);
        }
        return url != null ? url : "/";
    }

    /** Every name a person is known under, in both scripts. */
    private String allNamesOf(UserDtls u) {
        if (u == null) {
            return null;
        }
        return join(" ", u.getTitle(), u.getFirstName(), u.getLastName(),
                u.getFirstNameEn(), u.getLastNameEn(),
                u.getAcademicPosition(), u.getAcademicPositionEn(),
                u.getEmail(), u.getMobileNumber(), u.getApplicantId());
    }

    /** Joins the non-blank parts, dropping duplicates so the index stays lean. */
    private String keywords(String... parts) {
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            if (isBlank(part)) {
                continue;
            }
            for (String word : part.trim().split("\\s+")) {
                if (!word.isBlank()) {
                    seen.add(word);
                }
            }
        }
        return seen.isEmpty() ? null : String.join(" ", seen);
    }

    private static String join(String separator, String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (!isBlank(part)) {
                present.add(part.trim());
            }
        }
        return String.join(separator, present);
    }

    private static String label(String documentLabel, Integer documentType) {
        if (!isBlank(documentLabel)) {
            return documentLabel;
        }
        return "เอกสารฉบับที่ " + (documentType != null ? documentType : "-");
    }

    private static String thaiStatus(AcademicRequest r) {
        return r.getCurrentStatus() != null ? r.getCurrentStatus().getThaiLabel() : "-";
    }

    private static String thaiStatus(PositionRequest r) {
        return r.getCurrentStatus() != null ? r.getCurrentStatus().getThaiLabel() : "-";
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String orDash(String value) {
        return isBlank(value) ? "-" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
