package com.ecom.sso;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Talks to the KKU SSONext REST service.
 *
 * <p><b>The provider signals failure with HTTP 200 and {@code "ok": false}.</b>
 * Every response is therefore checked on the {@code ok} field, never on the
 * status code alone — treating 200 as success would accept a rejected login.
 *
 * <p>Neither the client secret nor an access token is ever logged.
 */
@Service
public class KkuSsoClient {

    private static final Logger log = LoggerFactory.getLogger(KkuSsoClient.class);

    private final KkuSsoProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;

    public KkuSsoClient(KkuSsoProperties props) {
        this.props = props;
        this.http = RestClient.builder()
                .requestFactory(requestFactory())
                .build();
    }

    private static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    /**
     * Exchanges the one-time {@code code} from the login callback for an access
     * token and the caller's identity.
     *
     * @return the token payload, or empty when SSO rejected the exchange
     */
    public Optional<SsoToken> exchangeCode(String code) {
        System.out.println("==================================================================");
        System.out.println("🚀 [SSO TOKEN EXCHANGE] Sending request to KKU SSO API...");
        System.out.println("   - Endpoint: " + props.tokenEndpoint());
        System.out.println("   - App ID: " + props.getAppId());
        System.out.println("   - Client ID: " + props.getClientId());
        System.out.println("   - Redirect URL sent: " + props.getRedirectLoginUrl());
        System.out.println("   - One-time Code: " + (code != null ? (code.length() > 10 ? code.substring(0, 10) + "..." : code) : "NULL"));
        System.out.println("==================================================================");

        ObjectNode body = mapper.createObjectNode();
        body.put("code", code);
        body.put("redirectUrl", props.getRedirectLoginUrl());
        body.put("clientId", props.getClientId());
        body.put("clientSecret", props.getClientSecret());

        JsonNode response = post(props.tokenEndpoint(), body.toString(), null);
        if (response == null) {
            System.err.println("❌ [SSO TOKEN ERROR] Response is null (Network error / Connection refused / Timeout)");
            return Optional.empty();
        }

        System.out.println("📥 [SSO TOKEN RESPONSE] " + response.toString());

        if (!response.path("ok").asBoolean(false)) {
            // Documented failure shape: 200 OK with ok=false and an error code.
            String err = response.path("error").asText("unknown");
            System.err.println("❌ [SSO TOKEN REJECTED] KKU SSO returned ok=false, error=" + err);
            log.warn("SSO token exchange rejected: {}", err);
            return Optional.empty();
        }

        String accessToken = text(response, "accessToken");
        String email = text(response, "email");
        if (accessToken == null || email == null) {
            System.err.println("❌ [SSO TOKEN ERROR] Response is ok=true but missing accessToken or email");
            log.error("SSO token response was ok=true but missing accessToken or email");
            return Optional.empty();
        }

        return Optional.of(new SsoToken(
                accessToken,
                email,
                text(response, "immutableId"),
                text(response, "firstName"),
                text(response, "lastName"),
                text(response, "employeeId")));
    }

    /** Full profile for a token. Optional extra detail — a login does not depend on it. */
    public Optional<SsoProfile> fetchProfile(String accessToken) {
        JsonNode response = post(props.profileEndpoint(), "", accessToken);
        if (response == null || !response.path("ok").asBoolean(false)) {
            return Optional.empty();
        }

        JsonNode p = response.path("profile");
        if (p.isMissingNode()) {
            return Optional.empty();
        }

        return Optional.of(new SsoProfile(
                text(p, "email"),
                text(p, "userId"),
                text(p, "type"),
                text(p, "title"),
                text(p, "firstname"),
                text(p, "lastname"),
                text(p, "titleEng"),
                text(p, "firstnameEng"),
                text(p, "lastnameEng"),
                text(p, "facultyName"),
                text(p, "positionName"),
                text(p, "positionTypeName"),
                text(p, "gender"),
                text(p, "phoneNumber"),
                text(p, "personStatus")));
    }

    /** Confirms a token is still valid on the SSO side. */
    public boolean isTokenActive(String accessToken) {
        JsonNode response = post(props.statusEndpoint(), "", accessToken);
        return response != null && response.path("ok").asBoolean(false);
    }

    /**
     * One POST, returning {@code null} on any transport or protocol failure.
     *
     * <p>Responses are read as text and parsed with our own mapper: Spring Boot 4
     * registers Jackson 3 converters while this codebase is written against
     * Jackson 2.
     */
    private JsonNode post(String url, String jsonBody, String bearerToken) {
        try {
            return http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                        if (bearerToken != null) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
                        }
                    })
                    .body(jsonBody == null ? "" : jsonBody)
                    .exchange((request, response) -> {
                        String raw = readBody(response);
                        if (response.getStatusCode().isError()) {
                            System.err.println("❌ [SSO HTTP ERROR] " + safePath(url) + " returned HTTP " + response.getStatusCode().value() + " Body: " + raw);
                            log.warn("SSO call to {} returned HTTP {}",
                                    safePath(url), response.getStatusCode().value());
                            return null;
                        }
                        return raw == null || raw.isBlank() ? null : mapper.readTree(raw);
                    });
        } catch (Exception e) {
            System.err.println("❌ [SSO NETWORK EXCEPTION] Call to " + safePath(url) + " failed: " + e.getMessage());
            log.error("SSO call to {} failed: {}", safePath(url), e.toString());
            return null;
        }
    }

    private String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try (InputStream in = response.getBody()) {
            byte[] bytes = in.readAllBytes();
            return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    /** Keeps query strings out of logs. */
    private static String safePath(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    /** Identity returned alongside the access token. */
    public record SsoToken(String accessToken,
            String email,
            String immutableId,
            String firstName,
            String lastName,
            String employeeId) {
    }

    /** Optional richer profile from {@code /user.profile}. */
    public record SsoProfile(String email,
            String userId,
            String type,
            String title,
            String firstName,
            String lastName,
            String titleEng,
            String firstNameEng,
            String lastNameEng,
            String facultyName,
            String positionName,
            String positionTypeName,
            String gender,
            String phoneNumber,
            String personStatus) {
    }
}
