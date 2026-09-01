package com.ecom.external.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.ecom.external.config.CpWebProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the College of Computing's public staff directory.
 *
 * <p>The site at {@code computing.kku.ac.th/people} renders in the browser from
 * {@code /api/v1/user/list}, so that endpoint is what is read here. It needs no
 * credentials — the same data any visitor sees.
 *
 * <p>Only what this application has a use for is kept: the address, the Thai and
 * English name and rank, and the photo. Everything else the endpoint returns
 * (biographies, publication lists, teaching history) is left where it is.
 */
@Service
public class CpWebClient {

    /**
     * {@code userLocalized} carries one entry per language, tagged by
     * {@code languageId}. These are the two the directory publishes.
     */
    private static final int LANGUAGE_ID_THAI = 1;
    private static final int LANGUAGE_ID_ENGLISH = 2;

    private static final Logger log = LoggerFactory.getLogger(CpWebClient.class);

    /** A guard against a paging bug turning into an endless loop of requests. */
    private static final int MAX_PAGES = 50;

    private final CpWebProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;

    public CpWebClient(CpWebProperties props) {
        this.props = props;
        this.http = RestClient.builder().requestFactory(requestFactory(props)).build();
    }

    private static ClientHttpRequestFactory requestFactory(CpWebProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int connectSec = props != null && props.getConnectTimeoutSeconds() > 0 ? props.getConnectTimeoutSeconds() : 5;
        int readSec = props != null && props.getReadTimeoutSeconds() > 0 ? props.getReadTimeoutSeconds() : 15;
        factory.setConnectTimeout(Duration.ofSeconds(connectSec));
        factory.setReadTimeout(Duration.ofSeconds(readSec));
        return factory;
    }

    /** Everyone listed in the directory, across as many pages as it takes. */
    public List<CpPerson> fetchAll() {
        return fetchAllWithFallback().people();
    }

    /**
     * The same read as {@link #fetchAll()}, but saying where the answer came from.
     *
     * <p>A run that fell back to the snapshot is not a failure — the caller can
     * still apply every field — but it is not fresh either, and the person reading
     * the sync page deserves to be told which of the two they are looking at.
     */
    public DirectoryFetch fetchAllWithFallback() {
        List<CpPerson> people = new ArrayList<>();
        int page = 1;
        int totalPages = 1;
        boolean liveSuccess = false;

        while (page <= totalPages && page <= MAX_PAGES) {
            JsonNode body = get(props.listEndpoint(page));
            if (body == null) {
                break;
            }

            JsonNode data = body.path("data");
            totalPages = data.path("pager").path("totalPages").asInt(1);

            for (JsonNode item : data.path("items")) {
                toPerson(item).ifPresent(people::add);
            }
            liveSuccess = true;
            page++;
        }

        if (liveSuccess && !people.isEmpty()) {
            log.info("Read {} people from the college directory (live API)", people.size());
            if (props != null && props.isFallbackCacheEnabled()) {
                saveSnapshot(people);
            }
            return new DirectoryFetch(people, false);
        }

        if (props != null && props.isFallbackCacheEnabled()) {
            List<CpPerson> cached = loadSnapshot();
            if (!cached.isEmpty()) {
                log.warn("College directory unreachable — serving {} people from the local snapshot instead",
                        cached.size());
                return new DirectoryFetch(cached, true);
            }
        }

        log.info("Read {} people from the college directory", people.size());
        return new DirectoryFetch(people, false);
    }

    /**
     * Downloads one photo.
     *
     * @return the bytes, or empty when the image cannot be fetched — a missing
     *         photo is not a reason to fail the run
     */
    public Optional<byte[]> fetchImage(String url) {
        try {
            byte[] bytes = http.get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);
            return Optional.ofNullable(bytes);
        } catch (ResourceAccessException e) {
            log.warn("Could not fetch directory photo due to timeout/connection error ({}): {}", url, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Could not fetch a directory photo ({}): {}", url, e.toString());
            return Optional.empty();
        }
    }

