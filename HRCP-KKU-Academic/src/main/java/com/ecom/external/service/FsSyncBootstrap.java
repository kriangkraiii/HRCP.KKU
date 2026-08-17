package com.ecom.external.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;

/**
 * First-run loader for the external mirror.
 *
 * <p>The nightly cron keeps the tables fresh, but on a brand-new deployment it
 * would not run until 01:30 the following morning — until then every professor
 * would open the publication picker and see nothing. So each dataset is checked
 * at start-up and pulled if it is still empty.
 *
 * <p>"Empty" is the whole condition: this never re-pulls data that already
 * exists, so restarting the application is cheap and cannot stampede the
 * upstream API. Set {@code fs.sync.on-startup=false} to opt out entirely.
 *
 * <p>Runs asynchronously — the publication sync is deliberately throttled and
 * takes about a minute, and start-up must not block on a remote campus service.
 */
@Component
@Order(100)
public class FsSyncBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FsSyncBootstrap.class);

    private final FsSyncService syncService;
    private final CpDirectorySyncService cpSyncService;
    private final FsFacultyRepository facultyRepo;
    private final ScopusPublicationRepository publicationRepo;
    private final boolean enabled;
    private final boolean force;
    private final boolean cpSyncOnStartup;

    public FsSyncBootstrap(FsSyncService syncService,
            CpDirectorySyncService cpSyncService,
            FsFacultyRepository facultyRepo,
            ScopusPublicationRepository publicationRepo,
            @Value("${fs.sync.on-startup:true}") boolean enabled,
            @Value("${fs.sync.force-on-startup:false}") boolean force,
            @Value("${cp.sync.on-startup:true}") boolean cpSyncOnStartup) {
        this.syncService = syncService;
        this.cpSyncService = cpSyncService;
        this.facultyRepo = facultyRepo;
        this.publicationRepo = publicationRepo;
        this.enabled = enabled;
        this.force = force;
        this.cpSyncOnStartup = cpSyncOnStartup;
    }

    @Override
    @Async
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("First-run sync disabled (fs.sync.on-startup=false)");
            return;
        }

        // A forced run re-reads everything even though rows already exist. It is
        // what you want after adding a mapped column: the stored rows are valid
        // but predate the new field, and only a re-read fills it in.
        if (force) {
            log.info("Forced startup sync requested — re-reading the whole upstream dataset");
            syncService.syncUsers(true);
            FsSyncService.SyncResult pubs = syncService.syncPublications();
            log.info("Forced startup sync finished: {} publication row(s) refreshed", pubs.rows());
            return;
        }

        // Each dataset is judged on its own: a first run that loaded the
        // directory but died before the publications must still finish the job.
        long faculty = facultyRepo.count();
        if (faculty == 0) {
            log.info("First run detected — faculty table is empty, pulling the directory now");
            FsSyncService.SyncResult result = syncService.syncUsers(true);
            log.info("First-run faculty load: {} row(s) [{}]", result.rows(),
                    result.success() ? "ok" : result.message());
        } else {
            log.debug("Faculty mirror already holds {} row(s) — no first-run pull needed", faculty);
        }

        long publications = publicationRepo.count();
        if (publications == 0) {
            log.info("First run detected — publication table is empty, pulling publications now");
            FsSyncService.SyncResult result = syncService.syncPublications();
            log.info("First-run publication load: {} row(s) [{}]", result.rows(),
                    result.success() ? "ok" : result.message());
        } else {
            log.debug("Publication mirror already holds {} row(s) — no first-run pull needed", publications);
        }

        // Check and sync faculty photographs/directory on startup if enabled
        if (cpSyncOnStartup) {
            try {
                log.info("Checking faculty profile photos from college directory on startup...");
                CpDirectorySyncService.Result cpResult = cpSyncService.sync();
                log.info("College directory startup check completed: {}", cpResult.describe());
            } catch (Exception e) {
                log.warn("College directory startup sync encountered an issue: {}", e.getMessage());
            }
        }
    }
}
