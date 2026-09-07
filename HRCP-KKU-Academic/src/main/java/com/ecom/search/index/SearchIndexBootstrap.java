package com.ecom.search.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ecom.search.repository.SearchDocumentRepository;

/**
 * Brings the index in line with the database at every start-up.
 *
 * <p>Without this, deploying the feature leaves every record already in the
 * database unsearchable until someone happens to edit it. That is not a
 * migration's job — the rows need form JSON flattened, names assembled from
 * nine columns and links resolved, all of which is Java.
 *
 * <p>It matters that this needs no operator step: production is deployed by a
 * workflow that stops a Windows Service, swaps the jar and starts it again.
 * Anything requiring a person to remember to run a command afterwards would
 * eventually not be run.
 *
 * <p><b>Why a full pass and not "only when the index is empty".</b> That was the
 * first version and it was silently wrong. Other start-up work writes to indexed
 * tables before this runs — {@code AdminInitializer} creates the administrator
 * account, and the entity listener indexes it — so by the time an emptiness
 * check ran, the index held a row and the check concluded there was nothing to
 * do. On a first deploy that skips the backfill entirely, which is the one thing
 * this class exists to prevent. No count is a reliable "has this ever been
 * built" signal, so the only honest answer is to reconcile and let the
 * comparison decide.
 *
 * <p>The pass is cheap to repeat: {@code content_hash} means an already-correct
 * row costs a read and no write. It runs asynchronously so a slow pass delays
 * nobody's sign-in, and {@code app.search.reconcile-on-startup=false} turns it
 * off if the corpus ever grows enough to make boot-time work unwelcome.
 */
@Component
public class SearchIndexBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final SearchDocumentRepository index;
    private final SearchReconciler reconciler;
    private final boolean enabled;
    private final boolean reconcileOnStartup;

    public SearchIndexBootstrap(SearchDocumentRepository index,
            SearchReconciler reconciler,
            @Value("${app.search.enabled:true}") boolean enabled,
            @Value("${app.search.reconcile-on-startup:true}") boolean reconcileOnStartup) {
        this.index = index;
        this.reconciler = reconciler;
        this.enabled = enabled;
        this.reconcileOnStartup = reconcileOnStartup;
    }

    @Override
    @Async("searchIndexExecutor")
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            // Menus come from a static catalogue and nothing else ever writes
            // them, so they are seeded even when the full pass is switched off —
            // otherwise turning it off would silently empty the menu results.
            reconciler.seedNavigation();

            if (!reconcileOnStartup) {
                return;
            }
            log.info("Search index: เริ่มตรวจสอบความครบถ้วน (มี {} แถว)", index.count());
            log.info("Search index พร้อมใช้งาน: {}", reconciler.reconcileAll());
        } catch (Exception e) {
            // A failed pass must not stop the application starting; the nightly
            // reconcile picks the work up either way.
            log.warn("Search index bootstrap ไม่สำเร็จ: {}", e.toString());
        }
    }
}