    /**
     * One person, or empty when the record carries no address.
     *
     * <p>The address is how a directory entry is tied to an account here, so a
     * record without one cannot be matched to anything and is not worth carrying.
     *
     * <p>The biographies, publication lists and teaching histories the endpoint
     * also returns are deliberately dropped. They are HTML written for a public
     * web page, this system has nowhere to put them, and carrying untrusted markup
     * into a form that renders it is a problem nobody asked for. Dates of birth
     * are skipped for the same reason in reverse: personal data with no use here.
     */
    private Optional<CpPerson> toPerson(JsonNode item) {
        String email = text(item, "email");
        if (email == null) {
            return Optional.empty();
        }

        JsonNode localizedList = item.path("userLocalized");
        JsonNode thai = localeFor(localizedList, LANGUAGE_ID_THAI);
        JsonNode english = localeFor(localizedList, LANGUAGE_ID_ENGLISH);

        return Optional.of(new CpPerson(
                email.trim().toLowerCase(),
                text(thai, "firstname"),
                text(thai, "lastname"),
                academicRank(thai, "name"),
                academicRank(thai, "shortName"),
                text(english, "firstname"),
                text(english, "lastname"),
                academicRank(english, "name"),
                // The English entries disagree with themselves about what the
                // abbreviation is called: some carry "shortPrefix", others
                // "shortName". Try both rather than lose half of them.
                academicRank(english, "shortName", "shortPrefix"),
                // Named "academicPosition" upstream, but it holds the degree
                // programme the person belongs to — a department, not a rank.
                text(thai, "academicPosition"),
                text(item.path("image"), "url"),
                text(item, "slug"),
                item.path("isActived").asBoolean(false)));
    }

    /**
     * Picks one language out of {@code userLocalized}.
     *
     * <p>
     * This used to read index 0 and nothing else, which silently discarded the
     * English half of every record. The directory carries one entry per language,
     * and the English one holds a properly separated English given and family
     * name — something no other feed we have provides, since the FS directory
     * only sends a single combined string.
     *
     * <p>
     * Thai falls back to the first entry when no entry declares a language, so a
     * record missing the marker still yields a name instead of nothing. English
     * has no such fallback: guessing that an unlabelled entry is English would put
     * Thai text into the English columns.
     */
    private JsonNode localeFor(JsonNode localizedList, int languageId) {
        for (JsonNode entry : localizedList) {
            if (entry.path("languageId").asInt(-1) == languageId) {
                return entry;
            }
        }
        return languageId == LANGUAGE_ID_THAI
                ? localizedList.path(0)
                : localizedList.path(-1); // missing node
    }

