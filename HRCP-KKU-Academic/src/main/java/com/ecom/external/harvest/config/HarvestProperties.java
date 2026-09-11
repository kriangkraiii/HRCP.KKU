package com.ecom.external.harvest.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for multi-source academic publication harvesting.
 */
@Component
@ConfigurationProperties(prefix = "harvest")
public class HarvestProperties {

    private boolean enabled = true;
    private String cron = "0 0 4 * * *";
    private int yearFrom = 2015;
    private int userBatchSize = 25;
    private double fuzzyThreshold = 0.70;
    private int adapterTimeoutSeconds = 480; // 8 minutes (allows sources taking ~210s to complete with safe headroom)
    private int globalTimeoutSeconds = 900;  // 15 minutes overall process ceiling
    private int staleThresholdMinutes = 30;  // 30 minutes before declaring a RUNNING job as zombie

    private CrossrefProps crossref = new CrossrefProps();
    private OpenAlexProps openalex = new OpenAlexProps();
    private DblpProps dblp = new DblpProps();
    private ThaijoProps thaijo = new ThaijoProps();
    private KkuIrProps kkuir = new KkuIrProps();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getYearFrom() {
        return yearFrom;
    }

    public void setYearFrom(int yearFrom) {
        this.yearFrom = yearFrom;
    }

    public int getUserBatchSize() {
        return userBatchSize;
    }

    public void setUserBatchSize(int userBatchSize) {
        this.userBatchSize = userBatchSize;
    }

    public double getFuzzyThreshold() {
        return fuzzyThreshold;
    }

    public void setFuzzyThreshold(double fuzzyThreshold) {
        this.fuzzyThreshold = fuzzyThreshold;
    }

    public int getAdapterTimeoutSeconds() {
        return adapterTimeoutSeconds;
    }

    public void setAdapterTimeoutSeconds(int adapterTimeoutSeconds) {
        this.adapterTimeoutSeconds = adapterTimeoutSeconds;
    }

    public int getGlobalTimeoutSeconds() {
        return globalTimeoutSeconds;
    }

    public void setGlobalTimeoutSeconds(int globalTimeoutSeconds) {
        this.globalTimeoutSeconds = globalTimeoutSeconds;
    }

    public int getStaleThresholdMinutes() {
        return staleThresholdMinutes;
    }

    public void setStaleThresholdMinutes(int staleThresholdMinutes) {
        this.staleThresholdMinutes = staleThresholdMinutes;
    }

    public CrossrefProps getCrossref() {
        return crossref;
    }

    public void setCrossref(CrossrefProps crossref) {
        this.crossref = crossref;
    }

    public OpenAlexProps getOpenalex() {
        return openalex;
    }

    public void setOpenalex(OpenAlexProps openalex) {
        this.openalex = openalex;
    }

    public DblpProps getDblp() {
        return dblp;
    }

    public void setDblp(DblpProps dblp) {
        this.dblp = dblp;
    }

    public ThaijoProps getThaijo() {
        return thaijo;
    }

    public void setThaijo(ThaijoProps thaijo) {
        this.thaijo = thaijo;
    }

    public KkuIrProps getKkuir() {
        return kkuir;
    }

    public void setKkuir(KkuIrProps kkuir) {
        this.kkuir = kkuir;
    }

    // =========================================================================
    // Nested Properties per Source
    // =========================================================================

    public static class CrossrefProps {
        private boolean enabled = true;
        private String baseUrl = "https://api.crossref.org";
        private String mailto = "admin@computing.kku.ac.th";
        private long throttleMs = 250;
        private int pageSize = 100;
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getMailto() { return mailto; }
        public void setMailto(String mailto) { this.mailto = mailto; }
        public long getThrottleMs() { return throttleMs; }
        public void setThrottleMs(long throttleMs) { this.throttleMs = throttleMs; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    }

    public static class OpenAlexProps {
        private boolean enabled = true;
        private String baseUrl = "https://api.openalex.org";
        private String mailto = "admin@computing.kku.ac.th";
        private String rorId = "https://ror.org/03cq4gr50";
        private long throttleMs = 100;
        private int pageSize = 100;
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getMailto() { return mailto; }
        public void setMailto(String mailto) { this.mailto = mailto; }
        public String getRorId() { return rorId; }
        public void setRorId(String rorId) { this.rorId = rorId; }
        public long getThrottleMs() { return throttleMs; }
        public void setThrottleMs(long throttleMs) { this.throttleMs = throttleMs; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    }

    public static class DblpProps {
        private boolean enabled = true;
        private String baseUrl = "https://dblp.org";
        private long throttleMs = 1000;
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public long getThrottleMs() { return throttleMs; }
        public void setThrottleMs(long throttleMs) { this.throttleMs = throttleMs; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    }

    public static class ThaijoProps {
        private boolean enabled = true;
        private String oaiEndpoint = "https://sc01.tci-thaijo.org/index.php/index/oai";
        private long throttleMs = 500;
        private int connectTimeoutSeconds = 15;
        private int readTimeoutSeconds = 60;
        private List<String> affiliationKeywords = new ArrayList<>(List.of(
                "Khon Kaen University",
                "มหาวิทยาลัยขอนแก่น",
                "วิทยาลัยการคอมพิวเตอร์",
                "College of Computing",
                "Computing KKU"
        ));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getOaiEndpoint() { return oaiEndpoint; }
        public void setOaiEndpoint(String oaiEndpoint) { this.oaiEndpoint = oaiEndpoint; }
        public long getThrottleMs() { return throttleMs; }
        public void setThrottleMs(long throttleMs) { this.throttleMs = throttleMs; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
        public List<String> getAffiliationKeywords() { return affiliationKeywords; }
        public void setAffiliationKeywords(List<String> affiliationKeywords) { this.affiliationKeywords = affiliationKeywords; }
    }

    public static class KkuIrProps {
        private boolean enabled = true;
        private String oaiEndpoint = "https://kkuir.kku.ac.th/oai/request";
        private String set = "col_123456789_37199,col_123456789_37198,col_123456789_37197";
        private long throttleMs = 1000;
        private int connectTimeoutSeconds = 15;
        private int readTimeoutSeconds = 60;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getOaiEndpoint() { return oaiEndpoint; }
        public void setOaiEndpoint(String oaiEndpoint) { this.oaiEndpoint = oaiEndpoint; }
        public String getSet() { return set; }
        public void setSet(String set) { this.set = set; }
        public long getThrottleMs() { return throttleMs; }
        public void setThrottleMs(long throttleMs) { this.throttleMs = throttleMs; }
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    }
}
