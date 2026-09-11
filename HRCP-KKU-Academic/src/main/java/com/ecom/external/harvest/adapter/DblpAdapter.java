package com.ecom.external.harvest.adapter;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ecom.external.harvest.PublicationSourceAdapter;
import com.ecom.external.harvest.config.HarvestProperties;
import com.ecom.external.harvest.model.HarvestContext;
import com.ecom.external.harvest.model.HarvestResult;
import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.harvest.service.FacultyNameResolver;
import com.ecom.external.model.ExternalAuthorMapping;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.service.EnglishNameSplitter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Harvester adapter for DBLP Computer Science Bibliography (https://dblp.org).
 * Reads author PIDs seeded in {@link ExternalAuthorMapping} or queries by English author name.
 */
@Component
public class DblpAdapter implements PublicationSourceAdapter {

    public static final String SOURCE_NAME = "DBLP";
    private static final Logger log = LoggerFactory.getLogger(DblpAdapter.class);

    private final HarvestProperties.DblpProps props;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public DblpAdapter(HarvestProperties harvestProperties) {
        this.props = harvestProperties.getDblp();
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 (HRCP-KKU-Academic)")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(requestFactory(props))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(HarvestProperties.DblpProps props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int connectSec = props.getConnectTimeoutSeconds() > 0 ? Math.min(props.getConnectTimeoutSeconds(), 3) : 3;
        int readSec = props.getReadTimeoutSeconds() > 0 ? Math.min(props.getReadTimeoutSeconds(), 4) : 4;
        factory.setConnectTimeout(Duration.ofSeconds(connectSec));
        factory.setReadTimeout(Duration.ofSeconds(readSec));
        return factory;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled() && props.getBaseUrl() != null && !props.getBaseUrl().isBlank();
    }

    @Override
    public HarvestResult harvest(HarvestContext context) {
        if (!isEnabled()) {
            return HarvestResult.skipped(SOURCE_NAME, "DBLP adapter is disabled");
        }

        long startedAt = System.currentTimeMillis();
        int requestsMade = 0;
        List<RawPublication> harvested = new ArrayList<>();

        // 1. Fast Pre-flight probe: Check if DBLP is accessible or blocked by Cloudflare Bot Challenge
        try {
            requestsMade++;
            String probe = restClient.get()
                    .uri("/search/publ/api?q=test&format=json&h=1")
                    .retrieve()
                    .body(String.class);

            if (probe != null) {
                String trimmed = probe.trim();
                if (trimmed.startsWith("<") || trimmed.contains("cloudflare") || trimmed.contains("cf-browser-verification") || trimmed.contains("not a bot")) {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    log.info("DBLP is currently protected by Cloudflare Bot Challenge. Fast-skipping DBLP adapter in {} ms.", durationMs);
                    return HarvestResult.ok(SOURCE_NAME, List.of(), OffsetDateTime.now(), requestsMade, durationMs,
                            "DBLP skipped: Cloudflare Bot Challenge active on dblp.org");
                }
            }
        } catch (Exception probeEx) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("DBLP pre-flight check failed ({}). Fast-skipping DBLP adapter in {} ms.", probeEx.getMessage(), durationMs);
            return HarvestResult.ok(SOURCE_NAME, List.of(), OffsetDateTime.now(), requestsMade, durationMs,
                    "DBLP skipped: unreachable or timed out (" + probeEx.getMessage() + ")");
        }

        try {
            facultyLoop:
            for (FsFaculty faculty : context.targetFaculty()) {
                Long fsUserId = faculty.getFsUserId();
                List<ExternalAuthorMapping> mappings = context.authorMappingsByUserId() != null
                        ? context.authorMappingsByUserId().getOrDefault(fsUserId, List.of())
                        : List.of();

                // Find DBLP PID if seeded
                List<String> queries = new ArrayList<>();
                boolean isPid = false;
                for (ExternalAuthorMapping m : mappings) {
                    if ("DBLP".equalsIgnoreCase(m.getProvider()) && m.getExternalPid() != null && !m.getExternalPid().isBlank()) {
                        queries.add("author:" + m.getExternalPid() + ":");
                        isPid = true;
                    }
                }

                // If no DBLP PID seeded, query using FacultyNameResolver (English only)
                if (queries.isEmpty() && faculty.getNameEn() != null && !faculty.getNameEn().isBlank()) {
                    for (String q : FacultyNameResolver.generateEnglishQueries(faculty)) {
                        if (!q.contains(",")) {
                            queries.add(q);
                        }
                    }
                    if (queries.isEmpty()) {
                        queries.add(faculty.getNameEn().trim());
                    }
                }

                for (String q : queries) {
                    String encodedQuery = java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
                    String url = "/search/publ/api?q=" + encodedQuery + "&format=json&h=1000";
                    log.debug("DBLP harvest request for user {}: {}", fsUserId, url);
                    requestsMade++;

                    try {
                        String responseBody = restClient.get()
                                .uri(url)
                                .retrieve()
                                .body(String.class);

                        if (responseBody != null && !responseBody.isBlank()) {
                            String trimmed = responseBody.trim();
                            if (trimmed.startsWith("<") || trimmed.contains("cloudflare") || trimmed.contains("cf-browser-verification") || trimmed.contains("not a bot")) {
                                log.warn("DBLP returned HTML / Cloudflare challenge page. Circuit breaker activated — fast skipping remaining DBLP queries.");
                                break facultyLoop;
                            }
                            JsonNode root = mapper.readTree(responseBody);
                            JsonNode hits = root.path("result").path("hits").path("hit");
                            if (hits.isArray()) {
                                for (JsonNode hit : hits) {
                                    processHit(hit, faculty, context.yearFrom(), isPid, harvested);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("DBLP query failed for user {} query '{}': {}. Circuit breaker activated — fast skipping.", fsUserId, q, e.getMessage());
                        break facultyLoop;
                    }

                    throttle(props.getThrottleMs());
                }
            }

            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("DBLP harvest completed: {} publications harvested in {} request(s), {} ms",
                    harvested.size(), requestsMade, durationMs);
            return HarvestResult.ok(SOURCE_NAME, harvested, OffsetDateTime.now(), requestsMade, durationMs,
                    "Harvested " + harvested.size() + " works from DBLP");

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.error("DBLP harvest failed: {}", e.getMessage(), e);
            return HarvestResult.failed(SOURCE_NAME, requestsMade, durationMs, e.getMessage());
        }
    }

    private void processHit(JsonNode hit, FsFaculty faculty, int minYear, boolean isPidQuery, List<RawPublication> out) {
        JsonNode info = hit.path("info");
        if (info.isMissingNode() || info.isNull()) {
            return;
        }

        String title = info.path("title").asText(null);
        if (title == null || title.isBlank()) {
            return;
        }
        // DBLP often appends a trailing dot to titles
        if (title.endsWith(".")) {
            title = title.substring(0, title.length() - 1).trim();
        }

        int year = info.path("year").asInt(0);
        if (year > 0 && year < minYear) {
            return;
        }

        String venue = info.path("venue").asText(null);
        String doi = info.path("doi").asText(null);
        String url = info.path("ee").asText(null);
        if (url == null) {
            url = info.path("url").asText(null);
        }
        String type = info.path("type").asText(null);
        String volume = info.path("volume").asText(null);
        String number = info.path("number").asText(null);
        String pages = info.path("pages").asText(null);
        String dblpKey = info.path("key").asText(null);

        // Authors
        List<String> authorNames = new ArrayList<>();
        JsonNode authorsNode = info.path("authors").path("author");
        if (authorsNode.isArray()) {
            for (JsonNode a : authorsNode) {
                String text = a.isTextual() ? a.asText() : a.path("text").asText(null);
                if (text != null && !text.isBlank()) {
                    authorNames.add(text.replaceAll("\\s+\\d+$", "").trim()); // Strip DBLP disambiguation numbers e.g. "John Doe 0001"
                }
            }
        } else if (!authorsNode.isMissingNode() && !authorsNode.isNull()) {
            String text = authorsNode.isTextual() ? authorsNode.asText() : authorsNode.path("text").asText(null);
            if (text != null && !text.isBlank()) {
                authorNames.add(text.replaceAll("\\s+\\d+$", "").trim());
            }
        }

        String combinedAuthors = String.join(" | ", authorNames);

        if (!isPidQuery) {
            boolean matched = false;
            for (String aName : authorNames) {
                if (FacultyNameResolver.matchesAuthor(aName, faculty)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return;
            }
        }

        RawPublication raw = RawPublication.builder()
                .targetFsUserId(faculty.getFsUserId())
                .externalId(dblpKey != null ? dblpKey : doi)
                .doi(doi)
                .title(title)
                .publicationName(venue)
                .publicationYear(year > 0 ? year : null)
                .authorNames(combinedAuthors)
                .aggregationType(type)
                .volume(volume)
                .issue(number)
                .pageRange(pages)
                .url(url)
                .dataSource(SOURCE_NAME)
                .rawMetadataJson(hit.toString())
                .build();

        out.add(raw);
    }

    private static void throttle(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
