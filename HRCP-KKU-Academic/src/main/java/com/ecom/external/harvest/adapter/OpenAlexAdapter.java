package com.ecom.external.harvest.adapter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ecom.external.harvest.PublicationHarmonizer;
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
 * Harvester adapter for OpenAlex (https://openalex.org).
 * Filters works by KKU's ROR ID ({@code https://ror.org/03cq4gr50}) and matches
 * authorships to our faculty members.
 */
@Component
public class OpenAlexAdapter implements PublicationSourceAdapter {

    public static final String SOURCE_NAME = "OPENALEX";
    private static final Logger log = LoggerFactory.getLogger(OpenAlexAdapter.class);

    private final HarvestProperties.OpenAlexProps props;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAlexAdapter(HarvestProperties harvestProperties) {
        this.props = harvestProperties.getOpenalex();
        var builder = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "HRCP-KKU-Academic/1.0 (mailto:" + props.getMailto() + ")")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(requestFactory(props));

        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            builder.defaultHeader("api-key", props.getApiKey().trim());
        }

        this.restClient = builder.build();
    }

    private static ClientHttpRequestFactory requestFactory(HarvestProperties.OpenAlexProps props) {
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
            return HarvestResult.skipped(SOURCE_NAME, "OpenAlex adapter is disabled");
        }

        long startedAt = System.currentTimeMillis();
        int requestsMade = 0;
        List<RawPublication> harvested = new ArrayList<>();

        try {
            int fromYear = context.yearFrom();

            // Search directly per faculty member using clean English name
            long maxDurationMs = 420_000L; // 7 minutes soft deadline

            facultyLoop:
            for (FsFaculty faculty : context.targetFaculty()) {
                if (System.currentTimeMillis() - startedAt > maxDurationMs) {
                    log.warn("OpenAlex harvest deadline reached ({} ms); stopping early with {} works harvested",
                            System.currentTimeMillis() - startedAt, harvested.size());
                    break;
                }

                List<String> queries = com.ecom.external.harvest.service.FacultyNameResolver.generateEnglishQueries(faculty);
                if (queries.isEmpty()) {
                    continue;
                }

                int queryLimit = Math.min(2, queries.size());
                int pageSize = props.getPageSize() > 0 ? props.getPageSize() : 100;

                for (int qIdx = 0; qIdx < queryLimit; qIdx++) {
                    String queryAuthorName = queries.get(qIdx);

                    // Strategy: Search OpenAlex works with raw_author_name and date filter
                    // Note: 'from_updated_date' requires a paid plan on OpenAlex. Free tier supports 'from_publication_date'.
                    String filterParam = "raw_author_name.search:" + java.net.URLEncoder.encode(queryAuthorName, StandardCharsets.UTF_8);
                    if (context.since() != null) {
                        filterParam += ",from_publication_date:" + context.since().toLocalDate().toString();
                    } else if (fromYear > 0) {
                        filterParam += ",from_publication_date:" + fromYear + "-01-01";
                    }

                    int pageNum = 1;
                    int maxPagesForUser = 3; // Up to 300 works per author

                    while (pageNum <= maxPagesForUser) {
                        String url = "/works?filter=" + filterParam + "&per_page=" + pageSize + "&page=" + pageNum;
                        log.debug("OpenAlex author harvest user {} ('{}', page {}): {}", faculty.getFsUserId(), queryAuthorName, pageNum, url);
                        requestsMade++;

                        try {
                            String responseBody = restClient.get()
                                    .uri(url)
                                    .retrieve()
                                    .body(String.class);

                            if (responseBody != null && !responseBody.isBlank()) {
                                JsonNode root = mapper.readTree(responseBody);
                                JsonNode results = root.path("results");
                                if (results.isArray() && !results.isEmpty()) {
                                    for (JsonNode workNode : results) {
                                        processWork(workNode, context.targetFaculty(), harvested);
                                    }
                                }
                                int count = root.path("meta").path("count").asInt(0);
                                if (pageNum * pageSize >= count || results.size() < pageSize) {
                                    break; // Reached end of results for this query
                                }
                                pageNum++;
                            } else {
                                break;
                            }
                        } catch (Exception ex) {
                            String msg = ex.getMessage() != null ? ex.getMessage() : "";
                            if (msg.contains("429") || msg.contains("Plan upgrade") || msg.contains("Rate limit") || msg.contains("Insufficient budget")) {
                                log.warn("OpenAlex quota or plan restriction encountered (429): {}. Fast-skipping remaining OpenAlex queries for this harvest.", msg);
                                break facultyLoop;
                            }
                            log.warn("OpenAlex author query failed for {}: {}", queryAuthorName, msg);
                            break;
                        }

                        throttle(props.getThrottleMs());
                    }
                }
            }

            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("OpenAlex harvest completed: {} publications harvested in {} request(s), {} ms",
                    harvested.size(), requestsMade, durationMs);
            return HarvestResult.ok(SOURCE_NAME, harvested, OffsetDateTime.now(), requestsMade, durationMs,
                    "Harvested " + harvested.size() + " works from OpenAlex");

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.error("OpenAlex harvest failed: {}", e.getMessage(), e);
            return HarvestResult.failed(SOURCE_NAME, requestsMade, durationMs, e.getMessage());
        }
    }

    private void processWork(JsonNode work, List<FsFaculty> targetFaculty, List<RawPublication> out) {
        String workId = work.path("id").asText(null);
        String doi = work.path("doi").asText(null);
        String title = work.path("title").asText(null);
        if (title == null || title.isBlank()) {
            title = work.path("display_name").asText(null);
        }
        if (title == null || title.isBlank()) {
            return;
        }

        int pubYear = work.path("publication_year").asInt(0);
        int citedBy = work.path("cited_by_count").asInt(0);
        String language = work.path("language").asText(null);

        // Host venue / journal
        JsonNode primaryLocation = work.path("primary_location");
        JsonNode source = primaryLocation.path("source");
        String pubName = source.path("display_name").asText(null);
        String issn = source.path("issn_l").asText(null);
        String aggregationType = source.path("type").asText(null);

        // Biblio
        JsonNode biblio = work.path("biblio");
        String volume = biblio.path("volume").asText(null);
        String issue = biblio.path("issue").asText(null);
        String firstPage = biblio.path("first_page").asText(null);
        String lastPage = biblio.path("last_page").asText(null);
        String pageRange = (firstPage != null && lastPage != null) ? firstPage + "-" + lastPage : firstPage;

        // Authors
        List<String> authorNamesList = new ArrayList<>();
        List<FsFaculty> matchedFacultyList = new ArrayList<>();

        JsonNode authorships = work.path("authorships");
        for (JsonNode auth : authorships) {
            String authorDisplayName = auth.path("author").path("display_name").asText(null);
            if (authorDisplayName != null && !authorDisplayName.isBlank()) {
                authorNamesList.add(authorDisplayName.trim());
                for (FsFaculty f : targetFaculty) {
                    if (com.ecom.external.harvest.service.FacultyNameResolver.matchesAuthor(authorDisplayName, f)) {
                        if (!matchedFacultyList.contains(f)) {
                            matchedFacultyList.add(f);
                        }
                    }
                }
            }
        }

        String combinedAuthors = String.join(" | ", authorNamesList);

        // For each matched faculty, create a RawPublication row
        for (FsFaculty faculty : matchedFacultyList) {
            RawPublication raw = RawPublication.builder()
                    .targetFsUserId(faculty.getFsUserId())
                    .externalId(workId)
                    .openalexId(workId)
                    .doi(doi)
                    .title(title)
                    .publicationName(pubName)
                    .publicationYear(pubYear > 0 ? pubYear : null)
                    .citedBy(citedBy)
                    .authorNames(combinedAuthors)
                    .aggregationType(aggregationType)
                    .issn(issn)
                    .volume(volume)
                    .issue(issue)
                    .pageRange(pageRange)
                    .url(doi != null ? doi : workId)
                    .language(language)
                    .dataSource(SOURCE_NAME)
                    .rawMetadataJson(work.toString())
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
