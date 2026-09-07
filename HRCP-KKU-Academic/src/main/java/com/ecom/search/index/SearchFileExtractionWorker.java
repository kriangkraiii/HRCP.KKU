package com.ecom.search.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.search.model.ExtractionState;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.repository.SearchDocumentRepository;

/**
 * Drains the queue of files waiting to have their text read.
 *
 * <p>Separate from the indexer because the work is a different shape: indexing a
 * row is a millisecond, opening a hundred-page PDF is not, and putting the two
 * on one path would mean a large upload delaying every unrelated index update
 * behind it.
 *
 * <p>Rows are claimed by flipping them to {@code RUNNING} before the file is
 * opened, so a crash mid-extraction leaves a row that is visibly stuck rather
 * than one that is silently retried forever. Ordering by id keeps the queue
 * predictable and stops a file that always fails from being picked ahead of
 * everything behind it.
 *
 * <p><b>One instance only.</b> Production runs a single Windows Service, so a
 * plain claim-by-update is enough. A second instance would need
 * {@code SELECT … FOR UPDATE SKIP LOCKED}, which is native SQL and
 * PostgreSQL-only — worth knowing before anyone scales this out.
 */
@Component
public class SearchFileExtractionWorker {

    private static final Logger log = LoggerFactory.getLogger(SearchFileExtractionWorker.class);

    private final SearchDocumentRepository index;
    private final FileTextExtractor extractor;
    private final boolean enabled;
    private final int batchSize;

    public SearchFileExtractionWorker(SearchDocumentRepository index,
            FileTextExtractor extractor,
            @Value("${app.search.enabled:true}") boolean searchEnabled,
            @Value("${app.search.file-text.enabled:true}") boolean fileTextEnabled,
            @Value("${app.search.file-text.batch-size:20}") int batchSize) {
        this.index = index;
        this.extractor = extractor;
        this.enabled = searchEnabled && fileTextEnabled;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.search.file-text.interval-ms:60000}",
            initialDelayString = "${app.search.file-text.initial-delay-ms:60000}")
    public void drainQueue() {
        if (!enabled) {
            return;
        }
        try {
            processBatch();
        } catch (Exception e) {
            log.warn("คิวสกัดข้อความจากไฟล์ทำงานไม่สำเร็จ: {}", e.toString());
        }
    }

    /**
     * One batch.
     *
     * @return how many rows were touched
     */
    @Transactional
    public int processBatch() {
        List<SearchDocument> pending = index.findNextForExtraction(
                ExtractionState.PENDING, PageRequest.of(0, Math.max(batchSize, 1)));

        for (SearchDocument row : pending) {
            row.setExtractionState(ExtractionState.RUNNING);
        }
        index.saveAll(pending);

        int done = 0;
        for (SearchDocument row : pending) {
            FileTextExtractor.Extraction result = extractor.extract(row.getFilePath());

            // Only DONE writes text. A skip or a failure must leave whatever the
            // factory put in body alone — for an attachment that is the filename
            // and request code, which are still worth finding.
            if (result.state() == ExtractionState.DONE && result.text() != null) {
                row.setBody(result.text());
                done++;
            }
            row.setExtractionState(result.state());
            row.setExtractionError(result.error());
            row.setExtractedAt(java.time.LocalDateTime.now());
            row.setFileFingerprint(fingerprintOf(row.getFilePath()));
            // content_hash is left stale on purpose: the next reconcile then
            // sees a difference and rebuilds the row, which is how extracted
            // text survives a later edit to the source.
            index.save(row);
        }

        if (!pending.isEmpty()) {
            log.info("สกัดข้อความจากไฟล์: ประมวลผล {} รายการ สำเร็จ {}", pending.size(), done);
        }
        return pending.size();
    }

    /**
     * Size and last-modified, so a replaced file is read again and an unchanged
     * one is not.
     */
    private static String fingerprintOf(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(storedPath);
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return null;
        }
    }
}
