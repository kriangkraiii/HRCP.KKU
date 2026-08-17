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
}
