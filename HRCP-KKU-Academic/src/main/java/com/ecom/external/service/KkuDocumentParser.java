package com.ecom.external.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecom.external.model.KkuRegulationDoc;

/**
 * Parser for KKU HR regulations and announcements page (https://hr2.kku.ac.th/?page_id=5546).
 */
@Component
public class KkuDocumentParser {

    private static final Logger log = LoggerFactory.getLogger(KkuDocumentParser.class);
    private static final Pattern YEAR_4DIGIT_PATTERN = Pattern.compile("25[0-9]{2}");
    private static final Pattern YEAR_2DIGIT_PATTERN = Pattern.compile("(?:/|_|\\b)([567][0-9])\\b");

    public static class ParsedCategory {
        private final String category;
        private final String icon;
        private final String color;
        private final List<KkuRegulationDoc> docs;

        public ParsedCategory(String category, String icon, String color, List<KkuRegulationDoc> docs) {
            this.category = category;
            this.icon = icon;
            this.color = color;
            this.docs = docs;
        }

        public String getCategory() { return category; }
        public String getIcon() { return icon; }
        public String getColor() { return color; }
        public List<KkuRegulationDoc> getDocs() { return docs; }
    }

    /**
     * Parses the HTML of page_id=5546 into a list of KkuRegulationDoc entities.
     */
    public List<KkuRegulationDoc> parse(String html) {
        List<KkuRegulationDoc> results = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return results;
        }

