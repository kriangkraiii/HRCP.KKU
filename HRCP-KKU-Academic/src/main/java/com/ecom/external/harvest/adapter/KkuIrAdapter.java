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
 * Harvester adapter for KKU Institutional Repository (DSpace OAI-PMH).
 * Extracts theses, reports, and journal articles archived by KKU faculty.
 */
@Component
public class KkuIrAdapter implements PublicationSourceAdapter {

    public static final String SOURCE_NAME = "KKUIR";
    private static final Logger log = LoggerFactory.getLogger(KkuIrAdapter.class);

    private final HarvestProperties.KkuIrProps props;
    private final RestClient restClient;
    private final DocumentBuilderFactory xmlFactory;

    public KkuIrAdapter(HarvestProperties harvestProperties) {
        this.props = harvestProperties.getKkuir();
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "HRCP-KKU-Academic/1.0 (KKU-IR OAI Client)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE + ", text/xml")
                .requestFactory(requestFactory(props))
                .build();

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

    private static ClientHttpRequestFactory requestFactory(HarvestProperties.KkuIrProps props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int connectSec = props.getConnectTimeoutSeconds() > 0 ? Math.min(props.getConnectTimeoutSeconds(), 3) : 3;
        int readSec = props.getReadTimeoutSeconds() > 0 ? Math.min(props.getReadTimeoutSeconds(), 5) : 5;
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
        return props.isEnabled() && props.getOaiEndpoint() != null && !props.getOaiEndpoint().isBlank();
    }

    @Override
    public HarvestResult harvest(HarvestContext context) {
        if (!isEnabled()) {
            return HarvestResult.skipped(SOURCE_NAME, "KKU IR adapter is disabled");
        }

        long startedAt = System.currentTimeMillis();
        int requestsMade = 0;
        List<RawPublication> harvested = new ArrayList<>();

        String baseUrl = props.getOaiEndpoint();
        String fromDate = context.yearFrom() + "-01-01";

        String setConfig = props.getSet();
        List<String> targetSets = new ArrayList<>();
        if (setConfig != null && !setConfig.isBlank()) {
            for (String s : setConfig.split(",")) {
                if (!s.trim().isEmpty()) {
                    targetSets.add(s.trim());
                }
            }
        }
        if (targetSets.isEmpty()) {
            targetSets.add("col_123456789_37199");
            targetSets.add("col_123456789_37197");
        }

        try {
            long maxDurationMs = 420_000L; // 7 minutes soft deadline

            for (String currentSet : targetSets) {
                String resumptionToken = null;
                int page = 1;
                int maxPages = 30;

                while (page <= maxPages) {
                    if (System.currentTimeMillis() - startedAt > maxDurationMs) {
                        log.warn("KKU IR harvest deadline reached ({} ms); stopping early with {} works harvested",
                                System.currentTimeMillis() - startedAt, harvested.size());
                        break;
                    }

                    String requestUrl;
                    if (resumptionToken == null || resumptionToken.isBlank()) {
                        requestUrl = baseUrl + "?verb=ListRecords&metadataPrefix=oai_dc&from=" + fromDate;
                        if (currentSet != null && !currentSet.isBlank()) {
                            requestUrl += "&set=" + currentSet;
                        }
                    } else {
                        requestUrl = baseUrl + "?verb=ListRecords&resumptionToken=" + resumptionToken;
                    }

                    log.debug("KKU IR OAI-PMH harvest set={} page {}: {}", currentSet, page, requestUrl);
                    requestsMade++;

                    String xml = null;
                    try {
                        xml = restClient.get()
                                .uri(requestUrl)
                                .retrieve()
                                .body(String.class);
                    } catch (Exception e) {
                        log.warn("KKU IR request failed for set {}: {}", currentSet, e.getMessage());
                        break;
                    }

                    if (xml == null || xml.isBlank()) {
                        break;
                    }

                    DocumentBuilder builder = xmlFactory.newDocumentBuilder();
                    Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

                    // Check for OAI error response (e.g. noRecordsMatch)
                    NodeList errorNodes = doc.getElementsByTagNameNS("http://www.openarchives.org/OAI/2.0/", "error");
                    if (errorNodes.getLength() == 0) {
                        errorNodes = doc.getElementsByTagName("error");
                    }
                    if (errorNodes.getLength() > 0) {
                        String errCode = ((Element) errorNodes.item(0)).getAttribute("code");
                        String errText = errorNodes.item(0).getTextContent();
                        log.debug("KKU IR OAI-PMH notice: code={}, message={}", errCode, errText);

                        // If OAI index has lag, attempt direct collection RSS fallback
                        if (currentSet != null && currentSet.contains("_")) {
                            String cid = currentSet.substring(currentSet.lastIndexOf('_') + 1);
                            harvestRssFallback(cid, context.targetFaculty(), harvested);
                        }
                        break;
                    }

                    NodeList recordNodes = doc.getElementsByTagNameNS("http://www.openarchives.org/OAI/2.0/", "record");
                    if (recordNodes.getLength() == 0) {
                        recordNodes = doc.getElementsByTagName("record");
                    }

                    for (int i = 0; i < recordNodes.getLength(); i++) {
                        Element record = (Element) recordNodes.item(i);
                        processRecord(record, context.targetFaculty(), harvested);
                    }

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
            }

            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("KKU IR harvest completed: {} publications harvested in {} request(s), {} ms",
                    harvested.size(), requestsMade, durationMs);
            return HarvestResult.ok(SOURCE_NAME, harvested, OffsetDateTime.now(), requestsMade, durationMs,
                    "Harvested " + harvested.size() + " works from KKU IR");

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            if (msg.contains("Connection refused") || msg.contains("ConnectException") || msg.contains("timed out")) {
                log.warn("KKU IR repository unreachable ({}): {}", props.getOaiEndpoint(), msg);
                return HarvestResult.failed(SOURCE_NAME, requestsMade, durationMs, "ไม่สามารถเชื่อมต่อเซิร์ฟเวอร์ KKU IR ได้ (Connection Refused / Server Unreachable)");
            }
            log.error("KKU IR harvest failed: {}", msg, e);
            return HarvestResult.failed(SOURCE_NAME, requestsMade, durationMs, msg);
        }
    }

    private void processRecord(Element record, List<FsFaculty> targetFaculty, List<RawPublication> out) {
        Element header = (Element) record.getElementsByTagName("header").item(0);
        if (header != null && "deleted".equals(header.getAttribute("status"))) {
            return;
        }

        String oaiIdentifier = header != null ? getTextChild(header, "identifier") : null;

        Element metadata = (Element) record.getElementsByTagName("metadata").item(0);
        if (metadata == null) {
            return;
        }

        List<String> titles = getTextsByTagName(metadata, "dc:title", "title");
        String title = titles.isEmpty() ? null : titles.get(0);
        if (title == null || title.isBlank()) {
            return;
        }

        List<String> creators = getTextsByTagName(metadata,
                "dc:creator", "creator",
                "dc:contributor", "contributor",
                "dc.contributor.advisor", "dc.contributor.author");
        List<String> dates = getTextsByTagName(metadata, "dc:date", "date");
        List<String> identifiers = getTextsByTagName(metadata, "dc:identifier", "identifier");
        List<String> descriptions = getTextsByTagName(metadata, "dc:description", "description");
        List<String> types = getTextsByTagName(metadata, "dc:type", "type");
        List<String> publishers = getTextsByTagName(metadata, "dc:publisher", "publisher");

        Integer pubYear = null;
        if (!dates.isEmpty()) {
            String dateStr = dates.get(0).trim();
            if (dateStr.length() >= 4) {
                try {
                    pubYear = Integer.parseInt(dateStr.substring(0, 4));
                } catch (NumberFormatException ignored) {}
            }
        }

        String handleUrl = null;
        String doi = null;
        for (String id : identifiers) {
            String lower = id.toLowerCase();
            if (lower.contains("doi.org/") || lower.startsWith("10.")) {
                doi = id.replace("https://doi.org/", "").replace("http://doi.org/", "").trim();
            } else if (lower.contains("hdl.handle.net") || lower.startsWith("http://") || lower.startsWith("https://")) {
                handleUrl = id.trim();
            }
        }

        String pubName = publishers.isEmpty() ? "Khon Kaen University Institutional Repository" : publishers.get(0);
        String abstractText = descriptions.isEmpty() ? null : String.join("\n", descriptions);
        String type = types.isEmpty() ? null : types.get(0);

        List<FsFaculty> matchedFaculty = new ArrayList<>();
        for (String creator : creators) {
            for (FsFaculty f : targetFaculty) {
                if (com.ecom.external.harvest.service.FacultyNameResolver.matchesAuthor(creator, f)) {
                    if (!matchedFaculty.contains(f)) {
                        matchedFaculty.add(f);
                    }
                }
            }
        }

        if (matchedFaculty.isEmpty()) {
            return;
        }

        String combinedAuthors = String.join(" | ", creators);

        for (FsFaculty faculty : matchedFaculty) {
            RawPublication raw = RawPublication.builder()
                    .targetFsUserId(faculty.getFsUserId())
                    .externalId(oaiIdentifier != null ? oaiIdentifier : (handleUrl != null ? handleUrl : doi))
                    .doi(doi)
                    .title(title)
                    .publicationName(pubName)
                    .publicationYear(pubYear)
                    .authorNames(combinedAuthors)
                    .abstractText(abstractText)
                    .aggregationType(type)
                    .url(handleUrl != null ? handleUrl : (doi != null ? "https://doi.org/" + doi : null))
                    .dataSource(SOURCE_NAME)
                    .rawMetadataJson("{\"oai_identifier\":\"" + oaiIdentifier + "\",\"title\":\"" + title.replace("\"", "\\\"") + "\"}")
                    .build();

            out.add(raw);
        }
    }

    private void harvestRssFallback(String collectionId, List<FsFaculty> targetFaculty, List<RawPublication> out) {
        try {
            String rssUrl = "https://kkuir.kku.ac.th/jspui/feed/rss_2.0/123456789/" + collectionId;
            log.debug("KKU IR attempting RSS fallback for collection {}: {}", collectionId, rssUrl);
            String xml = restClient.get().uri(rssUrl).retrieve().body(String.class);
            if (xml == null || !xml.contains("<item>")) {
                return;
            }

            DocumentBuilder builder = xmlFactory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String title = getTextChild(item, "title");
                String link = getTextChild(item, "link");
                String dateStr = getTextChild(item, "dc:date");
                if (dateStr == null) dateStr = getTextChild(item, "pubDate");
                Integer pubYear = null;
                if (dateStr != null && dateStr.length() >= 4) {
                    try {
                        pubYear = Integer.parseInt(dateStr.replaceAll("^.*?(\\d{4}).*$", "$1"));
                    } catch (Exception ignored) {}
                }

                List<String> creators = getTextsByTagName(item, "dc:creator", "creator", "dc:contributor", "contributor");
                if (link != null && link.contains("/handle/123456789/")) {
                    enrichCreatorsFromItemPage(link, creators);
                }

                List<FsFaculty> matchedFaculty = new ArrayList<>();
                for (String creator : creators) {
                    for (FsFaculty f : targetFaculty) {
                        if (com.ecom.external.harvest.service.FacultyNameResolver.matchesAuthor(creator, f)) {
                            if (!matchedFaculty.contains(f)) {
                                matchedFaculty.add(f);
                            }
                        }
                    }
                }

                for (FsFaculty faculty : matchedFaculty) {
                    RawPublication raw = RawPublication.builder()
                            .targetFsUserId(faculty.getFsUserId())
                            .externalId(link)
                            .title(title)
                            .publicationName("Khon Kaen University Institutional Repository")
                            .publicationYear(pubYear)
                            .authorNames(String.join(" | ", creators))
                            .aggregationType("Thesis")
                            .url(link)
                            .dataSource(SOURCE_NAME)
                            .rawMetadataJson("{\"url\":\"" + link + "\",\"title\":\"" + (title != null ? title.replace("\"", "\\\"") : "") + "\"}")
                            .build();
                    out.add(raw);
                }
            }
        } catch (Exception e) {
            log.debug("KKU IR RSS fallback notice for collection {}: {}", collectionId, e.getMessage());
        }
    }

    private void enrichCreatorsFromItemPage(String handleUrl, List<String> creators) {
        try {
            String fullUrl = handleUrl.replace("http://", "https://") + "?mode=full";
            String html = restClient.get().uri(fullUrl).retrieve().body(String.class);
            if (html != null && html.contains("dc.contributor.advisor")) {
                html.lines().forEach(line -> {
                    if (line.contains("dc.contributor.advisor") || line.contains("dc.contributor.author")) {
                        int valIdx = line.indexOf("metadataFieldValue\">");
                        if (valIdx >= 0) {
                            int endIdx = line.indexOf("</td>", valIdx);
                            if (endIdx > valIdx) {
                                String val = line.substring(valIdx + 20, endIdx).trim();
                                val = org.springframework.web.util.HtmlUtils.htmlUnescape(val);
                                if (!val.isBlank() && !creators.contains(val)) {
                                    creators.add(val);
                                }
                            }
                        }
                    }
                });
            }
        } catch (Exception ignored) {
        }
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

    private static void throttle(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
