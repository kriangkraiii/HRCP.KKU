package com.ecom.external.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for reading the College of Computing's public staff directory
 * ({@code computing.kku.ac.th/people}).
 *
 * <p>The website is a Nuxt front end over a REST API, so the data is taken from
 * that API rather than by parsing rendered pages — scraping HTML would break the
 * first time the site is restyled.
 */
@Component
@ConfigurationProperties(prefix = "cp.web")
public class CpWebProperties {

    /** Where both the JSON and the images live. */
    private String baseUrl = "https://api.computing.kku.ac.th";

    private boolean enabled = true;

    /**
     * Rows per request. The directory holds under a hundred people, so one
     * request covers it; the paging loop exists in case it grows.
     */
    private int pageSize = 100;

    /** Give up on a single photo rather than stalling the whole run. */
    private int imageTimeoutSeconds = 15;

    /** Connection timeout in seconds when reaching the college API. */
    private int connectTimeoutSeconds = 5;

    /** Read timeout in seconds when waiting for responses from the college API. */
    private int readTimeoutSeconds = 15;

    /** Whether to enable local snapshot cache fallback when the live directory API is unreachable. */
    private boolean fallbackCacheEnabled = true;

    /** Path to save/load directory snapshot cache. */
    private String snapshotFilePath = "uploads/cache/cp_directory_snapshot.json";

    /**
     * How many extra attempts a request gets after the first one times out, with
     * a short backoff between them. A directory read is a background enrichment,
     * so a brief blip is worth waiting out; a real outage is not worth hammering.
     */
    private int maxRetryAttempts = 2;

    public String listEndpoint(int page) {
        return baseUrl + "/api/v1/user/list?page=" + page + "&size=" + pageSize
                + "&sortBy=id&orderBy=asc";
    }

    /** Turns the relative path the API returns into something fetchable. */
    public String imageUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.startsWith("/") ? path : "/" + path;
        return baseUrl + trimmed;
    }

    public boolean isUsable() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getImageTimeoutSeconds() {
        return imageTimeoutSeconds;
    }

    public void setImageTimeoutSeconds(int imageTimeoutSeconds) {
        this.imageTimeoutSeconds = imageTimeoutSeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public boolean isFallbackCacheEnabled() {
        return fallbackCacheEnabled;
    }

    public void setFallbackCacheEnabled(boolean fallbackCacheEnabled) {
        this.fallbackCacheEnabled = fallbackCacheEnabled;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public String getSnapshotFilePath() {
        return snapshotFilePath;
    }

    public void setSnapshotFilePath(String snapshotFilePath) {
        this.snapshotFilePath = snapshotFilePath;
    }
}
