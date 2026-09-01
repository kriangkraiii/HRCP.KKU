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
import com.ecom.external.model.ExternalAuthorMapping;
import com.ecom.external.model.FsFaculty;
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
                .defaultHeader(HttpHeaders.USER_AGENT, "HRCP-KKU-Academic/1.0")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(requestFactory(props))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(HarvestProperties.DblpProps props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(props.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(props.getReadTimeoutSeconds()));
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

        try {
            for (FsFaculty faculty : context.targetFaculty()) {
                Long fsUserId = faculty.getFsUserId();
                List<ExternalAuthorMapping> mappings = context.authorMappingsByUserId() != null
                        ? context.authorMappingsByUserId().getOrDefault(fsUserId, List.of())
                        : List.of();

                // Find DBLP PID if seeded
                List<String> queries = new ArrayList<>();
                for (ExternalAuthorMapping m : mappings) {
                    if ("DBLP".equalsIgnoreCase(m.getProvider()) && m.getExternalPid() != null && !m.getExternalPid().isBlank()) {
                        queries.add("author:" + m.getExternalPid() + ":");
                    }
                }

                // If no DBLP PID seeded, query by English name
                if (queries.isEmpty() && faculty.getNameEn() != null && !faculty.getNameEn().isBlank()) {
                    String cleanEnName = faculty.getNameEn().trim();
                    queries.add(cleanEnName);
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
                            JsonNode root = mapper.readTree(responseBody);
                            JsonNode hits = root.path("result").path("hits").path("hit");
                            if (hits.isArray()) {
                                for (JsonNode hit : hits) {
                                    processHit(hit, faculty, context.yearFrom(), harvested);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("DBLP query failed for user {} query '{}': {}", fsUserId, q, e.getMessage());
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

    private void processHit(JsonNode hit, FsFaculty faculty, int minYear, List<RawPublication> out) {
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
