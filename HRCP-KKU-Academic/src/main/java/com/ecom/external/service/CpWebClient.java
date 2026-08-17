package com.ecom.external.service;

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
import org.springframework.web.client.RestClient;

import com.ecom.external.config.CpWebProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the College of Computing's public staff directory.
 *
 * <p>The site at {@code computing.kku.ac.th/people} renders in the browser from
 * {@code /api/v1/user/list}, so that endpoint is what is read here. It needs no
 * credentials — the same data any visitor sees.
 *
 * <p>Only what this application has a use for is kept: the address, the Thai
 * name, and the photo. Everything else the endpoint returns (biographies,
 * publication lists, teaching history) is left where it is.
 */
@Service
public class CpWebClient {

    private static final Logger log = LoggerFactory.getLogger(CpWebClient.class);

    /** A guard against a paging bug turning into an endless loop of requests. */
    private static final int MAX_PAGES = 50;

    private final CpWebProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;

    public CpWebClient(CpWebProperties props) {
        this.props = props;
        this.http = RestClient.builder().requestFactory(requestFactory()).build();
    }

    private static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    /** Everyone listed in the directory, across as many pages as it takes. */
    public List<CpPerson> fetchAll() {
        List<CpPerson> people = new ArrayList<>();
        int page = 1;
        int totalPages = 1;

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
            page++;
        }

        log.info("Read {} people from the college directory", people.size());
        return people;
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
        } catch (Exception e) {
            log.warn("Could not fetch a directory photo: {}", e.toString());
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

        JsonNode localized = item.path("userLocalized").path(0);

        return Optional.of(new CpPerson(
                email.trim().toLowerCase(),
                text(localized, "firstname"),
                text(localized, "lastname"),
                academicRank(localized, "name"),
                academicRank(localized, "shortName"),
                // Named "academicPosition" upstream, but it holds the degree
                // programme the person belongs to — a department, not a rank.
                text(localized, "academicPosition"),
                text(item.path("image"), "url"),
                text(item, "slug"),
                item.path("isActived").asBoolean(false)));
    }

    /**
     * The academic rank, assembled from the prefix list.
     *
     * <p>Upstream this arrives as a JSON array inside a string, holding one entry
     * per component — {@code ผู้ช่วยศาสตราจารย์} and {@code ดร.} are separate — so
     * they are joined back into the single label people actually write.
     *
     * @param field {@code name} for the full form, {@code shortName} for ผศ./รศ.
     */
    private String academicRank(JsonNode localized, String field) {
        String raw = text(localized, "prefix");
        if (raw == null) {
            return null;
        }
        try {
            JsonNode parts = mapper.readTree(raw);
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : parts) {
                String value = text(part, field);
                if (value != null) {
                    sb.append(value);
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        } catch (Exception e) {
            log.debug("Could not read a prefix value from the directory: {}", e.toString());
            return null;
        }
    }

    private JsonNode get(String url) {
        try {
            String raw = http.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return raw == null || raw.isBlank() ? null : mapper.readTree(raw);
        } catch (Exception e) {
            log.error("College directory request failed: {}", e.toString());
            return null;
        }
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
