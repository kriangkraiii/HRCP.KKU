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
 * Parser for KKU HR regulations and announcements pages — พนักงานมหาวิทยาลัย (https://hr2.kku.ac.th/?page_id=5546)
 * and ข้าราชการ (https://hr2.kku.ac.th/?page_id=5532). Both pages share one layout: Fusion builder columns, each
 * opened by a section heading and followed by PDF links.
 */
@Component
public class KkuDocumentParser {

    private static final Logger log = LoggerFactory.getLogger(KkuDocumentParser.class);
    private static final Pattern YEAR_4DIGIT_PATTERN = Pattern.compile("25[0-9]{2}");
    private static final Pattern YEAR_2DIGIT_PATTERN = Pattern.compile("(?:/|_|\\b)([567][0-9])\\b");

    /** The page's own title heading — names who the page is for, not a section of documents */
    private static final java.util.Set<String> PAGE_TITLES = java.util.Set.of("พนักงานมหาวิทยาลัย", "ข้าราชการ");

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
        return parse(html, null);
    }

    /**
     * @param audience who the page is for, put in front of every category (e.g. "ข้าราชการ") so documents from two
     *                 pages stay in separate groups — {@code null} keeps the categories as they are
     */
    public List<KkuRegulationDoc> parse(String html, String audience) {
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
                    if (!headerText.isBlank() && !PAGE_TITLES.contains(headerText) && !headerText.contains("Home")) {
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

                    String category = categoryNamedByTitle(title, currentCategory);
                    KkuRegulationDoc docEntity = new KkuRegulationDoc();
                    docEntity.setCategory(withAudience(audience, category));
                    docEntity.setCategoryIcon(getCategoryIcon(category));
                    docEntity.setCategoryColor(getCategoryColor(category));
                    docEntity.setTitle(title);
                    docEntity.setFileUrl(href);
                    docEntity.setFileKey(fileKey);
                    docEntity.setDisplayOrder(++globalOrder);
                    docEntity.setPublishedYear(year);

                    // If title contains 🆕, (ใหม่), or 2569+
                    if (rawTitle.contains("🆕") || rawTitle.contains("NEW") || rawTitle.matches(".*\\(\\s*ใหม่\\s*\\).*")
                            || title.contains("2569") || isRecentYear(year)) {
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
                        String year = extractYear(title);
                        KkuRegulationDoc item = new KkuRegulationDoc(withAudience(audience, category), title, href,
                                extractFileKey(href), ++globalOrder);
                        item.setCategoryIcon(getCategoryIcon(category));
                        item.setCategoryColor(getCategoryColor(category));
                        item.setPublishedYear(year);
                        if (rawTitle.contains("🆕") || rawTitle.contains("NEW") || rawTitle.matches(".*\\(\\s*ใหม่\\s*\\).*")
                                || title.contains("2569") || isRecentYear(year)) {
                            item.setIsNew(true);
                        }
                        results.add(item);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse KKU HR document HTML: {}", e.getMessage(), e);
        }

        return results;
    }

    private static String withAudience(String audience, String category) {
        return audience == null || audience.isBlank() ? category : audience + " · " + category;
    }

    private String mapCategory(String header) {
        String h = header.trim();
        // ก.พ.อ. ก่อน "ประกาศ" — ไม่อย่างนั้น "ประกาศ ก.พ.อ." ถูกจัดเป็นประกาศของมหาวิทยาลัย
        if (h.contains("ก.พ.อ")) return h.contains("แนวปฏิบัติ") ? "แนวปฏิบัติและหนังสือเวียน ก.พ.อ." : "ประกาศ ก.พ.อ.";
        // "เอกสารแนบท้ายข้อบังคับ…" มีคำว่าข้อบังคับอยู่ข้างใน จึงต้องตรวจก่อน
        if (h.contains("แนบท้าย")) return "เอกสารแนบท้ายข้อบังคับฯ";
        if (h.contains("ข้อบังคับ")) return "ข้อบังคับมหาวิทยาลัยขอนแก่น";
        if (h.contains("ประกาศ")) return "ประกาศมหาวิทยาลัยขอนแก่น";
        if (h.contains("แนบท้าย")) return "เอกสารแนบท้ายข้อบังคับฯ";
        if (h.contains("กลุ่ม 4") || h.contains("เฉพาะด้าน")) return "คำจำกัดความฯ ผลงานทางวิชาการ (กลุ่ม 4 เฉพาะด้าน)";
        if (h.contains("คำจำกัดความ") || h.contains("กลุ่ม 1") || h.contains("กลุ่ม 2") || h.contains("กลุ่ม 3")) return "คำจำกัดความฯ ผลงานทางวิชาการ (กลุ่ม 1-3)";
        return h;
    }

    /**
     * ชื่อเอกสารที่ขึ้นต้นด้วยประเภทของตัวเอง ("ข้อบังคับ…", "ประกาศมหาวิทยาลัย…", "ประกาศ ก.พ.อ…") ชนะหัวข้อของคอลัมน์
     * — หน้าเว็บวางประกาศ มข. ไว้ใต้หัวข้อข้อบังคับได้ (หน้า 5532) ชื่ออื่น ๆ ใช้หัวข้อของคอลัมน์ตามเดิม
     */
    private String categoryNamedByTitle(String title, String sectionCategory) {
        if (title.startsWith("ประกาศ ก.พ.อ")) return "ประกาศ ก.พ.อ.";
        if (title.startsWith("ประกาศมหาวิทยาลัย")) return "ประกาศมหาวิทยาลัยขอนแก่น";
        if (title.startsWith("ข้อบังคับมหาวิทยาลัย")) return "ข้อบังคับมหาวิทยาลัยขอนแก่น";
        return sectionCategory;
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
        if (category.contains("ก.พ.อ")) return "fas fa-building-columns";
        if (category.contains("ข้อบังคับ")) return "fas fa-landmark";
        if (category.contains("ประกาศ")) return "fas fa-scroll";
        if (category.contains("แนบท้าย")) return "fas fa-paperclip";
        if (category.contains("เฉพาะด้าน") || category.contains("กลุ่ม 4")) return "fas fa-star";
        if (category.contains("คำจำกัดความ") || category.contains("กลุ่ม 1")) return "fas fa-flask";
        return "fas fa-file-alt";
    }

    private String getCategoryColor(String category) {
        if (category.contains("ก.พ.อ")) return "#00695c";
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
                  .replaceAll("\\s*\\(\\s*ใหม่\\s*\\)\\s*$", "")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    private boolean isRecentYear(String year) {
        if (year == null) return false;
        try {
            return Integer.parseInt(year.trim()) >= 2569;
        } catch (Exception e) {
            return false;
        }
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
