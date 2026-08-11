package com.ecom.academic.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DOCX Document Generation Service
 *
 * ใช้ Pure ZIP/XML approach (ไม่ใช้ POI เขียนไฟล์):
 * 1. Defragment: รวม placeholder ที่ Word แยกข้าม runs กลับเป็นชิ้นเดียว
 * 2. Simple Replace: แทนค่า {{placeholder}} ทั้งหมดด้วย string replace ธรรมดา
 * 3. Checkbox Fix: แปลง ☑/☐ เป็น MS Gothic font runs (text-style)
 *
 * ไม่ merge runs → รักษา formatting ต้นฉบับ 100%
 */
@Service
public class DocumentGenerationService {

    private static final String TEMPLATE_DIR = "templates/docx/";
    private static final String OUTPUT_BASE_DIR = "uploads/academic/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =====================================================================
    // Public API
    // =====================================================================

    public String generateDocument(Long requestId, int documentType, String jsonData, Integer copyNumber)
            throws IOException {
        Map<String, Object> dataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> placeholders = flattenMap(dataMap, "");

        String templateFile = TEMPLATE_DIR + "doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        String outputDir = OUTPUT_BASE_DIR + requestId + "/";
        Files.createDirectories(Path.of(outputDir));

        String outputFileName = (copyNumber != null && copyNumber > 0)
                ? "doc_" + documentType + "_copy_" + copyNumber + ".docx"
                : "doc_" + documentType + ".docx";
        String outputPath = outputDir + outputFileName;

        // Doc 4: remap form field names to template placeholder names
        if (documentType == 4) {
            preprocessDoc4Placeholders(placeholders);
        }

        byte[] result = processTemplate(resource.getInputStream(), placeholders, documentType);

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(result);
        }

