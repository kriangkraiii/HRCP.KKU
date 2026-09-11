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

        Map<String, FsFaculty> facultyByName = buildFacultyNameIndex(context.targetFaculty());

        String cursor = "*";
        int page = 1;
        int maxPages = 50;

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

                if (faculty.getNameEn() == null || faculty.getNameEn().isBlank()) {
                    continue;
                }

                EnglishNameSplitter.Parts parts = EnglishNameSplitter.split(faculty.getNameEn());
                if (parts.firstName() == null || parts.lastName() == null) {
                    continue;
                }

                String fullName = parts.firstName() + " " + parts.lastName();
                log.debug("Crossref harvest request for user {} ({})", faculty.getFsUserId(), fullName);
                requestsMade++;

                try {
                    String responseBody = restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/works")
                                    .queryParam("query.author", fullName)
                                    .queryParam("filter", filterParam)
                                    .queryParam("rows", props.getPageSize())
                                    .build())
                            .retrieve()
                            .body(String.class);

                    if (responseBody != null && !responseBody.isBlank()) {
                        JsonNode root = mapper.readTree(responseBody);
                        JsonNode message = root.path("message");
                        JsonNode items = message.path("items");
                        if (items.isArray() && !items.isEmpty()) {
                            for (JsonNode item : items) {
                                processItem(item, facultyByName, harvested);
                            }
                        }
                    }
                } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests ex) {
                    log.warn("Crossref rate limit (429) hit for {}: backing off 2s", faculty.getNameEn());
                    throttle(2000);
                } catch (Exception ex) {
                    log.warn("Crossref author query failed for {}: {}", faculty.getNameEn(), ex.getMessage());
                }

                throttle(props.getThrottleMs());
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

    private void processItem(JsonNode item, Map<String, FsFaculty> facultyByName, List<RawPublication> out) {
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
                FsFaculty matched = matchFaculty(full, facultyByName);
                if (matched != null && !matchedFacultyList.contains(matched)) {
                    matchedFacultyList.add(matched);
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

    private static Map<String, FsFaculty> buildFacultyNameIndex(List<FsFaculty> facultyList) {
        Map<String, FsFaculty> map = new HashMap<>();
        if (facultyList == null) {
            return map;
        }
        for (FsFaculty f : facultyList) {
            if (f.getDisplayName() != null) {
                map.put(normalize(f.getDisplayName()), f);
            }
            if (f.getFirstName() != null && f.getLastName() != null) {
                map.put(normalize(f.getFirstName() + " " + f.getLastName()), f);
            }
            if (f.getNameEn() != null && !f.getNameEn().isBlank()) {
                map.put(normalize(f.getNameEn()), f);
                EnglishNameSplitter.Parts parts = EnglishNameSplitter.split(f.getNameEn());
                if (parts.firstName() != null && parts.lastName() != null) {
                    map.put(normalize(parts.lastName() + " " + parts.firstName()), f);
                    map.put(normalize(parts.firstName() + " " + parts.lastName()), f);
                }
            }
        }
        return map;
    }

    private static FsFaculty matchFaculty(String authorName, Map<String, FsFaculty> index) {
        if (authorName == null) {
            return null;
        }
        String norm = normalize(authorName);
        FsFaculty direct = index.get(norm);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, FsFaculty> entry : index.entrySet()) {
            if (entry.getKey().contains(norm) || norm.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9\\u0E00-\\u0E7F]", "");
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
