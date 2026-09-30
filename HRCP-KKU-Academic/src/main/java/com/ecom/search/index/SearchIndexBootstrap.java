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
 *
 * <p><b>Once per JVM.</b> Over the remote development database the pass takes
 * 45–200 seconds and holds connections the whole time, and devtools restarts the
 * context on every recompile — so a developer was paying for a full pass after
 * each saved file, with the app sluggish throughout. The marker lives in a
 * system property because that survives a devtools restart while the classes do
 * not. Production starts a fresh JVM on every deploy, so it still reconciles
 * each time.
 */
@Component
public class SearchIndexBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    /** Set once this JVM has run the start-up pass; see the class comment. */
    static final String RECONCILED_MARKER = "hrcp.search.startup-reconcile-done";

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
            if (System.getProperty(RECONCILED_MARKER) != null) {
                log.info("Search index: ข้ามการตรวจทั้งหมดตอนเริ่ม — JVM นี้ตรวจไปแล้ว (devtools restart)");
                return;
            }
            System.setProperty(RECONCILED_MARKER, "true");
            log.info("Search index: เริ่มตรวจสอบความครบถ้วน (มี {} แถว)", index.count());
            log.info("Search index พร้อมใช้งาน: {}", reconciler.reconcileAll());
        } catch (Exception e) {
            // A failed pass must not stop the application starting; the nightly
            // reconcile picks the work up either way.
            log.warn("Search index bootstrap ไม่สำเร็จ: {}", e.toString());
        }
    }
}
