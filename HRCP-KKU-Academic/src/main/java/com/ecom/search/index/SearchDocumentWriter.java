package com.ecom.search.index;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.search.model.ExtractionState;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;
import com.ecom.search.repository.SearchDocumentRepository;

/**
 * The write half of indexing, and the only code that touches
 * {@code search_document}.
 *
 * <p><b>A separate bean from {@link SearchIndexer}, deliberately.</b> Spring's
 * {@code @Transactional} is applied by a proxy, so a call from one method of a
 * bean to another on the same bean does not go through it — the annotation is
 * silently ignored. That happened here: an {@code upsert} marked
 * {@code REQUIRES_NEW} but invoked internally ran with no transaction at all,
 * leaving its read and its write unable to see each other as one operation.
 * Splitting the class is what makes the annotation real.
 */
@Component
public class SearchDocumentWriter {

    private final SearchDocumentRepository index;

    public SearchDocumentWriter(SearchDocumentRepository index) {
        this.index = index;
    }

    /**
     * Writes the document, skipping the UPDATE when its text is unchanged.
     *
     * <p>The hash matters more than it looks: without it the nightly reconcile
     * rewrites every row it visits, which regenerates every {@code tsvector} and
     * churns two GIN indexes for no reason.
     *
     * <p>Two writers can still collide — the reconciler runs on the caller's
     * thread while the listener path runs on the index thread — and both can
     * find nothing and try to insert. The unique key settles it, and the loser's
     * transaction is then unusable: Hibernate will not let a session continue
     * after a constraint violation. Recovery therefore belongs to the caller,
     * which can start a fresh transaction; see
     * {@link SearchIndexer#reindex}. Catching it here and retrying in place was
     * the first attempt and it produced "Entry for instance ... has a null
     * identifier", which is Hibernate saying exactly that.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean upsert(SearchDocument built) {
        built.setContentHash(hashOf(built));

        Optional<SearchDocument> existing = index.findByEntityTypeAndEntityIdAndDocPart(
                built.getEntityType(), built.getEntityId(), built.getDocPart());

        if (existing.isPresent()) {
            SearchDocument row = existing.get();
            if (built.getContentHash() != null
                    && built.getContentHash().equals(row.getContentHash())
                    && row.isDeleted() == built.isDeleted()) {
                return false;
            }
            copyInto(built, row);
            row.setIndexedAt(LocalDateTime.now());
            index.save(row);
            return true;
        }

        index.save(built);
        return true;
    }

    /** Runs in its own transaction: the one that hit the constraint is doomed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retryAsUpdate(SearchDocument built) {
        Optional<SearchDocument> existing = index.findByEntityTypeAndEntityIdAndDocPart(
                built.getEntityType(), built.getEntityId(), built.getDocPart());
        if (existing.isEmpty()) {
            return false;
        }
        SearchDocument row = existing.get();
        copyInto(built, row);
        row.setIndexedAt(LocalDateTime.now());
        index.save(row);
        return true;
    }

    /** Drops every entry for a source row. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean remove(SearchEntityType type, Long entityId) {
        if (index.findByEntityTypeAndEntityId(type, entityId).isEmpty()) {
            return false;
        }
        index.deleteByEntityTypeAndEntityId(type, entityId);
        return true;
    }

    private static void copyInto(SearchDocument from, SearchDocument to) {
        to.setTitle(from.getTitle());
        to.setSubtitle(from.getSubtitle());
        to.setKeywords(from.getKeywords());
        to.setCategory(from.getCategory());
        to.setUrl(from.getUrl());
        to.setAdminUrl(from.getAdminUrl());
        to.setIcon(from.getIcon());
        to.setBadge(from.getBadge());
        to.setBadgeClass(from.getBadgeClass());
        to.setExternal(from.isExternal());
        to.setVisibility(from.getVisibility());
        to.setOwnerUserId(from.getOwnerUserId());
        to.setStatus(from.getStatus());
        to.setWeight(from.getWeight());
        to.setOccurredAt(from.getOccurredAt());
        to.setSourceUpdatedAt(from.getSourceUpdatedAt());
        to.setContentHash(from.getContentHash());
        to.setDeleted(from.isDeleted());
        copyFileText(from, to);
    }

    /**
     * Extraction state belongs to the file worker. Copying it on every rewrite
     * would re-queue every file on every unrelated edit to the row, so it is
     * only reset when the row now points at a different file.
     *
     * <p>The same goes for the text the worker read: a rebuilt document has no
     * file text of its own, and writing its body over the extracted one would
     * make a renamed or moved file unsearchable by content.
     */
    private static void copyFileText(SearchDocument from, SearchDocument to) {
        if (!Objects.equals(from.getFilePath(), to.getFilePath())) {
            to.setFilePath(from.getFilePath());
            to.setExtractionState(from.getExtractionState());
            to.setExtractionError(null);
            to.setFileFingerprint(null);
            to.setExtractedAt(null);
            to.setBody(from.getBody());
            return;
        }
        if (to.getExtractionState() != ExtractionState.DONE) {
            to.setBody(from.getBody());
        }
    }

    private static String hashOf(SearchDocument d) {
        String material = String.join(" ",
                nullToEmpty(d.getTitle()),
                nullToEmpty(d.getSubtitle()),
                nullToEmpty(d.getKeywords()),
                nullToEmpty(d.getBody()),
                nullToEmpty(d.getUrl()),
                nullToEmpty(d.getAdminUrl()),
                nullToEmpty(d.getBadge()),
                nullToEmpty(d.getStatus()),
                String.valueOf(d.getVisibility()),
                String.valueOf(d.getOwnerUserId()),
                String.valueOf(d.getWeight()))
                // Only file-backed rows carry a path; leaving it out otherwise
                // keeps every other row's hash, and so its tsvector, untouched.
                + (d.getFilePath() == null ? "" : " " + d.getFilePath());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the platform; if it is missing, skipping
            // the optimisation is better than failing the write.
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
