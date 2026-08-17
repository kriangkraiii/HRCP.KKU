package com.ecom.external.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.ecom.external.config.FsApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin HTTP client for the Fund Management external API.
 *
 * <p>Three behaviours matter here and are all deliberate:
 * <ul>
 *   <li><b>Paging</b> — callers get every row; {@code paging.total} drives the loop.</li>
 *   <li><b>Throttling</b> — a fixed pause between calls keeps us inside the
 *       100 requests/minute budget without needing a token bucket.</li>
 *   <li><b>429 handling</b> — {@code Retry-After} is honoured rather than
 *       hammering the upstream, which would only extend the ban.</li>
 * </ul>
 *
 * <p>The API key is sent as a Bearer header and never logged; failures log the
 * status and path only.
 */
@Service
public class FsApiClient {

    private static final Logger log = LoggerFactory.getLogger(FsApiClient.class);

    /** Upstream refuses anything that is not RFC 3339. */
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final int MAX_RETRIES = 3;

    private final FsApiProperties props;
    private final RestClient restClient;

    /**
     * Responses are read as text and parsed here rather than letting the HTTP
     * message converters do it. Spring Boot 4 wires Jackson 3 converters while
     * this codebase is written against Jackson 2, and handing a Jackson 2
     * {@code JsonNode} to a Jackson 3 converter fails at runtime.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /** Counts requests for the sync report; reset by {@link #resetRequestCount()}. */
    private int requestCount;

    public FsApiClient(FsApiProperties props) {
        this.props = props;
        // Built directly rather than injecting RestClient.Builder: this client
        // needs its own base URL and timeouts, and must not pick up interceptors
        // that a shared application-wide builder might carry.
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(requestFactory())
                .build();
    }

    /** Upstream is a remote campus service — fail fast rather than hang a sync thread. */
    private static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }

    public void resetRequestCount() {
        requestCount = 0;
    }

    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Pulls the faculty directory, following pagination to the end.
     *
     * @param updatedSince incremental cursor; {@code null} performs a full pull
     */
    public List<JsonNode> fetchUsers(OffsetDateTime updatedSince) {
        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        int total;

        do {
            UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/users")
                    .queryParam("limit", props.getPageSize())
                    .queryParam("offset", offset);
            if (updatedSince != null) {
                uri.queryParam("updated_since", RFC3339.format(updatedSince));
            }

            JsonNode body = get(uri.build().toUriString());
            total = collect(body, all);
            offset += props.getPageSize();
        } while (offset < total);

        log.info("Fetched {} faculty record(s) from upstream", all.size());
        return all;
    }

    /**
     * Pulls publications for one batch of faculty ids.
     *
     * <p>Callers are expected to have split the faculty list already — see
     * {@link #partition(List, int)} — so this method issues one request series
     * per batch and pages through it.
     */
    public List<JsonNode> fetchPublications(Collection<Long> userIds, int yearFrom, Integer yearTo) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        StringJoiner ids = new StringJoiner(",");
        userIds.forEach(id -> ids.add(String.valueOf(id)));

        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        int total;

        do {
            UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/scopus/publications")
                    .queryParam("user_ids", ids.toString())
                    .queryParam("year_from", yearFrom)
                    .queryParam("limit", props.getPageSize())
                    .queryParam("offset", offset);
            if (yearTo != null) {
                uri.queryParam("year_to", yearTo);
            }

            JsonNode body = get(uri.build().toUriString());
            total = collect(body, all);
            offset += props.getPageSize();
        } while (offset < total);

        return all;
    }

    /** Appends {@code data[]} to {@code sink} and returns {@code paging.total}. */
    private int collect(JsonNode body, List<JsonNode> sink) {
        if (body == null) {
            return 0;
        }
        JsonNode data = body.path("data");
        if (data.isArray()) {
            data.forEach(sink::add);
        }
        return body.path("paging").path("total").asInt(sink.size());
    }

    /**
     * Issues one GET, retrying on 429 and 5xx.
     *
     * <p>Returns {@code null} rather than throwing when the call is ultimately
     * unrecoverable, so one failed batch does not abort a whole nightly sync.
     */
    private JsonNode get(String uri) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            throttle();
            try {
                requestCount++;
                return restClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getKey())
                        .exchange((request, response) -> {
                            HttpStatusCode status = response.getStatusCode();
                            String body = readBody(response);
                            if (status.value() == 429) {
                                throw new RateLimitedException(retryAfterMs(response.getHeaders()));
                            }
                            if (status.isError()) {
                                throw new UpstreamException(status.value(), errorCode(body));
                            }
                            return body == null ? null : mapper.readTree(body);
                        });
            } catch (RateLimitedException e) {
                log.warn("Rate limited on {} (attempt {}/{}), waiting {} ms",
                        safePath(uri), attempt, MAX_RETRIES, e.retryAfterMs);
                sleep(e.retryAfterMs);
            } catch (UpstreamException e) {
                // 4xx other than 429 will not fix themselves — stop immediately.
                if (e.status < 500) {
                    log.error("Upstream rejected {}: HTTP {} {}", safePath(uri), e.status, e.code);
                    return null;
                }
                log.warn("Upstream error on {}: HTTP {} (attempt {}/{})",
                        safePath(uri), e.status, attempt, MAX_RETRIES);
                sleep(1000L * attempt);
            } catch (Exception e) {
                log.warn("Call to {} failed (attempt {}/{}): {}",
                        safePath(uri), attempt, MAX_RETRIES, e.toString());
                sleep(1000L * attempt);
            }
        }
        log.error("Giving up on {} after {} attempts", safePath(uri), MAX_RETRIES);
        return null;
    }

    private void throttle() {
        sleep(props.getThrottleMs());
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(Duration.ofMillis(ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long retryAfterMs(HttpHeaders headers) {
        String value = headers.getFirst("Retry-After");
        if (value != null) {
            try {
                return Long.parseLong(value.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                // Header may be a HTTP-date; fall through to the default.
            }
        }
        return 60_000L;
    }

    /** Reads the raw response body, tolerating an empty one. */
    private String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try (InputStream in = response.getBody()) {
            byte[] bytes = in.readAllBytes();
            return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Pulls the {@code code} field out of the documented error envelope. */
    private String errorCode(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            return mapper.readTree(body).path("code").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /** Strips the query string so faculty ids and cursors stay out of the logs. */
    private String safePath(String uri) {
        int q = uri.indexOf('?');
        return q < 0 ? uri : uri.substring(0, q);
    }

    /** Splits a list into fixed-size batches. */
    public static <T> List<List<T>> partition(List<T> source, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            batches.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return batches;
    }

    /** Reads a nullable text field, collapsing blanks to {@code null}. */
    public static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    public static Integer integer(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }

    public static Long longValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }

    public static Double doubleValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asDouble();
    }

    private static final class RateLimitedException extends RuntimeException {
        private final transient long retryAfterMs;

        RateLimitedException(long retryAfterMs) {
            super(null, null, false, false);
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static final class UpstreamException extends RuntimeException {
        private final transient int status;
        private final transient String code;

        UpstreamException(int status, String code) {
            super(null, null, false, false);
            this.status = status;
            this.code = code;
        }
    }

    /** Exposed for the admin status page. */
    public Map<String, Object> describe() {
        return Map.of(
                "baseUrl", props.getBaseUrl(),
                "enabled", props.isEnabled(),
                "keyConfigured", props.getKey() != null && !props.getKey().isBlank());
    }
}
