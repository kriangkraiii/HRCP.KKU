package com.ecom.external.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Bookkeeping for one sync job — one row per {@link #syncType}.
 *
 * <p>Holds the incremental cursor ({@link #lastUpdatedSince}) so a restart does
 * not re-pull the whole upstream dataset, plus the last outcome so an admin can
 * see a failing nightly job without reading logs.
 */
@Entity
@Table(name = "fs_sync_state")
public class FsSyncState {

    public static final String TYPE_USERS = "users";
    public static final String TYPE_SCOPUS = "scopus";

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RUNNING = "RUNNING";

    @Id
    @Column(name = "sync_type", length = 32)
    private String syncType;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    /**
     * Highest {@code updated_at} seen upstream. Sent as {@code updated_since} on
     * the next run so only changed records come back.
     */
    @Column(name = "last_updated_since")
    private OffsetDateTime lastUpdatedSince;

    @Column(name = "last_status", length = 16)
    private String lastStatus;

    @Column(name = "rows_processed")
    private Integer rowsProcessed;

    @Column(name = "requests_made")
    private Integer requestsMade;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "message", length = 2000)
    private String message;

    public FsSyncState() {
    }

    public FsSyncState(String syncType) {
        this.syncType = syncType;
    }

    public String getSyncType() {
        return syncType;
    }

    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(LocalDateTime lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public OffsetDateTime getLastUpdatedSince() {
        return lastUpdatedSince;
    }

    public void setLastUpdatedSince(OffsetDateTime lastUpdatedSince) {
        this.lastUpdatedSince = lastUpdatedSince;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public Integer getRowsProcessed() {
        return rowsProcessed;
    }

    public void setRowsProcessed(Integer rowsProcessed) {
        this.rowsProcessed = rowsProcessed;
    }

    public Integer getRequestsMade() {
        return requestsMade;
    }

    public void setRequestsMade(Integer requestsMade) {
        this.requestsMade = requestsMade;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
