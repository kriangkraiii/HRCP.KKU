package com.ecom.external.harvest.adapter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.ecom.external.harvest.PublicationSourceAdapter;
import com.ecom.external.harvest.config.HarvestProperties;
import com.ecom.external.harvest.model.HarvestContext;
import com.ecom.external.harvest.model.HarvestResult;
import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.service.EnglishNameSplitter;

/**
 * Harvester adapter for Thai Journals Online (ThaiJO) using the OAI-PMH protocol.
 * Filters articles by KKU and College of Computing affiliation and matches authors.
 */
@Component
public class ThaijoAdapter implements PublicationSourceAdapter {

    public static final String SOURCE_NAME = "THAIJO";
    private static final Logger log = LoggerFactory.getLogger(ThaijoAdapter.class);

    private final HarvestProperties.ThaijoProps props;
    private final RestClient restClient;
    private final DocumentBuilderFactory xmlFactory;

    public ThaijoAdapter(HarvestProperties harvestProperties) {
        this.props = harvestProperties.getThaijo();
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "HRCP-KKU-Academic/1.0 (OAI-PMH Client)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE + ", text/xml")
                .requestFactory(requestFactory(props))
                .build();

        // Secure XML parser configuration against XXE
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        this.xmlFactory = dbf;
    }

    private static ClientHttpRequestFactory requestFactory(HarvestProperties.ThaijoProps props) {
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
        return props.isEnabled() && props.getOaiEndpoint() != null && !props.getOaiEndpoint().isBlank();
    }

    @Override
    public HarvestResult harvest(HarvestContext context) {
        if (!isEnabled()) {
            return HarvestResult.skipped(SOURCE_NAME, "ThaiJO adapter is disabled");
        }

        long startedAt = System.currentTimeMillis();
        int requestsMade = 0;
        List<RawPublication> harvested = new ArrayList<>();

        Map<String, FsFaculty> facultyByName = buildFacultyNameIndex(context.targetFaculty());

        String resumptionToken = null;
        int page = 1;
        int maxPages = 30;

        try {
            String baseUrl = props.getOaiEndpoint();
            String fromDate = context.yearFrom() + "-01-01";

            while (page <= maxPages) {
                String requestUrl;
                if (resumptionToken == null || resumptionToken.isBlank()) {
                    requestUrl = baseUrl + "?verb=ListRecords&metadataPrefix=oai_dc&from=" + fromDate;
                } else {
                    requestUrl = baseUrl + "?verb=ListRecords&resumptionToken=" + resumptionToken;
                }

                log.debug("ThaiJO OAI-PMH harvest page {}: {}", page, requestUrl);
                requestsMade++;

                String xml = restClient.get()
                        .uri(requestUrl)
                        .retrieve()
                        .body(String.class);

                if (xml == null || xml.isBlank()) {
                    break;
                }

                DocumentBuilder builder = xmlFactory.newDocumentBuilder();
                Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

                NodeList recordNodes = doc.getElementsByTagNameNS("http://www.openarchives.org/OAI/2.0/", "record");
                if (recordNodes.getLength() == 0) {
                    recordNodes = doc.getElementsByTagName("record");
                }

                for (int i = 0; i < recordNodes.getLength(); i++) {
                    Element record = (Element) recordNodes.item(i);
                    processRecord(record, facultyByName, harvested);
                }

                // Check resumption token
                NodeList tokenNodes = doc.getElementsByTagNameNS("http://www.openarchives.org/OAI/2.0/", "resumptionToken");
                if (tokenNodes.getLength() == 0) {
                    tokenNodes = doc.getElementsByTagName("resumptionToken");
                }

                if (tokenNodes.getLength() > 0 && tokenNodes.item(0).getTextContent() != null
                        && !tokenNodes.item(0).getTextContent().isBlank()) {
                    resumptionToken = tokenNodes.item(0).getTextContent().trim();
                } else {
                    break;
                }

                throttle(props.getThrottleMs());
                page++;
            }

            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("ThaiJO harvest completed: {} publications harvested in {} request(s), {} ms",
                    harvested.size(), requestsMade, durationMs);
            return HarvestResult.ok(SOURCE_NAME, harvested, OffsetDateTime.now(), requestsMade, durationMs,
                    "Harvested " + harvested.size() + " works from ThaiJO");

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.error("ThaiJO harvest failed: {}", e.getMessage(), e);
            return HarvestResult.failed(SOURCE_NAME, requestsMade, durationMs, e.getMessage());
        }
    }