        try {
            Document doc = Jsoup.parse(html, "https://hr2.kku.ac.th");
            Elements columns = doc.select(".fusion_builder_column");

            String currentCategory = "ข้อบังคับมหาวิทยาลัยขอนแก่น";
            int globalOrder = 0;

            for (Element col : columns) {
                // Check if this column has a section header
                Elements headerEl = col.select(".fusion-title h2, .fusion-title h3, .fusion-title h4, .fusion-title h5, h2, h3, h4, h5");
                if (!headerEl.isEmpty()) {
                    String headerText = headerEl.first().text().trim();
                    if (!headerText.isBlank() && !headerText.equalsIgnoreCase("พนักงานมหาวิทยาลัย") && !headerText.contains("Home")) {
                        currentCategory = mapCategory(headerText);
                    }
                }

                // Extract PDF links in this column
                Elements links = col.select("a[href*='.pdf'], a[href*='uploads']");
                for (Element a : links) {
                    String href = a.attr("abs:href");
                    if (href == null || href.isBlank() || !href.toLowerCase().contains(".pdf")) {
                        continue;
                    }

                    String rawTitle = a.text().trim();
                    if (rawTitle.isBlank()) {
                        continue;
                    }

                    // Remove trailing icon texts or extra whitespaces
                    String title = cleanTitle(rawTitle);
                    String fileKey = extractFileKey(href);
                    String year = extractYear(title + " " + href);

                    KkuRegulationDoc docEntity = new KkuRegulationDoc();
                    docEntity.setCategory(currentCategory);
                    docEntity.setCategoryIcon(getCategoryIcon(currentCategory));
                    docEntity.setCategoryColor(getCategoryColor(currentCategory));
                    docEntity.setTitle(title);
                    docEntity.setFileUrl(href);
                    docEntity.setFileKey(fileKey);
                    docEntity.setDisplayOrder(++globalOrder);
                    docEntity.setPublishedYear(year);

                    // If title contains 🆕 or 2569 / newest tag
                    if (rawTitle.contains("🆕") || rawTitle.contains("ใหม่") || title.contains("2569")) {
                        docEntity.setIsNew(true);
                    }

                    results.add(docEntity);
                }
            }

            // If column traversal found nothing, fallback to searching all PDF links on the page
            if (results.isEmpty()) {
                Elements allPdfLinks = doc.select("a[href*='.pdf']");
                for (Element a : allPdfLinks) {
                    String href = a.attr("abs:href");
                    String rawTitle = a.text().trim();
                    if (!rawTitle.isBlank() && href != null && href.toLowerCase().contains(".pdf")) {
                        String title = cleanTitle(rawTitle);
                        String category = guessCategoryFromTitle(title);
                        KkuRegulationDoc item = new KkuRegulationDoc(category, title, href, extractFileKey(href), ++globalOrder);
                        item.setCategoryIcon(getCategoryIcon(category));
                        item.setCategoryColor(getCategoryColor(category));
                        item.setPublishedYear(extractYear(title));
                        if (rawTitle.contains("🆕") || title.contains("2569")) item.setIsNew(true);
                        results.add(item);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse KKU HR document HTML: {}", e.getMessage(), e);
        }

        return results;
    }

    private String mapCategory(String header) {
        String h = header.trim();
        if (h.contains("ข้อบังคับ")) return "ข้อบังคับมหาวิทยาลัยขอนแก่น";
        if (h.contains("ประกาศ")) return "ประกาศมหาวิทยาลัยขอนแก่น";
        if (h.contains("แนบท้าย")) return "เอกสารแนบท้ายข้อบังคับฯ";
        if (h.contains("กลุ่ม 4") || h.contains("เฉพาะด้าน")) return "คำจำกัดความฯ ผลงานทางวิชาการ (กลุ่ม 4 เฉพาะด้าน)";
        if (h.contains("คำจำกัดความ") || h.contains("กลุ่ม 1") || h.contains("กลุ่ม 2") || h.contains("กลุ่ม 3")) return "คำจำกัดความฯ ผลงานทางวิชาการ (กลุ่ม 1-3)";
        return h;
    }

    private String guessCategoryFromTitle(String title) {
        if (title.startsWith("ข้อบังคับ")) return "ข้อบังคับมหาวิทยาลัยขอนแก่น";
        if (title.startsWith("ประกาศ")) return "ประกาศมหาวิทยาลัยขอนแก่น";
        if (title.contains("แนบท้าย") || title.contains("ลักษณะการมีส่วนร่วม")) return "เอกสารแนบท้ายข้อบังคับฯ";
        if (title.contains("เฉพาะด้าน") || title.startsWith("4.")) return "คำจำกัดความฯ ผลงานทางวิชาการ (กลุ่ม 4 เฉพาะด้าน)";
        if (title.startsWith("กลุ่ม") || title.startsWith("1.") || title.startsWith("2.") || title.startsWith("3.")) return "คำจำกัดความฯ ผลงานทางวิชาการ (กลุ่ม 1-3)";
        return "ข้อบังคับมหาวิทยาลัยขอนแก่น";
    }

    private String getCategoryIcon(String category) {
        if (category.contains("ข้อบังคับ")) return "fas fa-landmark";
        if (category.contains("ประกาศ")) return "fas fa-scroll";
        if (category.contains("แนบท้าย")) return "fas fa-paperclip";
        if (category.contains("เฉพาะด้าน") || category.contains("กลุ่ม 4")) return "fas fa-star";
        if (category.contains("คำจำกัดความ") || category.contains("กลุ่ม 1")) return "fas fa-flask";
        return "fas fa-file-alt";
    }

    private String getCategoryColor(String category) {
        if (category.contains("ข้อบังคับ")) return "var(--color-primary-medium, #1565c0)";
        if (category.contains("ประกาศ")) return "#ef6c00";
        if (category.contains("แนบท้าย")) return "#2e7d32";
        if (category.contains("เฉพาะด้าน") || category.contains("กลุ่ม 4")) return "#004d40";
        if (category.contains("คำจำกัดความ") || category.contains("กลุ่ม 1")) return "#7b1fa2";
        return "#1565c0";
    }

    private String cleanTitle(String raw) {
        return raw.replace("🆕", "")
                  .replace("NEW", "")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    private String extractFileKey(String url) {
        try {
            String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
            int lastSlash = decoded.lastIndexOf('/');
            if (lastSlash != -1) {
                return decoded.substring(lastSlash + 1).trim();
            }
            return decoded;
        } catch (Exception e) {
            int lastSlash = url.lastIndexOf('/');
            return (lastSlash != -1) ? url.substring(lastSlash + 1) : url;
        }
    }

    private String extractYear(String text) {
        if (text == null) return null;
        Matcher m4 = YEAR_4DIGIT_PATTERN.matcher(text);
        if (m4.find()) {
            return m4.group();
        }
        Matcher m2 = YEAR_2DIGIT_PATTERN.matcher(text);
        if (m2.find()) {
            return "25" + m2.group(1);
        }
        return null;
    }
}
