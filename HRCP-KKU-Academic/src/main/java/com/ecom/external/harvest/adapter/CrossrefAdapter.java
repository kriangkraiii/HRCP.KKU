package com.ecom.external.harvest.adapter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.ecom.external.model.FsFaculty;
import com.ecom.external.service.EnglishNameSplitter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Harvester adapter for Crossref (https://api.crossref.org).
 * Searches works by affiliation "Khon Kaen University" and matches
 * authorships to our faculty members.
 */
@Component
public class CrossrefAdapter implements PublicationSourceAdapter {

    public static final String SOURCE_NAME = "CROSSREF";
    private static final Logger log = LoggerFactory.getLogger(CrossrefAdapter.class);

    private final HarvestProperties.CrossrefProps props;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public CrossrefAdapter(HarvestProperties harvestProperties) {
        this.props = harvestProperties.getCrossref();
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "HRCP-KKU-Academic/1.0 (mailto:" + props.getMailto() + ")")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(requestFactory(props))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(HarvestProperties.CrossrefProps props) {
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
            return HarvestResult.skipped(SOURCE_NAME, "Crossref adapter is disabled");
        }

        long startedAt = System.currentTimeMillis();
        int requestsMade = 0;
        List<RawPublication> harvested = new ArrayList<>();

        try {
            int fromYear = context.yearFrom();
            String filterParam = "from-pub-date:" + fromYear + "-01-01";

            // Query Crossref per faculty member to ensure complete individual coverage
            // Max execution deadline: 420s (7 minutes) to ensure clean completion within 8-minute adapter timeout
            long maxDurationMs = 420_000L;

            for (FsFaculty faculty : context.targetFaculty()) {
                if (System.currentTimeMillis() - startedAt > maxDurationMs) {
                    log.warn("Crossref harvest deadline reached ({} ms); stopping early with {} works harvested",
                            System.currentTimeMillis() - startedAt, harvested.size());
                    break;
                }

                List<String> searchQueries = com.ecom.external.harvest.service.FacultyNameResolver.generateEnglishQueries(faculty);
                if (searchQueries.isEmpty()) {
                    continue;
                }

                // Query with top variants (e.g. First Last, and Full Name if different)
                int queryLimit = Math.min(2, searchQueries.size());
                for (int qIdx = 0; qIdx < queryLimit; qIdx++) {
                    String queryName = searchQueries.get(qIdx);
                    log.debug("Crossref harvest request for user {} ('{}')", faculty.getFsUserId(), queryName);

                    int offset = 0;
                    int pageSize = props.getPageSize() > 0 ? props.getPageSize() : 50;
                    int maxPagesForUser = 2; // Up to 100-200 works per author

                    for (int p = 0; p < maxPagesForUser; p++) {
                        requestsMade++;
                        final int currentOffset = offset;
                        try {
                            String responseBody = restClient.get()
                                    .uri(uriBuilder -> uriBuilder
                                            .path("/works")
                                            .queryParam("query.author", queryName)
                                            .queryParam("filter", filterParam)
                                            .queryParam("rows", pageSize)
                                            .queryParam("offset", currentOffset)
                                            .build())
                                    .retrieve()
                                    .body(String.class);

                            if (responseBody != null && !responseBody.isBlank()) {
                                JsonNode root = mapper.readTree(responseBody);
                                JsonNode message = root.path("message");
                                JsonNode items = message.path("items");
                                if (items.isArray() && !items.isEmpty()) {
                                    for (JsonNode item : items) {
                                        processItem(item, context.targetFaculty(), harvested);
                                    }
                                }
                                int totalResults = message.path("total-results").asInt(0);
                                if (offset + pageSize >= totalResults || items.size() < pageSize) {
                                    break; // Reached end of results for this query
                                }
                                offset += pageSize;
                            } else {
                                break;
                            }
                        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests ex) {
                            log.warn("Crossref rate limit (429) hit for {}: backing off 2s", queryName);
                            throttle(2000);
                            break;
                        } catch (Exception ex) {
                            log.warn("Crossref author query failed for {}: {}", queryName, ex.getMessage());
                            break;
                        }

                        throttle(props.getThrottleMs());
                    }
                }
            }

            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("Crossref harvest completed: {} publications harvested in {} request(s), {} ms",
                    harvested.size(), requestsMade, durationMs);
            return HarvestResult.ok(SOURCE_NAME, harvested, OffsetDateTime.now(), requestsMade, durationMs,
                    "Harvested " + harvested.size() + " works from Crossref");

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.error("Crossref harvest failed: {}", e.getMessage(), e);
            return HarvestResult.failed(SOURCE_NAME, requestsMade, durationMs, e.getMessage());
        }
    }

    private void processItem(JsonNode item, List<FsFaculty> targetFaculty, List<RawPublication> out) {
        String doi = item.path("DOI").asText(null);

        // Title
        JsonNode titleArr = item.path("title");
        String title = titleArr.isArray() && !titleArr.isEmpty() ? titleArr.get(0).asText(null) : null;
        if (title == null || title.isBlank()) {
            return;
        }

        // Journal / Container title
        JsonNode containerArr = item.path("container-title");
        String pubName = containerArr.isArray() && !containerArr.isEmpty() ? containerArr.get(0).asText(null) : null;

        // Publication Year
        Integer pubYear = null;
        JsonNode dateParts = item.path("published").path("date-parts");
        if (!dateParts.isArray() || dateParts.isEmpty()) {
            dateParts = item.path("created").path("date-parts");
        }
        if (dateParts.isArray() && !dateParts.isEmpty() && dateParts.get(0).isArray() && !dateParts.get(0).isEmpty()) {
            pubYear = dateParts.get(0).get(0).asInt();
        }

        // ISSN
        String issn = null;
        JsonNode issnArr = item.path("ISSN");
        if (issnArr.isArray() && !issnArr.isEmpty()) {
            issn = issnArr.get(0).asText(null);
        }

        String volume = item.path("volume").asText(null);
        String issue = item.path("issue").asText(null);
        String pageRange = item.path("page").asText(null);
        String type = item.path("type").asText(null);
        Double score = item.has("score") ? item.path("score").asDouble() : null;
        String url = item.path("URL").asText(doi != null ? "https://doi.org/" + doi : null);

        // Authors
        List<String> authorNamesList = new ArrayList<>();
        List<FsFaculty> matchedFacultyList = new ArrayList<>();

        JsonNode authors = item.path("author");
        for (JsonNode auth : authors) {
            String given = auth.path("given").asText("");
            String family = auth.path("family").asText("");
            String full = (given + " " + family).trim();
            if (full.isBlank()) {
                full = auth.path("name").asText(null);
            }
            if (full != null && !full.isBlank()) {
                authorNamesList.add(full);
                for (FsFaculty f : targetFaculty) {
                    if (com.ecom.external.harvest.service.FacultyNameResolver.matchesAuthor(full, f)) {
                        if (!matchedFacultyList.contains(f)) {
                            matchedFacultyList.add(f);
                        }
                    }
                }
            }
        }

        String combinedAuthors = String.join(" | ", authorNamesList);

        for (FsFaculty faculty : matchedFacultyList) {
            RawPublication raw = RawPublication.builder()
                    .targetFsUserId(faculty.getFsUserId())
                    .externalId(doi)
                    .doi(doi)
                    .title(title)
                    .publicationName(pubName)
                    .publicationYear(pubYear)
                    .authorNames(combinedAuthors)
                    .aggregationType(type)
                    .subtype(type)
                    .issn(issn)
                    .volume(volume)
                    .issue(issue)
                    .pageRange(pageRange)
                    .url(url)
                    .crossrefScore(score)
                    .dataSource(SOURCE_NAME)
                    .rawMetadataJson(item.toString())
                    .build();

            out.add(raw);
        }
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