    private void processRecord(Element record, Map<String, FsFaculty> facultyByName, List<RawPublication> out) {
        // Skip deleted records
        Element header = (Element) record.getElementsByTagName("header").item(0);
        if (header != null && "deleted".equals(header.getAttribute("status"))) {
            return;
        }

        String oaiIdentifier = header != null ? getTextChild(header, "identifier") : null;

        Element metadata = (Element) record.getElementsByTagName("metadata").item(0);
        if (metadata == null) {
            return;
        }

        // Dublin Core fields
        List<String> titles = getTextsByTagName(metadata, "dc:title", "title");
        String title = titles.isEmpty() ? null : titles.get(0);
        if (title == null || title.isBlank()) {
            return;
        }

        List<String> creators = getTextsByTagName(metadata, "dc:creator", "creator");
        List<String> dates = getTextsByTagName(metadata, "dc:date", "date");
        List<String> identifiers = getTextsByTagName(metadata, "dc:identifier", "identifier");
        List<String> sources = getTextsByTagName(metadata, "dc:source", "source");
        List<String> descriptions = getTextsByTagName(metadata, "dc:description", "description");
        List<String> languages = getTextsByTagName(metadata, "dc:language", "language");
        List<String> types = getTextsByTagName(metadata, "dc:type", "type");

        // Parse year
        Integer pubYear = null;
        if (!dates.isEmpty()) {
            String dateStr = dates.get(0).trim();
            if (dateStr.length() >= 4) {
                try {
                    pubYear = Integer.parseInt(dateStr.substring(0, 4));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Parse DOI and URL from identifiers
        String doi = null;
        String articleUrl = null;
        for (String id : identifiers) {
            String lower = id.toLowerCase();
            if (lower.contains("doi.org/") || lower.startsWith("10.")) {
                doi = id.replace("https://doi.org/", "").replace("http://doi.org/", "").trim();
            } else if (lower.startsWith("http://") || lower.startsWith("https://")) {
                articleUrl = id.trim();
            }
        }

        // Publication venue / journal
        String pubName = sources.isEmpty() ? null : sources.get(0);
        String abstractText = descriptions.isEmpty() ? null : String.join("\n", descriptions);
        String language = languages.isEmpty() ? null : languages.get(0);
        String type = types.isEmpty() ? null : types.get(0);

        // Filter and match authors
        boolean affiliationMatched = isAffiliationMatched(abstractText, pubName);
        List<FsFaculty> matchedFaculty = new ArrayList<>();

        for (String creator : creators) {
            FsFaculty match = matchFaculty(creator, facultyByName);
            if (match != null && !matchedFaculty.contains(match)) {
                matchedFaculty.add(match);
            }
        }

        // If faculty name matched or affiliation keywords matched with known faculty
        if (matchedFaculty.isEmpty() && !affiliationMatched) {
            return;
        }

        String combinedAuthors = String.join(" | ", creators);

        for (FsFaculty faculty : matchedFaculty) {
            RawPublication raw = RawPublication.builder()
                    .targetFsUserId(faculty.getFsUserId())
                    .externalId(oaiIdentifier != null ? oaiIdentifier : doi)
                    .doi(doi)
                    .title(title)
                    .publicationName(pubName)
                    .publicationYear(pubYear)
                    .authorNames(combinedAuthors)
                    .abstractText(abstractText)
                    .aggregationType(type)
                    .url(articleUrl != null ? articleUrl : (doi != null ? "https://doi.org/" + doi : null))
                    .language(language)
                    .dataSource(SOURCE_NAME)
                    .rawMetadataJson("{\"oai_identifier\":\"" + oaiIdentifier + "\",\"title\":\"" + title.replace("\"", "\\\"") + "\"}")
                    .build();

            out.add(raw);
        }
    }

    private boolean isAffiliationMatched(String description, String source) {
        String combined = (description != null ? description : "") + " " + (source != null ? source : "");
        String lower = combined.toLowerCase();
        for (String kw : props.getAffiliationKeywords()) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getTextsByTagName(Element parent, String... tagNames) {
        List<String> list = new ArrayList<>();
        for (String tag : tagNames) {
            NodeList nodes = parent.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                String text = nodes.item(i).getTextContent();
                if (text != null && !text.isBlank()) {
                    list.add(text.trim());
                }
            }
        }
        return list;
    }

    private static String getTextChild(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
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
