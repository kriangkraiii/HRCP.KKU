package com.ecom.external.harvest;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.external.repository.ScopusPublicationRepository;

/**
 * Runs the {@code pg_trgm} title-similarity lookup in a transaction of its own.
 *
 * <p>Not an abstraction for its own sake — it exists to contain a failure. The
 * query is native SQL calling {@code similarity()}, which only exists where the
 * {@code pg_trgm} extension does. When it is missing the statement fails, and a
 * failed statement marks the surrounding transaction rollback-only. The caller
 * catches the exception and carries on, but the damage is already done: at
 * commit the whole harvest batch is discarded, and the only trace is one WARN
 * line about a fuzzy match that did not happen.
 *
 * <p>{@code REQUIRES_NEW} suspends the caller's transaction, so a failure here
 * marks only this one. The batch that was being written survives, minus the one
 * comparison that could not be made.
 */
@Component
public class FuzzyTitleMatcher {

    private final ScopusPublicationRepository publicationRepo;

    public FuzzyTitleMatcher(ScopusPublicationRepository publicationRepo) {
        this.publicationRepo = publicationRepo;
    }

    /**
     * @throws org.springframework.dao.DataAccessException when the database has
     *         no {@code pg_trgm}; the caller decides what that means
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Long> findSimilarTitleId(Long fsUserId, String title, int year, double threshold) {
        return publicationRepo.findFuzzyMatchId(fsUserId, title, year, threshold);
    }
}
