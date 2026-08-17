package com.ecom.external.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for the Fund Management external API ({@code fs.api.*}).
 *
 * <p>The API key lives in {@code application.properties}, which this repository
 * git-ignores, and can be overridden per environment with {@code FS_API_KEY}.
 */
@Component
@ConfigurationProperties(prefix = "fs.api")
public class FsApiProperties {

    private String baseUrl = "https://fs.computing.kku.ac.th/api/ext/v1";

    private String key = "";

    /** Lets a deployment turn the integration off without removing the cron beans. */
    private boolean enabled = true;

    /** Rows per page. Upstream caps this at 500. */
    private int pageSize = 200;

    /**
     * How many {@code user_id}s to put in one Scopus request. Keeping this well
     * under the whole faculty means one bad id cannot fail the entire sync, and
     * the URL stays a sane length.
     */
    private int userBatchSize = 25;

    /**
     * Pause between requests. The published budget is 100 requests/minute, so
     * 700 ms leaves roughly 85/min — comfortably inside it.
     */
    private long throttleMs = 700;

    /** Oldest publication year to mirror. */
    private int yearFrom = 2015;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPageSize() {
        return Math.min(Math.max(pageSize, 1), 500);
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getUserBatchSize() {
        return Math.max(userBatchSize, 1);
    }

    public void setUserBatchSize(int userBatchSize) {
        this.userBatchSize = userBatchSize;
    }

    public long getThrottleMs() {
        return Math.max(throttleMs, 0);
    }

    public void setThrottleMs(long throttleMs) {
        this.throttleMs = throttleMs;
    }

    public int getYearFrom() {
        return yearFrom;
    }

    public void setYearFrom(int yearFrom) {
        this.yearFrom = yearFrom;
    }

    /** True when the integration is on and a key is actually configured. */
    public boolean isUsable() {
        return enabled && key != null && !key.isBlank();
    }
}
