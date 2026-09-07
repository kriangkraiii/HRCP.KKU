package com.ecom.search.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
import com.ecom.search.model.SearchEntityType;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * Notices that an indexed row changed, wherever in the application it happened.
 *
 * <p>Attached with {@code @EntityListeners} to each indexed entity rather than
 * calling an indexer from every service. Writes to these tables happen in more
 * than twenty places — the request services, the attachment upload paths, the
 * directory and publication sync jobs — and a rule that has to be remembered at
 * each of them is a rule that will be missed at the next one. Eight annotations
 * cover every write path there is, including ones not written yet.
 *
 * <p><b>Two things this must never do.</b> It must not touch the
 * {@code EntityManager}: a query from inside a lifecycle callback runs during
 * flush and can corrupt the flush in progress. And it must not put the entity
 * on the event — see {@link SearchReindexEvent}. Publishing the type and id is
 * all that is safe, and all that is needed.
 */
@Component
public class SearchIndexListener {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexListener.class);

    /**
     * An ordinary instance field, and it must stay one.
     *
     * <p>Spring Boot hands Hibernate a {@code SpringBeanContainer}, so this
     * listener is built as a bean of the context that owns the
     * {@code SessionFactory}, and each context gets its own instance wired to
     * its own publisher.
     *
     * <p>Holding the publisher statically instead looks harmless and breaks as
     * soon as two application contexts exist in one JVM — which is exactly what
     * the test suite does. The last context to start wins the static, every
     * earlier context's saves publish into it, and its listeners index against
     * the wrong database. The symptom is nasty: each test class passes alone and
     * fails in the suite.
     */
    private final ApplicationEventPublisher publisher;

    public SearchIndexListener(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostPersist
    @PostUpdate
    public void onChange(Object entity) {
        publish(entity, false);
    }

    @PostRemove
    public void onRemove(Object entity) {
        publish(entity, true);
    }

    private void publish(Object entity, boolean removed) {
        if (publisher == null) {
            // Possible in a bare persistence-unit test with no Spring context.
            return;
        }
        try {
            SearchEntityType type = typeOf(entity);
            Long id = idOf(entity);
            if (type == null || id == null) {
                return;
            }
            publisher.publishEvent(removed
                    ? SearchReindexEvent.removed(type, id)
                    : SearchReindexEvent.changed(type, id));
        } catch (RuntimeException e) {
            // A stale index is a much smaller problem than a save that fails.
            log.warn("แจ้ง reindex ไม่สำเร็จสำหรับ {}: {}",
                    entity.getClass().getSimpleName(), e.toString());
        }
    }

    private static SearchEntityType typeOf(Object entity) {
        if (entity instanceof AcademicRequest) return SearchEntityType.ACADEMIC_REQUEST;
        if (entity instanceof AcademicDocument) return SearchEntityType.ACADEMIC_DOCUMENT;
        if (entity instanceof PositionRequest) return SearchEntityType.POSITION_REQUEST;
        if (entity instanceof PositionDocument) return SearchEntityType.POSITION_DOCUMENT;
        if (entity instanceof AcademicCommitteeMember) return SearchEntityType.COMMITTEE_MEMBER;
        if (entity instanceof StaffMember) return SearchEntityType.STAFF_MEMBER;
        if (entity instanceof UserDtls) return SearchEntityType.SYSTEM_USER;
        if (entity instanceof Notification) return SearchEntityType.NOTIFICATION;
        if (entity instanceof AdminFile) return SearchEntityType.ADMIN_FILE;
        if (entity instanceof KkuRegulationDoc) return SearchEntityType.REGULATION_DOC;
        if (entity instanceof AcademicAttachment) return SearchEntityType.ACADEMIC_ATTACHMENT;
        if (entity instanceof PositionAttachment) return SearchEntityType.POSITION_ATTACHMENT;
        if (entity instanceof RequestStatusHistory) return SearchEntityType.STATUS_HISTORY;
        if (entity instanceof SignatureRequest) return SearchEntityType.SIGNATURE_REQUEST;
        if (entity instanceof ScopusPublication) return SearchEntityType.PUBLICATION;
        return null;
    }

    private static Long idOf(Object entity) {
        if (entity instanceof AcademicRequest e) return e.getId();
        if (entity instanceof AcademicDocument e) return e.getId();
        if (entity instanceof PositionRequest e) return e.getId();
        if (entity instanceof PositionDocument e) return e.getId();
        if (entity instanceof AcademicCommitteeMember e) return e.getId();
        if (entity instanceof StaffMember e) return e.getId();
        if (entity instanceof UserDtls e) return e.getId() == null ? null : e.getId().longValue();
        if (entity instanceof Notification e) return e.getId();
        if (entity instanceof AdminFile e) return e.getId();
        if (entity instanceof KkuRegulationDoc e) return e.getId();
        if (entity instanceof AcademicAttachment e) return e.getId();
        if (entity instanceof PositionAttachment e) return e.getId();
        if (entity instanceof RequestStatusHistory e) return e.getId();
        if (entity instanceof SignatureRequest e) return e.getId();
        if (entity instanceof ScopusPublication e) return e.getId();
        return null;
    }
}