    /**
     * The academic rank, assembled from the prefix list.
     *
     * <p>Upstream this arrives as a JSON array inside a string, holding one entry
     * per component — {@code ผู้ช่วยศาสตราจารย์} and {@code ดร.} are separate — so
     * they are joined back into the single label people actually write.
     *
     * @param fields the key to read, in order of preference. {@code name} gives
     *               the full form and {@code shortName} the ผศ./รศ. form; the
     *               English entries sometimes name the latter
     *               {@code shortPrefix} instead, so more than one may be tried.
     */
    private String academicRank(JsonNode localized, String... fields) {
        String raw = text(localized, "prefix");
        if (raw == null) {
            return null;
        }
        try {
            JsonNode parts = mapper.readTree(raw);
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : parts) {
                for (String field : fields) {
                    String value = text(part, field);
                    if (value != null) {
                        if (sb.length() > 0 && !value.startsWith(".")) {
                            sb.append(" ");
                        }
                        sb.append(value);
                        break;
                    }
                }
            }
            return sb.isEmpty() ? null : sb.toString().trim();
        } catch (Exception e) {
            log.debug("Could not read a prefix value from the directory: {}", e.toString());
            return null;
        }
    }

    /**
     * One request, or {@code null} when the directory could not answer.
     *
     * <p>Only a connection or read timeout is retried: those are the failures a
     * second attempt a moment later can actually fix. An HTTP status or a
     * malformed body will say the same thing however many times it is asked, so
     * those give up at once.
     *
     * <p>Nothing here throws. This is background enrichment — the sync it feeds
     * has other sources, and a college web server that is down is not a reason to
     * fail the run, only to say so at {@code WARN}.
     */
    private JsonNode get(String url) {
        int retries = props != null ? Math.max(0, props.getMaxRetryAttempts()) : 0;
        String lastFailure = null;

        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                String raw = http.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(String.class);
                return raw == null || raw.isBlank() ? null : mapper.readTree(raw);
            } catch (ResourceAccessException e) {
                lastFailure = e.getMessage();
                if (attempt == retries || !backoff(attempt)) {
                    break;
                }
                log.debug("College directory attempt {} of {} timed out for {}, retrying",
                        attempt + 1, retries + 1, url);
            } catch (RestClientResponseException e) {
                log.warn("College directory returned HTTP {} for {}: {}",
                        e.getStatusCode().value(), url, e.getMessage());
                return null;
            } catch (Exception e) {
                log.error("College directory request failed for {}: {}", url, e.toString());
                return null;
            }
        }

        log.warn("College directory unreachable after {} attempt(s) (connection/read timeout): {} — {}",
                retries + 1, url, lastFailure);
        return null;
    }

    /** @return false when the wait was interrupted, meaning stop retrying */
    private boolean backoff(int attempt) {
        try {
            Thread.sleep(300L * (attempt + 1));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Path resolveSnapshotPath() {
        if (props != null && props.getSnapshotFilePath() != null && !props.getSnapshotFilePath().isBlank()) {
            return Path.of(props.getSnapshotFilePath()).toAbsolutePath().normalize();
        }
        return Path.of("uploads/cache/cp_directory_snapshot.json").toAbsolutePath().normalize();
    }

    /**
     * Persists the latest directory listing to local disk as a fallback snapshot.
     */
    public synchronized void saveSnapshot(List<CpPerson> people) {
        if (people == null || people.isEmpty()) {
            return;
        }
        try {
            Path path = resolveSnapshotPath();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), people);
            log.debug("Saved directory snapshot with {} entries to {}", people.size(), path);
        } catch (Exception e) {
            log.warn("Could not save college directory snapshot: {}", e.getMessage());
        }
    }

    /**
     * Loads the last known good directory listing from the local snapshot file.
     */
    public synchronized List<CpPerson> loadSnapshot() {
        try {
            Path path = resolveSnapshotPath();
            if (Files.exists(path) && Files.isRegularFile(path) && Files.size(path) > 0) {
                List<CpPerson> people = mapper.readValue(path.toFile(), new TypeReference<List<CpPerson>>() {});
                if (people != null && !people.isEmpty()) {
                    return people;
                }
            }
        } catch (Exception e) {
            log.warn("Could not load college directory snapshot from fallback cache: {}", e.getMessage());
        }
        return List.of();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String s = value.asText().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * A directory read, and where it came from.
     *
     * @param usedFallbackCache true when the live API could not be reached and the
     *                          people listed are the last snapshot saved to disk
     */
    public record DirectoryFetch(List<CpPerson> people, boolean usedFallbackCache) {
    }

    /**
     * One entry from the college directory, reduced to what is usable here.
     *
     * @param academicRank      full form, e.g. {@code ผู้ช่วยศาสตราจารย์ดร.}
     * @param academicRankShort abbreviated form, e.g. {@code ผศ.ดร.}
     * @param programme         the degree programme, used as the department
     */
    public record CpPerson(String email,
            String firstName,
            String lastName,
            String academicRank,
            String academicRankShort,
            String firstNameEn,
            String lastNameEn,
            String academicRankEn,
            String academicRankShortEn,
            String programme,
            String imagePath,
            String slug,
            boolean active) {

        public String fullName() {
            return ((firstName == null ? "" : firstName) + " "
                    + (lastName == null ? "" : lastName)).trim();
        }
    }
}
