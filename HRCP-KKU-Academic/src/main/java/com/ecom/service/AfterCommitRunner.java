package com.ecom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers work until the surrounding transaction has actually committed.
 *
 * <p>Notifications are raised from inside {@code @Transactional} service
 * methods and delivered on a background thread. Starting that thread straight
 * away is wrong twice over: it can read the row before the change is committed
 * and describe the old state, and if the transaction then rolls back it has
 * announced something that never happened.
 *
 * <p>When there is no transaction in progress — a controller calling this after
 * the service has already returned — the work simply runs now, because there is
 * nothing left to wait for.
 */
@Component
public class AfterCommitRunner {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitRunner.class);

    public void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(action);
            }
        });
    }

    /**
     * Runs the work without letting its failure surface as the caller's.
     *
     * <p>By this point the change itself has been saved. Reporting a failed
     * notification as a failed request would be worse than the missed
     * notification, so it is logged and goes no further.
     */
    private void dispatch(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Post-commit notification failed: {}", e.toString(), e);
        }
    }
}