        return outputPath;
    }

    public List<String> generateDocument4Copies(Long requestId, String jsonData,
            List<Map<String, String>> committeeMembers) throws IOException {
        Map<String, Object> baseDataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        List<String> generatedPaths = new ArrayList<>();

        for (int i = 0; i < committeeMembers.size(); i++) {
            Map<String, Object> copyData = new java.util.HashMap<>(baseDataMap);
            Map<String, String> member = committeeMembers.get(i);
            copyData.put("committee_name", member.get("name"));
            copyData.put("committee_position", member.get("position"));

            String copyJson = objectMapper.writeValueAsString(copyData);
            String path = generateDocument(requestId, 4, copyJson, i + 1);
            generatedPaths.add(path);
        }

        return generatedPaths;
    }

    public byte[] generatePreviewDocx(int documentType, String jsonData) throws IOException {
        Map<String, Object> dataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> placeholders = flattenMap(dataMap, "");

        String templateFile = TEMPLATE_DIR + "doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        // Doc 4: remap form field names to template placeholder names
        if (documentType == 4) {
            preprocessDoc4Placeholders(placeholders);
        }

        return processTemplate(resource.getInputStream(), placeholders, documentType);
    }

    public byte[] generatePreviewDocxForCopy(int documentType, String jsonData,
            String committeeName, String committeePosition) throws IOException {
        Map<String, Object> dataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        dataMap.put("committee_name", committeeName);
        dataMap.put("committee_position", committeePosition);

        return generatePreviewDocx(documentType, objectMapper.writeValueAsString(dataMap));
    }

    public byte[] getDocumentBytes(String filePath) throws IOException {
        return Files.readAllBytes(Path.of(filePath));
    }

    public File getDocumentFile(String filePath) {
        return new File(filePath);
    }

    // =====================================================================
    // Phase 2: Position Request Document Generation
    // =====================================================================

    /** Preview-only (in-memory) for Phase 2 position documents */
    public byte[] generateP2PreviewDocx(int documentType, String jsonData) throws IOException {
        Map<String, Object> dataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> placeholders = flattenMap(dataMap, "");
        mapUsedCheckboxes(placeholders);
        mapMethod3Fields(placeholders);

        String templateFile = TEMPLATE_DIR + "Phase2/p2doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        // Doc 4: remap form field names to template placeholder names
        if (documentType == 4) {
            preprocessDoc4Placeholders(placeholders);
        }

        return processTemplate(resource.getInputStream(), placeholders, documentType);
    }

    public String generateP2Document(com.ecom.academic.model.PositionRequest request,
            int documentType, String jsonData) throws IOException {
        Map<String, Object> dataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> placeholders = flattenMap(dataMap, "");

        String templateFile = TEMPLATE_DIR + "Phase2/p2doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        String outputDir = OUTPUT_BASE_DIR + "position/" + request.getId() + "/";
        Files.createDirectories(Path.of(outputDir));

        String outputPath = outputDir + "p2doc_" + documentType + ".docx";

        // Doc 4: remap form field names to template placeholder names
        if (documentType == 4) {
            preprocessDoc4Placeholders(placeholders);
        }

        byte[] result = processTemplate(resource.getInputStream(), placeholders, documentType);

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(result);
        }

        return outputPath;
    }

    // =====================================================================
    // Core: Pure ZIP/XML Processing (format-preserving)
    // =====================================================================

    private byte[] processTemplate(InputStream templateStream, Map<String, String> placeholders, int docType)
            throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        try (ZipInputStream zis = new ZipInputStream(templateStream);
                ZipOutputStream zos = new ZipOutputStream(result)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();

                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".xml.rels")) {
                    String xml = new String(data, StandardCharsets.UTF_8);

                    // Step 0: ลบ descr URL ใน drawing และ w:bdr frame — ป้องกัน LibreOffice
                    // แสดงกรอบสี่เหลี่ยมรอบรูปภาพใน PDF
                    xml = xml.replaceAll(" descr=\"https://[^\"]*\"", "");
                    xml = xml.replaceAll("<w:bdr[^>]*w:frame=\"1\"[^/]*/>", "");

                    if (xml.contains("{{")) {
                        // Step 1: Defragment - รวม placeholder ที่ Word แยกข้าม <w:t> กลับเป็นชิ้นเดียว
                        xml = defragmentPlaceholders(xml);

                        // Step 1.5: Dynamic row cloning - เพิ่มแถวตารางสำหรับนวิจัยที่เกิน 5 รายการ
                        xml = expandDynamicRows(xml, placeholders);

                        // Step 1.6: Co-author signature cloning - เพิ่มลายเซ็นผู้ร่วมงานในเอกสารที่ 9
                        xml = expandCoauthorSignatures(xml, placeholders);

                        // Step 2: Simple replace - แทนค่า {{placeholder}} ทั้งหมด
                        for (Map.Entry<String, String> ph : placeholders.entrySet()) {
                            String token = "{{" + ph.getKey() + "}}";
                            if (xml.contains(token)) {
                                String value = ph.getValue() != null ? escapeXml(ph.getValue()) : "";
                                xml = xml.replace(token, value);
                            }
                        }

                        // Step 2.5: Cleanup - ลบ {{...}} ที่เหลือซึ่งไม่มีค่าจากฟอร์ม
                        xml = xml.replaceAll("\\{\\{[^}]+\\}\\}", "");

                        // Step 3: Checkbox rendering
                        if (docType == 4) {
                            // Doc 4: ใช้ ✔ เฉยๆ ไม่มีกรอบ — แปลงค่าเก่า ☑→✔, ☐→ว่าง
                            xml = xml.replace("\u2611\uFE0E", "\u2714");
                            xml = xml.replace("\u2611", "\u2714");
                            xml = xml.replace("\u2610", "");
                        } else {
                            // เอกสารอื่น: ☑/☐ → MS Gothic font runs (กล่อง)
                            xml = renderCheckboxes(xml);
                        }
                    }

                    data = xml.getBytes(StandardCharsets.UTF_8);
                }

                ZipEntry newEntry = new ZipEntry(entry.getName());
                zos.putNextEntry(newEntry);
                zos.write(data);
                zos.closeEntry();
            }
        }

        return result.toByteArray();
    }

    // =====================================================================
    // Step 1: Placeholder Defragmentation
    // =====================================================================

    /**
     * Word มักแยก {{placeholder}} ข้าม <w:t> elements หลายตัว เช่น:
     * <w:t>{{score3</w:t> ... <w:t>1</w:t> ... <w:t>}}</w:t>
     *
     * Method นี้จะรวมข้อความกลับเป็นชิ้นเดียวใน <w:t> ตัวแรก แล้วล้างตัวที่เหลือ
     * โดยไม่แตะ XML structure อื่นๆ → รักษา formatting ต้นฉบับ 100%
     */
    private String defragmentPlaceholders(String xml) {
        StringBuilder out = new StringBuilder(xml.length());
        int pos = 0;

        while (pos < xml.length()) {
            // หา <w:t ถัดไป
            int tStart = xml.indexOf("<w:t", pos);
            if (tStart == -1) {
                out.append(xml, pos, xml.length());
                break;
            }

            // ตรวจว่าเป็น <w:t> หรือ <w:t ...> (ไม่ใช่ <w:tbl>, <w:tc> ฯลฯ)
            int tTagEnd = xml.indexOf('>', tStart);
            if (tTagEnd == -1) {
                out.append(xml, pos, xml.length());
                break;
            }

            String tagName = xml.substring(tStart, Math.min(tStart + 5, xml.length()));
            if (!tagName.startsWith("<w:t>") && !tagName.startsWith("<w:t ")) {
                out.append(xml, pos, tTagEnd + 1);
                pos = tTagEnd + 1;
                continue;
            }

            // Self-closing tag
            if (xml.charAt(tTagEnd - 1) == '/') {
                out.append(xml, pos, tTagEnd + 1);
                pos = tTagEnd + 1;
                continue;
            }

            int tCloseStart = xml.indexOf("</w:t>", tTagEnd);
            if (tCloseStart == -1) {
                out.append(xml, pos, xml.length());
                break;
            }

            String textContent = xml.substring(tTagEnd + 1, tCloseStart);
            int tBlockEnd = tCloseStart + "</w:t>".length();

            // เช็คว่า text นี้มี {{ ที่ยังไม่ปิดด้วย }} หรือลงท้ายด้วย {
            boolean hasIncompleteToken = textContent.contains("{{") && !hasCompleteTokens(textContent);
            boolean endsWithBrace = textContent.endsWith("{");
            if (hasIncompleteToken || endsWithBrace) {
                // เริ่ม defragmentation: สะสม text จาก <w:t> ถัดๆ ไป จนเจอ }}
                out.append(xml, pos, tTagEnd + 1); // copy จนถึง > ของ <w:t>

                StringBuilder accumulated = new StringBuilder(textContent);
                List<int[]> subsequentTexts = new ArrayList<>();
                int scanPos = tBlockEnd;

                while ((!hasCompleteTokens(accumulated.toString()) || accumulated.toString().endsWith("{"))
                        && scanPos < xml.length()) {
                    // หา <w:t> ถัดไปในขอบเขตที่สมเหตุสมผล (ไม่ข้าม paragraph)
                    int nextP = xml.indexOf("</w:p>", scanPos);
                    int nextT = xml.indexOf("<w:t", scanPos);

                    if (nextT == -1 || (nextP != -1 && nextT > nextP)) {
                        break;
                    }

                    // ตรวจว่าเป็น <w:t> จริง
                    int nextTTagEnd = xml.indexOf('>', nextT);
                    if (nextTTagEnd == -1)
                        break;

                    String nextTagName = xml.substring(nextT, Math.min(nextT + 5, xml.length()));
                    if (!nextTagName.startsWith("<w:t>") && !nextTagName.startsWith("<w:t ")) {
                        scanPos = nextTTagEnd + 1;
                        continue;
                    }

                    if (xml.charAt(nextTTagEnd - 1) == '/') {
                        scanPos = nextTTagEnd + 1;
                        continue;
                    }

                    int nextTClose = xml.indexOf("</w:t>", nextTTagEnd);
                    if (nextTClose == -1)
                        break;

                    String nextText = xml.substring(nextTTagEnd + 1, nextTClose);
                    accumulated.append(nextText);
                    subsequentTexts.add(new int[] { nextTTagEnd + 1, nextTClose });
                    scanPos = nextTClose + "</w:t>".length();
                }

                // เขียน text ที่รวมแล้วใน <w:t> ตัวแรก
                out.append(accumulated);
                out.append("</w:t>");

                // Copy XML ระหว่าง </w:t> ตัวแรก กับ <w:t> ถัดไปที่ถูกรวม
                // แต่ล้างข้อความใน <w:t> ที่ถูกรวมเป็นว่าง
                int copyFrom = tCloseStart + "</w:t>".length();
                for (int[] range : subsequentTexts) {
                    out.append(xml, copyFrom, range[0]); // copy XML structure
                    // ไม่ใส่ text (ล้างเป็นว่าง)
                    copyFrom = range[1]; // skip original text
                }
                out.append(xml, copyFrom, scanPos);
                pos = scanPos;
            } else {
                // ไม่มี fragment → copy ตามเดิม
                out.append(xml, pos, tBlockEnd);
                pos = tBlockEnd;
            }
        }

        return out.toString();
    }

    /**
     * ตรวจว่าข้อความมี {{ ทุกตัวจับคู่กับ }} ครบหรือไม่
     */
    private boolean hasCompleteTokens(String text) {
        int idx = 0;
        while (idx < text.length()) {
            int openIdx = text.indexOf("{{", idx);
            if (openIdx == -1)
                return true;
            int closeIdx = text.indexOf("}}", openIdx);
            if (closeIdx == -1)
                return false;
            idx = closeIdx + 2;
        }
        return true;
    }

    // =====================================================================
    // Step 1.5: Dynamic Table Row Expansion
    // =====================================================================

    /**
     * สำหรับเอกสารที่ 6 (จริยธรรมการวิจัย):
     * Template มี 5 แถวตายตั้ง (des_research1-5)
     * ถ้า research_count > 5 จะ clone แถวที่ 5 แล้วเปลี่ยนหมายเลข placeholder
     */
    private void mapUsedCheckboxes(Map<String, String> placeholders) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "^(asst|assoc|prof)_used_(research|other|book)_(\\d+)$");
        Map<String, String> toAdd = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : new java.util.ArrayList<>(placeholders.entrySet())) {
            java.util.regex.Matcher m = p.matcher(entry.getKey());
            if (m.matches()) {
                String prefix = m.group(1);
                String type = m.group(2);
                String n = m.group(3);
                boolean notUsed = "not_used".equals(entry.getValue());
                if ("research".equals(type)) {
                    // Generate both spellings — DOCX template is inconsistent:
                    // ASST section uses typo "reseach", ASSOC/PROF use correct "research"
                    toAdd.put(prefix + "_not_used_reseach_" + n, notUsed ? "☑" : "☐");
                    toAdd.put(prefix + "_is_used_reseach_" + n, notUsed ? "☐" : "☑");
                    toAdd.put(prefix + "_not_used_research_" + n, notUsed ? "☑" : "☐");
                    toAdd.put(prefix + "_is_used_research_" + n, notUsed ? "☐" : "☑");
                } else {
                    toAdd.put(prefix + "_not_used_" + type + "_" + n, notUsed ? "☑" : "☐");
                    toAdd.put(prefix + "_is_used_" + type + "_" + n, notUsed ? "☐" : "☑");
                }
            }
        }
        placeholders.putAll(toAdd);
    }

    private void mapMethod3Fields(Map<String, String> placeholders) {
        for (String prefix : new String[] { "assoc", "prof" }) {
            // Quartile radio: {prefix}_m3_quartile_N → {prefix}_m3_q1 / {prefix}_m3_q2
            for (int n = 1; n <= 10; n++) {
                String radioKey = prefix + "_m3_quartile_" + n;
                String val = placeholders.get(radioKey);
                if (val != null || n == 1) {
                    boolean q1 = "Q1".equals(val);
                    boolean q2 = "Q2".equals(val);
                    if (n == 1) {
                        placeholders.put(prefix + "_m3_q1", q1 ? "☑" : "☐");
                        placeholders.put(prefix + "_m3_q2", q2 ? "☑" : "☐");
                    }
                    placeholders.put(prefix + "_m3_q1_" + n, q1 ? "☑" : "☐");
                    placeholders.put(prefix + "_m3_q2_" + n, q2 ? "☑" : "☐");
                    if (val == null)
                        break;
                }
            }
            // First / Corresp checkboxes: alias _1 → no suffix, default ☐
            for (String field : new String[] { "m3_first", "m3_corresp" }) {
                String v = placeholders.getOrDefault(prefix + "_" + field + "_1", "☐");
                placeholders.put(prefix + "_" + field, v);
            }
            // PI fields: alias _1 → no suffix
            for (String field : new String[] { "pi_project_no", "pi_project", "pi_source" }) {
                String v = placeholders.get(prefix + "_" + field + "_1");
                if (v != null)
                    placeholders.put(prefix + "_" + field, v);
            }
        }
    }

    private String expandDynamicRows(String xml, Map<String, String> placeholders) {
        // Block A: เอกสารที่ 6 — research rows (des_research)
        if (xml.contains("{{des_research5}}")) {
            // หาจำนวน row ที่ต้องการจาก placeholder data
            int maxRow = 5;
            for (String key : placeholders.keySet()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^des_research(\\d+)$").matcher(key);
                if (m.matches()) {
                    int n = Integer.parseInt(m.group(1));
                    if (n > maxRow)
                        maxRow = n;
                }
            }

            if (maxRow > 5) {
                int searchFrom = 0;
                while (true) {
                    int trIdx = xml.indexOf("<w:tr ", searchFrom);
                    if (trIdx == -1)
                        trIdx = xml.indexOf("<w:tr>", searchFrom);
                    if (trIdx == -1)
                        break;

                    int trEnd = xml.indexOf("</w:tr>", trIdx);
                    if (trEnd == -1)
                        break;
                    trEnd += "</w:tr>".length();

                    String rowXml = xml.substring(trIdx, trEnd);
                    if (rowXml.contains("des_research5")) {
                        StringBuilder newRows = new StringBuilder();
                        for (int n = 6; n <= maxRow; n++) {
                            String cloned = rowXml
                                    .replace("des_research5", "des_research" + n)
                                    .replace("chk_firstauthor5", "chk_firstauthor" + n)
                                    .replace("chk_Corres5", "chk_Corres" + n)
                                    .replace("essen5", "essen" + n)
                                    .replace("impactfacttor5", "impactfacttor" + n)
                                    .replace("data5", "data" + n);
                            cloned = cloned.replaceFirst(">5\\.<", ">" + n + ".<")
                                    .replaceFirst(">5<", ">" + n + "<");
                            newRows.append(cloned);
                        }
                        xml = xml.substring(0, trEnd) + newRows.toString() + xml.substring(trEnd);
                        break;
                    }
                    searchFrom = trEnd;
                }
            }
        }

        // Block B: เอกสารที่ 1 — education history rows (ประวัติการศึกษา)
        if (xml.contains("{{education_degree_1}}")) {
            int maxEduRow = 1;
            for (String key : placeholders.keySet()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^education_degree_(\\d+)$").matcher(key);
                if (m.matches()) {
                    int n = Integer.parseInt(m.group(1));
                    if (n > maxEduRow)
                        maxEduRow = n;
                }
            }
            if (maxEduRow > 1) {
                int eduSearchFrom = 0;
                while (true) {
                    int trIdx = xml.indexOf("<w:tr ", eduSearchFrom);
                    if (trIdx == -1)
                        trIdx = xml.indexOf("<w:tr>", eduSearchFrom);
                    if (trIdx == -1)
                        break;
                    int trEnd = xml.indexOf("</w:tr>", trIdx);
                    if (trEnd == -1)
                        break;
                    trEnd += "</w:tr>".length();
                    String rowXml = xml.substring(trIdx, trEnd);
                    if (rowXml.contains("education_degree_1")) {
                        StringBuilder newRows = new StringBuilder();
                        for (int n = 2; n <= maxEduRow; n++) {
                            String cloned = rowXml
                                    .replace("education_no_1", "education_no_" + n)
                                    .replace("education_degree_1", "education_degree_" + n)
                                    .replace("education_major_1", "education_major_" + n)
                                    .replace("education_institution_1", "education_institution_" + n)
                                    .replace("education_country_1", "education_country_" + n)
                                    .replace("education_year_1", "education_year_" + n);
                            newRows.append(cloned);
                        }
                        xml = xml.substring(0, trEnd) + newRows.toString() + xml.substring(trEnd);
                        break;
                    }
                    eduSearchFrom = trEnd;
                }
            }
        }

        // Block C: เอกสารที่ 1 — work section rows (paragraph-based)
        // Each logical "row" = 3 consecutive <w:p>: title row, not-used checkbox, used
        // checkbox+year+level
        String[][][] workSections = {
                // ASST (DOCX has typo "reseach" for research)
                { { "asst_research_working_1" }, { "asst_research_working_no_1", "asst_research_working_1",
                        "asst_not_used_reseach_1", "asst_is_used_reseach_1",
                        "asst_used_research_year_1", "asst_used_research_level_1" } },
                { { "asst_other_working_1" }, { "asst_other_working_no_1", "asst_other_working_1",
                        "asst_not_used_other_1", "asst_is_used_other_1",
                        "asst_used_other_year_1", "asst_used_other_level_1" } },
                { { "asst_book_working_1" }, { "asst_book_working_no_1", "asst_book_working_1",
                        "asst_not_used_book_1", "asst_is_used_book_1",
                        "asst_used_book_year_1", "asst_used_book_level_1" } },
                // ASSOC
                { { "assoc_research_working_1" }, { "assoc_research_working_no_1", "assoc_research_working_1",
                        "assoc_not_used_research_1", "assoc_is_used_research_1",
                        "assoc_used_research_year_1", "assoc_used_research_level_1" } },
                { { "assoc_other_working_1" }, { "assoc_other_working_no_1", "assoc_other_working_1",
                        "assoc_not_used_other_1", "assoc_is_used_other_1",
                        "assoc_used_other_year_1", "assoc_used_other_level_1" } },
                { { "assoc_book_working_1" }, { "assoc_book_working_no_1", "assoc_book_working_1",
                        "assoc_not_used_book_1", "assoc_is_used_book_1",
                        "assoc_used_book_year_1", "assoc_used_book_level_1" } },
                // PROF
                { { "prof_research_working_1" }, { "prof_research_working_no_1", "prof_research_working_1",
                        "prof_not_used_research_1", "prof_is_used_research_1",
                        "prof_used_research_year_1", "prof_used_research_level_1" } },
                { { "prof_other_working_1" }, { "prof_other_working_no_1", "prof_other_working_1",
                        "prof_not_used_other_1", "prof_is_used_other_1",
                        "prof_used_other_year_1", "prof_used_other_level_1" } },
                { { "prof_book_working_1" }, { "prof_book_working_no_1", "prof_book_working_1",
                        "prof_not_used_book_1", "prof_is_used_book_1",
                        "prof_used_book_year_1", "prof_used_book_level_1" } },
                // International speaker & Other positions
                { { "international_speaker_last_5_years_1" },
                        { "international_speaker_last_5_years_no_1", "international_speaker_last_5_years_1" } },
                { { "other_position_1" }, { "other_position_no_1", "other_position_1" } },
        };
        for (String[][] section : workSections) {
            xml = expandParagraphRows(xml, placeholders, section[0][0], section[1]);
        }

        // Block D: เอกสารที่ 1 — teaching rows (table-based <w:tr>)
        if (xml.contains("{{teaching_subject_1}}")) {
            int maxTeach = 1;
            for (String key : placeholders.keySet()) {
                if (key.startsWith("teaching_subject_")) {
                    try {
                        int n = Integer.parseInt(key.substring("teaching_subject_".length()));
                        if (n > maxTeach)
                            maxTeach = n;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (maxTeach > 1) {
                int searchFrom = 0;
                while (true) {
                    int trIdx = xml.indexOf("<w:tr ", searchFrom);
                    if (trIdx == -1)
                        trIdx = xml.indexOf("<w:tr>", searchFrom);
                    if (trIdx == -1)
                        break;
                    int trEnd = xml.indexOf("</w:tr>", trIdx);
                    if (trEnd == -1)
                        break;
                    trEnd += "</w:tr>".length();
                    String rowXml = xml.substring(trIdx, trEnd);
                    if (rowXml.contains("{{teaching_subject_1}}")) {
                        StringBuilder newRows = new StringBuilder();
                        for (int n = 2; n <= maxTeach; n++) {
                            String cloned = rowXml
                                    .replace("{{teaching_subject_1}}", "{{teaching_subject_" + n + "}}")
                                    .replace("{{teaching_level_1}}", "{{teaching_level_" + n + "}}")
                                    .replace("{{teaching_semester_1}}", "{{teaching_semester_" + n + "}}")
                                    .replace("{{teaching_hours_per_week_1}}", "{{teaching_hours_per_week_" + n + "}}");
                            newRows.append(cloned);
                        }
                        xml = xml.substring(0, trEnd) + newRows + xml.substring(trEnd);
                        break;
                    }
                    searchFrom = trEnd;
                }
            }
        }

        return xml;
    }

    /**
     * Clone <w:p> paragraphs for a work section that uses row-_1 placeholders.
     * Finds all paragraphs containing any of fields1, then inserts copies for rows
     * 2..maxN.
     */
    private String expandParagraphRows(String xml, Map<String, String> placeholders,
            String anchorKey1, String[] fields1) {
        if (!xml.contains("{{" + anchorKey1 + "}}"))
            return xml;

        // Find max row number from placeholders (using anchor field base)
        String anchorBase = anchorKey1.substring(0, anchorKey1.lastIndexOf('_')); // strip trailing _1
        int maxN = 1;
        for (String key : placeholders.keySet()) {
            if (key.startsWith(anchorBase + "_")) {
                try {
                    int n = Integer.parseInt(key.substring(anchorBase.length() + 1));
                    if (n > maxN)
                        maxN = n;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (maxN <= 1)
            return xml;

        // Collect all <w:p> elements that contain any _1 field placeholder
        List<int[]> paraRanges = new ArrayList<>();
        int pos = 0;
        while (pos < xml.length()) {
            int pA = xml.indexOf("<w:p ", pos);
            int pB = xml.indexOf("<w:p>", pos);
            int pStart = (pA == -1) ? pB : (pB == -1) ? pA : Math.min(pA, pB);
            if (pStart == -1)
                break;
            int pEnd = xml.indexOf("</w:p>", pStart);
            if (pEnd == -1)
                break;
            pEnd += "</w:p>".length();
            String paraXml = xml.substring(pStart, pEnd);
            for (String f : fields1) {
                if (paraXml.contains("{{" + f + "}}")) {
                    paraRanges.add(new int[] { pStart, pEnd });
                    break;
                }
            }
            pos = pEnd;
        }
        if (paraRanges.isEmpty())
            return xml;

        // Insert clones after the last matched paragraph
        int insertAt = paraRanges.get(paraRanges.size() - 1)[1];
        StringBuilder cloned = new StringBuilder();
        for (int n = 2; n <= maxN; n++) {
            for (int[] range : paraRanges) {
                String paraXml = xml.substring(range[0], range[1]);
                for (String f : fields1) {
                    // Replace {{field_1}} → {{field_N}} by stripping trailing _1
                    String base = f.substring(0, f.lastIndexOf('_')); // e.g. "assoc_research_working_no"
                    paraXml = paraXml.replace("{{" + f + "}}", "{{" + base + "_" + n + "}}");
                }
                cloned.append(paraXml);
            }
        }
        return xml.substring(0, insertAt) + cloned + xml.substring(insertAt);
    }

    // =====================================================================
    // Doc 4: Field Name Remapping & Dynamic List Expansion
    // =====================================================================

    /**
     * เอกสารที่ 4: Remap form field names → DOCX template placeholder names
     * 
     * form: paper_title_1, paper_title_2 → template: research_title1,
     * research_title2
     * form: research_title_1, research_title_2 → template: research_title,
     * research_title_2
     * auto-generate: index1, index2... and index, index_2...
     */
    private void preprocessDoc4Placeholders(Map<String, String> placeholders) {
        // Section 1: Academic Papers
        // Template: {{index1}}{{research_title1}} — รวมทุกบทความเป็น text เดียว
        // เพราะ Word fragment placeholder ทำให้จับคู่ไม่ได้ถ้าแยกทีละรายการ
        int maxPaper = 0;
        for (String key : placeholders.keySet()) {
            if (key.startsWith("paper_title_")) {
                try {
                    int n = Integer.parseInt(key.substring("paper_title_".length()));
                    if (n > maxPaper)
                        maxPaper = n;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (maxPaper > 0) {
            // Item 1: use template's own positioning via {{index1}} and {{research_title1}}
            String firstVal = placeholders.get("paper_title_1");
            StringBuilder titleValue = new StringBuilder();
            if (firstVal != null && !firstVal.isEmpty()) {
                titleValue.append(firstVal);
            }
            // Items 2+: append with line breaks after item 1
            for (int i = 2; i <= maxPaper; i++) {
                String val = placeholders.get("paper_title_" + i);
                if (val != null && !val.isEmpty()) {
                    titleValue.append("\n").append(i).append(". ").append(val);
                }
            }
            placeholders.put("index1", "1. ");
            placeholders.put("research_title1", titleValue.toString());
        }

        // Section 2: Research
        // Template: {{index}}{{research_title}} — รวมทุกวิจัยเป็น text เดียว
        int maxResearch = 0;
        for (String key : placeholders.keySet()) {
            if (key.startsWith("research_title_")) {
                try {
                    int n = Integer.parseInt(key.substring("research_title_".length()));
                    if (n > maxResearch)
                        maxResearch = n;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (maxResearch > 0) {
            // Item 1: use template's own positioning via {{index}} and {{research_title}}
            String firstVal = placeholders.get("research_title_1");
            StringBuilder titleValue = new StringBuilder();
            if (firstVal != null && !firstVal.isEmpty()) {
                titleValue.append(firstVal);
            }
            // Items 2+: append with line breaks after item 1
            for (int i = 2; i <= maxResearch; i++) {
                String val = placeholders.get("research_title_" + i);
                if (val != null && !val.isEmpty()) {
                    titleValue.append("\n").append(i).append(". ").append(val);
                }
            }
            placeholders.put("index", "1. ");
            placeholders.put("research_title", titleValue.toString());
        }
    }

    // =====================================================================
    // Step 1.6: Co-author Signature Expansion (Doc 9)
    // =====================================================================

    /**
     * เอกสารที่ 9: ถ้ามี coauthor_count > 0 จะ clone paragraph group ลายเซ็น
     * ของ {{corres_name}} สำหรับผู้ร่วมงานแต่ละคน
     * 
     * โครงสร้าง DOCX signature block (3 paragraphs):
     * <w:p>...ลงชื่อ..................... </w:p>
     * <w:p>...({{corres_name}})</w:p>
     * <w:p>...ผู้ประพันธ์บรรณกิจ (Corresponding author)</w:p>
     */
    private String expandCoauthorSignatures(String xml, Map<String, String> placeholders) {
        String countStr = placeholders.get("coauthor_count");
        if (countStr == null || countStr.isEmpty()) {
            return xml;
        }
        int count;
        try {
            count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            return xml;
        }
        if (count <= 0 || !xml.contains("{{corres_name}}")) {
            return xml;
        }

        // หา paragraph ที่มี {{corres_name}} ตัวแรก
        int corresIdx = xml.indexOf("{{corres_name}}");
        if (corresIdx == -1)
            return xml;

        // หา <w:p> ที่ครอบ {{corres_name}} (paragraph ชื่อ)
        int pNameStart = xml.lastIndexOf("<w:p ", corresIdx);
        if (pNameStart == -1)
            pNameStart = xml.lastIndexOf("<w:p>", corresIdx);
        if (pNameStart == -1)
            return xml;

        int pNameEnd = xml.indexOf("</w:p>", corresIdx);
        if (pNameEnd == -1)
            return xml;
        pNameEnd += "</w:p>".length();

        // หา paragraph ถัดไป (role paragraph เช่น "ผู้ประพันธ์บรรณกิจ")
        int pRoleStart = xml.indexOf("<w:p ", pNameEnd);
        if (pRoleStart == -1)
            pRoleStart = xml.indexOf("<w:p>", pNameEnd);
        if (pRoleStart == -1)
            return xml;

        int pRoleEnd = xml.indexOf("</w:p>", pRoleStart);
        if (pRoleEnd == -1)
            return xml;
        pRoleEnd += "</w:p>".length();

        // หา paragraph ก่อนหน้า ("ลงชื่อ..." paragraph)
        int scanBack = pNameStart - 1;
        int pSignStart = xml.lastIndexOf("<w:p ", scanBack);
        if (pSignStart == -1)
            pSignStart = xml.lastIndexOf("<w:p>", scanBack);
        if (pSignStart == -1)
            return xml;

        int pSignEnd = xml.indexOf("</w:p>", pSignStart);
        if (pSignEnd == -1)
            return xml;
        pSignEnd += "</w:p>".length();

        String fullBlock;
        int insertAfter;

        if (pSignEnd <= pNameStart) {
            // 3 paragraphs ต่อเนื่อง: ลงชื่อ + ชื่อ + ตำแหน่ง
            fullBlock = xml.substring(pSignStart, pRoleEnd);
            insertAfter = pRoleEnd;
        } else {
            // ใช้ 2 paragraphs (ชื่อ + ตำแหน่ง)
            fullBlock = xml.substring(pNameStart, pRoleEnd);
            insertAfter = pRoleEnd;
        }

        // สร้าง signature blocks สำหรับ co-authors
        StringBuilder coauthorBlocks = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            String coName = placeholders.getOrDefault("coauthor_name_" + i, "");
            if (coName.isEmpty())
                continue;

            String block = fullBlock;
            // แทนชื่อ: {{corres_name}} → {{coauthor_name_N}}
            block = block.replace("{{corres_name}}", "{{coauthor_name_" + i + "}}");
            // แทนตำแหน่ง: "Corresponding author" → "Co-author"
            block = block.replace("Corresponding author", "Co-author");
            // แทนตำแหน่งภาษาไทย: "ผู้ประพันธ์บรรณกิจ" → "ผู้ร่วมประพันธ์"
            block = block.replace(
                    "\u0E1C\u0E39\u0E49\u0E1B\u0E23\u0E30\u0E1E\u0E31\u0E19\u0E18\u0E4C\u0E1A\u0E23\u0E23\u0E13\u0E01\u0E34\u0E08",
                    "\u0E1C\u0E39\u0E49\u0E23\u0E48\u0E27\u0E21\u0E1B\u0E23\u0E30\u0E1E\u0E31\u0E19\u0E18\u0E4C");
            coauthorBlocks.append(block);
        }

        if (coauthorBlocks.length() > 0) {
            xml = xml.substring(0, insertAfter) + coauthorBlocks.toString() + xml.substring(insertAfter);
        }

        // ลบ signature block ของ {{corres_name}} ที่ซ้ำ (ตัวที่ 2 ขึ้นไป)
        // หลัง expand แล้ว ถ้ายังมี {{corres_name}} เหลืออีก ให้ลบ 3-paragraph group
        // ทิ้ง
        while (true) {
            int nextCorres = xml.indexOf("{{corres_name}}", insertAfter);
            if (nextCorres == -1)
                break;

            // หา <w:p> ที่ครอบ
            int np = xml.lastIndexOf("<w:p ", nextCorres);
            if (np == -1)
                np = xml.lastIndexOf("<w:p>", nextCorres);
            if (np == -1)
                break;

            // หา paragraph ก่อนหน้า (ลงชื่อ...)
            int npPrev = xml.lastIndexOf("<w:p ", np - 1);
            if (npPrev == -1)
                npPrev = xml.lastIndexOf("<w:p>", np - 1);

            // หา </w:p> ของ paragraph ชื่อ
            int npEnd = xml.indexOf("</w:p>", nextCorres);
            if (npEnd == -1)
                break;
            npEnd += "</w:p>".length();

            // หา paragraph ถัดไป (ตำแหน่ง)
            int npRole = xml.indexOf("<w:p ", npEnd);
            if (npRole == -1)
                npRole = xml.indexOf("<w:p>", npEnd);
            if (npRole == -1)
                break;
            int npRoleEnd = xml.indexOf("</w:p>", npRole);
            if (npRoleEnd == -1)
                break;
            npRoleEnd += "</w:p>".length();

            // ลบ 3 paragraphs (ลงชื่อ + ชื่อ + ตำแหน่ง) หรือ 2 paragraphs
            int removeStart = (npPrev != -1 && npPrev < np) ? npPrev : np;
            xml = xml.substring(0, removeStart) + xml.substring(npRoleEnd);
        }

        return xml;
    }

    // =====================================================================
    // Step 3: Checkbox Rendering (MS Gothic font)
    // =====================================================================

    private String renderCheckboxes(String xml) {
        String chkOnXml = "</w:t></w:r>"
                + "<w:r><w:rPr>"
                + "<w:rFonts w:ascii=\"MS Gothic\" w:eastAsia=\"MS Gothic\" w:hAnsi=\"MS Gothic\"/>"
                + "</w:rPr>"
                + "<w:t>\u2611</w:t>"
                + "</w:r>"
                + "<w:r><w:rPr><w:rFonts w:ascii=\"TH Sarabun New\" w:hAnsi=\"TH Sarabun New\" w:cs=\"TH Sarabun New\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">";

        String chkOffXml = "</w:t></w:r>"
                + "<w:r><w:rPr>"
                + "<w:rFonts w:ascii=\"MS Gothic\" w:eastAsia=\"MS Gothic\" w:hAnsi=\"MS Gothic\"/>"
                + "</w:rPr>"
                + "<w:t>\u2610</w:t>"
                + "</w:r>"
                + "<w:r><w:rPr><w:rFonts w:ascii=\"TH Sarabun New\" w:hAnsi=\"TH Sarabun New\" w:cs=\"TH Sarabun New\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">";

        xml = xml.replace("\u2611\uFE0E", chkOnXml);
        xml = xml.replace("\u2611", chkOnXml);
        xml = xml.replace("\u2610", chkOffXml);

        return xml;
    }

    // =====================================================================
    // PDF Conversion (LibreOffice CLI)
    // =====================================================================

    /** จำกัดจำนวน soffice ที่รันพร้อมกัน — แต่ละ process กิน RAM/CPU สูง */
    private static final Semaphore PDF_SLOTS = new Semaphore(2);

    /** timeout ต่อการแปลง 1 ครั้ง — กัน process ค้างถาวรจนกิน slot ทั้งหมด */
    private static final long PDF_TIMEOUT_SECONDS = 60;

    /** cache ผลการค้นหา soffice: null = ยังไม่เคยหา, "" = หาแล้วไม่เจอ */
    private volatile String cachedSofficePath = null;

    /** LRU cache ของ PDF ที่แปลงแล้ว — preview เดิมซ้ำ ๆ จะไม่เรียก LibreOffice ใหม่ */
    private static final int PDF_CACHE_SIZE = 50;
    private final Map<String, byte[]> pdfCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > PDF_CACHE_SIZE;
                }
            });

    /** LibreOffice พร้อมใช้งานหรือไม่ — ใช้ตัดสินใจว่าจะ preview เป็น PDF ได้ไหม */
    public boolean isPdfConversionAvailable() {
        return !resolveLibreOffice().isEmpty();
    }

    /** แปลง DOCX → PDF โดยใช้ cache (สำหรับ preview ที่กดซ้ำบ่อย) */
    public byte[] convertDocxToPdfCached(byte[] docxBytes) throws IOException {
        String key = sha256(docxBytes);
        byte[] cached = pdfCache.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] pdf = convertDocxToPdf(docxBytes);
        pdfCache.put(key, pdf);
        return pdf;
    }

    public byte[] convertDocxToPdf(byte[] docxBytes) throws IOException {
        String soffice = resolveLibreOffice();
        if (soffice.isEmpty()) {
            throw new IOException("LibreOffice not found. " +
                    "Windows: https://www.libreoffice.org/download | " +
                    "Mac: brew install --cask libreoffice | " +
                    "Linux: sudo apt install libreoffice-writer");
        }

        try {
            PDF_SLOTS.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PDF conversion interrupted while waiting for a slot", e);
        }

        Path tempDir = Files.createTempDirectory("docx-to-pdf-");
        Path tempDocx = tempDir.resolve("input.docx");
        Path tempPdf = tempDir.resolve("input.pdf");
        Path profileDir = tempDir.resolve("lo-profile");
        Files.write(tempDocx, docxBytes);

        Process process = null;
        try {
            // UserInstallation แยกต่อการเรียกแต่ละครั้ง — ถ้าใช้ profile ร่วมกัน
            // soffice หลาย process จะชนกันแล้ว hang เมื่อมีผู้ใช้พร้อมกัน
            ProcessBuilder pb = new ProcessBuilder(
                    soffice,
                    "-env:UserInstallation=" + profileDir.toUri(),
                    "--headless", "--norestore", "--nolockcheck", "--nodefault",
                    "--convert-to", "pdf",
                    "--outdir", tempDir.toString(), tempDocx.toString());
            // เขียน output ลงไฟล์แทนการ read stream — ถ้า read ก่อน waitFor
            // จะบล็อกจนกว่า process จบ ทำให้ timeout ไม่มีผล
            Path logFile = tempDir.resolve("soffice.log");
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile.toFile());

            process = pb.start();

            if (!process.waitFor(PDF_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("LibreOffice conversion timed out after " + PDF_TIMEOUT_SECONDS + "s");
            }

            String output = Files.exists(logFile)
                    ? new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8).trim()
                    : "";
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("LibreOffice conversion failed (exit " + exitCode + "): " + output);
            }

            if (!Files.exists(tempPdf)) {
                throw new IOException("PDF conversion produced no output: " + output);
            }
            return Files.readAllBytes(tempPdf);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice conversion interrupted", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            PDF_SLOTS.release();
            deleteRecursively(tempDir);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** ลบทั้ง temp dir — profile ของ LibreOffice มีไฟล์ย่อยจำนวนมาก */
    private static void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException | UncheckedIOException ignored) {
        }
    }

    /** หา soffice แล้ว cache ผลไว้ (ทั้งกรณีเจอและไม่เจอ); "" = ไม่เจอ */
    private String resolveLibreOffice() {
        String cached = cachedSofficePath;
        if (cached != null) {
            return cached;
        }
        String found;
        try {
            found = findLibreOffice();
        } catch (IOException e) {
            found = "";
        }
        cachedSofficePath = found;
        return found;
    }

    private String findLibreOffice() throws IOException {
        String[] candidates = {
                // Windows
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe",
                // macOS
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/opt/homebrew/bin/soffice",
                // Linux
                "/usr/bin/soffice",
                "/usr/bin/libreoffice",
                "/usr/local/bin/soffice",
        };

        // Try 'which' (Mac/Linux) or 'where' (Windows)
        String whichCmd = System.getProperty("os.name", "").toLowerCase().contains("win") ? "where" : "which";
        try {
            Process p = new ProcessBuilder(whichCmd, "soffice").start();
            String path = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0 && !path.isEmpty())
                return path.lines().findFirst().orElse(path);
        } catch (Exception ignored) {
        }

        for (String c : candidates) {
            if (new File(c).exists())
                return c;
        }

        throw new IOException("LibreOffice not found. " +
                "Windows: https://www.libreoffice.org/download | " +
                "Mac: brew install --cask libreoffice | " +
                "Linux: sudo apt install libreoffice");
    }

    // =====================================================================
    // Utilities
    // =====================================================================

    private String escapeXml(String s) {
        if (s == null)
            return "";
        s = s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
        // \n → DOCX line break: stay in same <w:r> to preserve font (TH Sarabun New)
        if (s.contains("\n")) {
            s = s.replace("\n", "</w:t><w:br/><w:t xml:space=\"preserve\">");
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> flattenMap(Map<String, Object> map, String prefix) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "_" + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.putAll(flattenMap((Map<String, Object>) value, key));
            } else if (value != null) {
                result.put(entry.getKey(), value.toString());
                if (!prefix.isEmpty()) {
                    result.put(key, value.toString());
                }
            }
        }
        return result;
    }
}
