package com.ecom.search.index;

import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.repository.AcademicAttachmentRepository;
import com.ecom.academic.repository.AcademicCommitteeMemberRepository;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.AdminFileRepository;
import com.ecom.academic.repository.PositionAttachmentRepository;
import com.ecom.academic.repository.PositionDocumentRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.repository.RequestStatusHistoryRepository;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.KkuRegulationDocRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.repository.NotificationRepository;
import com.ecom.repository.UserRepository;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;

/**
 * Reads a source row and flattens it into a search document.
 *
 * <p><b>Read-only, and that is the whole reason this is a separate class.</b>
 * Indexing loads requests, documents and users into a persistence context, and
 * if that context can flush, Hibernate will write those entities back when the
 * transaction commits — with whatever values they held when they were read.
 *
 * <p>That is not theoretical. Indexing runs asynchronously, after the change
 * that triggered it: load a request while another thread is midway through
 * changing its status, and the flush at commit puts the old status back. It
 * surfaced as a workflow test failing perhaps two runs in three, with a request
 * mysteriously reverted to DRAFT — the search index silently corrupting the
 * tables it exists only to mirror.
 *
 * <p>{@code readOnly = true} puts Hibernate in {@code FlushMode.MANUAL}, so
 * there is no flush to go wrong. Writes happen in {@link SearchIndexer}, in a
 * transaction that touches nothing but {@code search_document}.
 */
@Component
public class SearchDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(SearchDocumentLoader.class);

    private final SearchDocumentFactory factory;

    private final AcademicRequestRepository academicRequests;
    private final AcademicDocumentRepository academicDocuments;
    private final PositionRequestRepository positionRequests;
    private final PositionDocumentRepository positionDocuments;
    private final AcademicCommitteeMemberRepository committeeMembers;
    private final StaffMemberRepository staffMembers;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final AdminFileRepository adminFiles;
    private final KkuRegulationDocRepository regulationDocs;
    private final AcademicAttachmentRepository academicAttachments;
    private final PositionAttachmentRepository positionAttachments;
    private final RequestStatusHistoryRepository statusHistory;
    private final SignatureRequestRepository signatureRequests;
    private final ScopusPublicationRepository publications;

    public SearchDocumentLoader(SearchDocumentFactory factory,
            AcademicRequestRepository academicRequests,
            AcademicDocumentRepository academicDocuments,
            PositionRequestRepository positionRequests,
            PositionDocumentRepository positionDocuments,
            AcademicCommitteeMemberRepository committeeMembers,
            StaffMemberRepository staffMembers,
            UserRepository users,
            NotificationRepository notifications,
            AdminFileRepository adminFiles,
            KkuRegulationDocRepository regulationDocs,
            AcademicAttachmentRepository academicAttachments,
            PositionAttachmentRepository positionAttachments,
            RequestStatusHistoryRepository statusHistory,
            SignatureRequestRepository signatureRequests,
            ScopusPublicationRepository publications) {
        this.factory = factory;
        this.academicRequests = academicRequests;
        this.academicDocuments = academicDocuments;
        this.positionRequests = positionRequests;
        this.positionDocuments = positionDocuments;
        this.committeeMembers = committeeMembers;
        this.staffMembers = staffMembers;
        this.users = users;
        this.notifications = notifications;
        this.adminFiles = adminFiles;
        this.regulationDocs = regulationDocs;
        this.academicAttachments = academicAttachments;
        this.positionAttachments = positionAttachments;
        this.statusHistory = statusHistory;
        this.signatureRequests = signatureRequests;
        this.publications = publications;
    }

    /**
     * @return the flattened document — a fresh, unmanaged object — or null when
     *         the source row is gone or its type is not indexed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public SearchDocument load(SearchEntityType type, Long id) {
        return switch (type) {
            case ACADEMIC_REQUEST -> map(academicRequests.findById(id), factory::fromAcademicRequest);
            case ACADEMIC_DOCUMENT -> map(academicDocuments.findById(id), factory::fromAcademicDocument);
            case POSITION_REQUEST -> map(positionRequests.findById(id), factory::fromPositionRequest);
            case POSITION_DOCUMENT -> map(positionDocuments.findById(id), factory::fromPositionDocument);
            case COMMITTEE_MEMBER -> map(committeeMembers.findById(id), factory::fromCommitteeMember);
            case STAFF_MEMBER -> map(staffMembers.findById(id), factory::fromStaffMember);
            case SYSTEM_USER -> map(users.findById(id.intValue()), factory::fromUser);
            case NOTIFICATION -> map(notifications.findById(id), factory::fromNotification);
            case ADMIN_FILE -> map(adminFiles.findById(id), factory::fromAdminFile);
            case REGULATION_DOC -> map(regulationDocs.findById(id), factory::fromRegulationDoc);
            case ACADEMIC_ATTACHMENT ->
                map(academicAttachments.findById(id), factory::fromAcademicAttachment);
            case POSITION_ATTACHMENT ->
                map(positionAttachments.findById(id), factory::fromPositionAttachment);
            case STATUS_HISTORY -> map(statusHistory.findById(id), factory::fromStatusHistory);
            case SIGNATURE_REQUEST ->
                map(signatureRequests.findById(id), factory::fromSignatureRequest);
            case PUBLICATION -> publications.findById(id)
                    .map(p -> factory.fromPublication(p, localOwnerOf(p)))
                    .orElse(null);
            // Not wired to a source repository. The caller must leave existing
            // rows alone rather than treat this as "the row is gone".
            default -> {
                log.debug("ยังไม่ได้ต่อ {} เข้ากับตัวสร้าง index", type);
                yield null;
            }
        };
    }

    /**
     * The local account behind a harvested publication, if there is one.
     *
     * <p>Publications arrive keyed by the directory's {@code fs_user_id}, which
     * is not this application's user id. {@code StaffMember} already carries
     * that mirror key and its link to a local account, so it is the mapping the
     * rest of the system uses too.
     *
     * <p>Resolved at index time, so a professor who signs in for the first time
     * after their publications were harvested does not own them until the next
     * reconcile — which is nightly. Acceptable, and cheaper than re-resolving on
     * every search.
     */
    private Integer localOwnerOf(ScopusPublication publication) {
        Long fsUserId = publication.getFsUserId();
        if (fsUserId == null) {
            return null;
        }
        return staffMembers.findByFsUserId(fsUserId)
                .map(staff -> staff.getUser() == null ? null : staff.getUser().getId())
                .orElse(null);
    }

    /** Types this loader can actually build, as opposed to ones it ignores. */
    public boolean canLoad(SearchEntityType type) {
        return switch (type) {
            case ACADEMIC_REQUEST, ACADEMIC_DOCUMENT, POSITION_REQUEST, POSITION_DOCUMENT,
                    COMMITTEE_MEMBER, STAFF_MEMBER, SYSTEM_USER, NOTIFICATION,
                    ADMIN_FILE, REGULATION_DOC, ACADEMIC_ATTACHMENT, POSITION_ATTACHMENT,
                    STATUS_HISTORY, SIGNATURE_REQUEST, PUBLICATION ->
                true;
            default -> false;
        };
    }

    private static <T> SearchDocument map(Optional<T> source, Function<T, SearchDocument> build) {
        return source.map(build).orElse(null);
    }
}
