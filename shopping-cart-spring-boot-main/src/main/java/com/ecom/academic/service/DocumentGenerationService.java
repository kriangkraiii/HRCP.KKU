package com.ecom.academic.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        byte[] result = processTemplate(resource.getInputStream(), placeholders);

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
        return processTemplate(resource.getInputStream(), placeholders);
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
    // Core: Pure ZIP/XML Processing (format-preserving)
    // =====================================================================

    private byte[] processTemplate(InputStream templateStream, Map<String, String> placeholders) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        try (ZipInputStream zis = new ZipInputStream(templateStream);
                ZipOutputStream zos = new ZipOutputStream(result)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();

                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".xml.rels")) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    if (xml.contains("{{")) {
                        // Step 1: Defragment - รวม placeholder ที่ Word แยกข้าม <w:t> กลับเป็นชิ้นเดียว
                        xml = defragmentPlaceholders(xml);

                        // Step 2: Simple replace - แทนค่า {{placeholder}} ทั้งหมด
                        for (Map.Entry<String, String> ph : placeholders.entrySet()) {
                            String token = "{{" + ph.getKey() + "}}";
                            if (xml.contains(token)) {
                                String value = ph.getValue() != null ? escapeXml(ph.getValue()) : "";
                                xml = xml.replace(token, value);
                            }
                        }

                        // Step 3: Checkbox rendering - ☑/☐ → MS Gothic font runs
                        xml = renderCheckboxes(xml);

                        data = xml.getBytes(StandardCharsets.UTF_8);
                    }
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
            // (อาจเป็นครึ่งแรกของ {{)
            boolean hasIncompleteToken = textContent.contains("{{") && !hasCompleteTokens(textContent);
            boolean endsWithBrace = textContent.endsWith("{");
            if (hasIncompleteToken || endsWithBrace) {
                // เริ่ม defragmentation: สะสม text จาก <w:t> ถัดๆ ไป จนเจอ }}
                out.append(xml, pos, tTagEnd + 1); // copy จนถึง > ของ <w:t>

                StringBuilder accumulated = new StringBuilder(textContent);
                List<int[]> subsequentTexts = new ArrayList<>();
                int scanPos = tBlockEnd;

                while (!hasCompleteTokens(accumulated.toString()) && scanPos < xml.length()) {
                    // หา <w:t> ถัดไปในขอบเขตที่สมเหตุสมผล (ไม่ข้าม paragraph)
                    int nextP = xml.indexOf("</w:p>", scanPos);
                    int nextT = xml.indexOf("<w:t", scanPos);

                    if (nextT == -1 || (nextP != -1 && nextT > nextP)) {
                        // ไม่เจอ <w:t> ก่อน </w:p> → หยุด
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
                return true; // ไม่มี {{ เหลือ
            int closeIdx = text.indexOf("}}", openIdx);
            if (closeIdx == -1)
                return false; // มี {{ แต่ไม่มี }}
            idx = closeIdx + 2;
        }
        return true;
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

    public byte[] convertDocxToPdf(byte[] docxBytes) throws IOException {
        Path tempDir = Files.createTempDirectory("docx-to-pdf-");
        Path tempDocx = tempDir.resolve("input.docx");
        Path tempPdf = tempDir.resolve("input.pdf");
        Files.write(tempDocx, docxBytes);

        try {
            String soffice = findLibreOffice();
            ProcessBuilder pb = new ProcessBuilder(
                    soffice, "--headless", "--convert-to", "pdf",
                    "--outdir", tempDir.toString(), tempDocx.toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());

            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("LibreOffice conversion interrupted", e);
            }

            if (exitCode != 0) {
                throw new IOException("LibreOffice conversion failed (exit " + exitCode + "): " + output);
            }

            if (!Files.exists(tempPdf)) {
                throw new IOException("PDF conversion produced no output");
            }
            return Files.readAllBytes(tempPdf);
        } finally {
            try {
                Files.deleteIfExists(tempPdf);
                Files.deleteIfExists(tempDocx);
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {
            }
        }
    }

    private String findLibreOffice() throws IOException {
        String[] candidates = {
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/opt/homebrew/bin/soffice",
                "/usr/bin/soffice",
                "/usr/bin/libreoffice",
                "/usr/local/bin/soffice",
        };

        try {
            Process p = new ProcessBuilder("which", "soffice").start();
            String path = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0 && !path.isEmpty())
                return path;
        } catch (Exception ignored) {
        }

        for (String c : candidates) {
            if (new File(c).exists())
                return c;
        }

        throw new IOException("LibreOffice not found. Install: brew install --cask libreoffice");
    }

    // =====================================================================
    // Utilities
    // =====================================================================

    private String escapeXml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
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
