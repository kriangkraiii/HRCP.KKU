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
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private Map<String, Object> parseJsonData(String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    // =====================================================================
    // Public API
    // =====================================================================

    public String generateDocument(Long requestId, int documentType, String jsonData, Integer copyNumber)
            throws IOException {
        Map<String, Object> dataMap = parseJsonData(jsonData);
        Map<String, String> placeholders = flattenMap(dataMap, "");

        String templateFile = TEMPLATE_DIR + "doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        String outputDir = OUTPUT_BASE_DIR + requestId + "/";
        Files.createDirectories(Path.of(outputDir));

        String outputFileName = (copyNumber != null && copyNumber > 0)
                ? "doc_" + documentType + "_copy_" + copyNumber + ".docx"
                : "doc_" + documentType + ".docx";
        String outputPath = outputDir + outputFileName;

        preprocessPlaceholders(documentType, placeholders);

        byte[] result = processTemplate(resource.getInputStream(), placeholders, documentType);

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(result);
        }

        return outputPath;
    }

    public List<String> generateDocument4Copies(Long requestId, String jsonData,
            List<Map<String, String>> committeeMembers) throws IOException {
        Map<String, Object> baseDataMap = parseJsonData(jsonData);
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
        Map<String, Object> dataMap = parseJsonData(jsonData);
        Map<String, String> placeholders = flattenMap(dataMap, "");

        String templateFile = TEMPLATE_DIR + "doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        preprocessPlaceholders(documentType, placeholders);

        return processTemplate(resource.getInputStream(), placeholders, documentType);
    }

    /**
     * Renders a Phase 1 document with signature images stamped in.
     *
     * <p>Same pipeline as {@link #generatePreviewDocx}; passing an empty list
     * produces an identical file, so callers do not have to branch on whether
     * anything has been signed yet.
     */
    public byte[] generateSignedDocx(int documentType, String jsonData,
            List<StampedSignature> signatures) throws IOException {
        Map<String, Object> dataMap = parseJsonData(jsonData);
        Map<String, String> placeholders = flattenMap(dataMap, "");

        String templateFile = TEMPLATE_DIR + "doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        preprocessPlaceholders(documentType, placeholders);

        return processTemplate(resource.getInputStream(), placeholders, documentType, signatures);
    }

    /**
     * As {@link #generateSignedDocx}, additionally printing the verification
     * footer. Used for the finished, fully-signed copy.
     */
    public byte[] generateSignedDocx(int documentType, String jsonData,
            List<StampedSignature> signatures, VerificationStamp verification) throws IOException {
        Map<String, Object> dataMap = parseJsonData(jsonData);
        Map<String, String> placeholders = flattenMap(dataMap, "");

        ClassPathResource resource = new ClassPathResource(TEMPLATE_DIR + "doc_" + documentType + ".docx");
        preprocessPlaceholders(documentType, placeholders);
        return processTemplate(resource.getInputStream(), placeholders, documentType, signatures, verification);
    }

    /** Phase 2 counterpart of the verification-stamped render. */
    public byte[] generateSignedP2Docx(int documentType, String jsonData,
            List<StampedSignature> signatures, VerificationStamp verification) throws IOException {
        Map<String, Object> dataMap = parseJsonData(jsonData);
        Map<String, String> placeholders = flattenMap(dataMap, "");
        mapUsedCheckboxes(placeholders);
        mapMethod3Fields(placeholders);
        aliasFirstRowFields(placeholders);

        ClassPathResource resource = new ClassPathResource(TEMPLATE_DIR + "Phase2/p2doc_" + documentType + ".docx");
        preprocessPlaceholders(documentType, placeholders);
        return processTemplate(resource.getInputStream(), placeholders, documentType, signatures, verification);
    }

    /** Phase 2 counterpart of {@link #generateSignedDocx}. */
    public byte[] generateSignedP2Docx(int documentType, String jsonData,
            List<StampedSignature> signatures) throws IOException {
        Map<String, Object> dataMap = parseJsonData(jsonData);
        Map<String, String> placeholders = flattenMap(dataMap, "");
        mapUsedCheckboxes(placeholders);
        mapMethod3Fields(placeholders);
        aliasFirstRowFields(placeholders);

        String templateFile = TEMPLATE_DIR + "Phase2/p2doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        preprocessPlaceholders(documentType, placeholders);

        return processTemplate(resource.getInputStream(), placeholders, documentType, signatures);
    }

    public byte[] generatePreviewDocxForCopy(int documentType, String jsonData,
            String committeeName, String committeePosition) throws IOException {
        Map<String, Object> dataMap = parseJsonData(jsonData);
        dataMap.put("committee_name", committeeName);
        dataMap.put("committee_position", committeePosition);

        return generatePreviewDocx(documentType, objectMapper.writeValueAsString(dataMap));
    }

    public byte[] getDocumentBytes(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return null;
        }
        return Files.readAllBytes(path);
    }

    public File getDocumentFile(String filePath) {
        return new File(filePath);
    }

    // =====================================================================
    // Phase 2: Position Request Document Generation
    // =====================================================================

    /** Preview-only (in-memory) for Phase 2 position documents */
    public byte[] generateP2PreviewDocx(int documentType, String jsonData) throws IOException {
        Map<String, Object> dataMap = parseJsonData(jsonData);
        Map<String, String> placeholders = flattenMap(dataMap, "");
        mapUsedCheckboxes(placeholders);
        mapMethod3Fields(placeholders);
        aliasFirstRowFields(placeholders);

        String templateFile = TEMPLATE_DIR + "Phase2/p2doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        preprocessPlaceholders(documentType, placeholders);

        return processTemplate(resource.getInputStream(), placeholders, documentType);
    }

    public String generateP2Document(com.ecom.academic.model.PositionRequest request,
            int documentType, String jsonData) throws IOException {
        Map<String, Object> dataMap = parseJsonData(jsonData);
        Map<String, String> placeholders = flattenMap(dataMap, "");
        // ต้องประมวลผลชุดเดียวกับ generateP2PreviewDocx มิฉะนั้นเอกสารที่บันทึกจริง
        // จะไม่ตรงกับที่ผู้ใช้เห็นในหน้า "ดูตัวอย่างเอกสาร"
        mapUsedCheckboxes(placeholders);
        mapMethod3Fields(placeholders);
        aliasFirstRowFields(placeholders);

        String templateFile = TEMPLATE_DIR + "Phase2/p2doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        String outputDir = OUTPUT_BASE_DIR + "position/" + request.getId() + "/";
        Files.createDirectories(Path.of(outputDir));

        String outputPath = outputDir + "p2doc_" + documentType + ".docx";

        preprocessPlaceholders(documentType, placeholders);

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
        return processTemplate(templateStream, placeholders, docType, List.of());
    }

    /**
     * As above, additionally stamping signature images into the document.
     *
     * <p>With an empty signature list this behaves exactly as it always has, so
     * documents that never enter the signing flow are byte-for-byte unchanged.
     */
    private byte[] processTemplate(InputStream templateStream, Map<String, String> placeholders, int docType,
            List<StampedSignature> signatures) throws IOException {
        return processTemplate(templateStream, placeholders, docType, signatures, null);
    }

    private byte[] processTemplate(InputStream templateStream, Map<String, String> placeholders, int docType,
            List<StampedSignature> signatures, VerificationStamp verification) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        // Relationship ids and media filenames are decided before the zip is
        // walked: [Content_Types].xml and document.xml.rels are emitted ahead of
        // document.xml, and all three have to agree on the same names.
        List<PreparedSignature> prepared = prepareSignatures(signatures);

        // The QR rides the same image plumbing as a signature: one more part,
        // one more relationship. anchorPlaceholder stays null because it is
        // placed by position, not by anchor.
        PreparedSignature qr = prepareVerificationQr(verification, prepared.size());
        if (qr != null) {
            prepared = new ArrayList<>(prepared);
            prepared.add(qr);
        }

        try (ZipInputStream zis = new ZipInputStream(templateStream);
                ZipOutputStream zos = new ZipOutputStream(result)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();
                String entryName = entry.getName();

                if (entryName.endsWith(".xml") || entryName.endsWith(".xml.rels")) {
                    String xml = new String(data, StandardCharsets.UTF_8);

                    // Step 0: ลบ descr URL ใน drawing และ w:bdr frame — ป้องกัน LibreOffice
                    // แสดงกรอบสี่เหลี่ยมรอบรูปภาพใน PDF
                    xml = xml.replaceAll(" descr=\"https://[^\"]*\"", "");
                    xml = xml.replaceAll("<w:bdr[^>]*w:frame=\"1\"[^/]*/>", "");

                    // Declare the image part and its relationship. Deliberately
                    // outside the {{ }} branch below: neither of these two parts
                    // holds placeholders, so they would never be visited there.
                    if (!prepared.isEmpty()) {
                        if (entryName.equals("[Content_Types].xml")) {
                            xml = ensurePngContentType(xml);
                        } else if (entryName.equals("word/_rels/document.xml.rels")) {
                            xml = appendImageRelationships(xml, prepared);
                        }
                    }

                    if (xml.contains("{{")) {
                        // Step 1: Defragment - รวม placeholder ที่ Word แยกข้าม <w:t> กลับเป็นชิ้นเดียว
                        xml = defragmentPlaceholders(xml);

                        // Step 1.5: Dynamic row cloning - เพิ่มแถวตารางสำหรับนวิจัยที่เกิน 5 รายการ
                        xml = expandDynamicRows(xml, placeholders);

                        // Step 1.6: Co-author signature cloning - เพิ่มลายเซ็นผู้ร่วมงานในเอกสารที่ 9
                        xml = expandCoauthorSignatures(xml, placeholders);

                        // Step 1.7: Signature stamping. Must sit between
                        // defragmentation and replacement: before it, the anchor
                        // token is split across runs in most templates; after it,
                        // the token it anchors on no longer exists.
                        if (!prepared.isEmpty() && entryName.equals("word/document.xml")) {
                            xml = insertSignatures(xml, prepared);
                            if (verification != null) {
                                xml = appendVerificationBlock(xml, verification, qr);
                            }
                        }

                        // Step 2: Simple replace - แทนค่า {{placeholder}} ทั้งหมด
                        for (Map.Entry<String, String> ph : placeholders.entrySet()) {
                            String token = "{{" + ph.getKey() + "}}";
                            if (xml.contains(token)) {
                                String raw = ph.getValue();
                                String value = (raw == null || raw.isBlank())
                                        ? blankFormFiller(ph.getKey())
                                        : escapeXml(raw);
                                xml = xml.replace(token, value);
                            }
                        }

                        // Step 2.5: Cleanup - {{...}} ที่เหลือซึ่งไม่มีค่าจากฟอร์ม
                        // ช่องผลงานที่ผู้ขอไม่ได้ใช้จะคืนเป็นจุดไข่ปลา/ช่องติ๊กว่าง
                        // ให้เหมือนแบบฟอร์มเปล่า ส่วนที่เหลือลบทิ้งตามเดิม
                        xml = fillRemainingPlaceholders(xml);

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

                ZipEntry newEntry = new ZipEntry(entryName);
                zos.putNextEntry(newEntry);
                zos.write(data);
                zos.closeEntry();
            }

            // Image parts last. Entry order is not significant to Word or
            // LibreOffice, and appending leaves the template's own entries alone.
            for (PreparedSignature sig : prepared) {
                zos.putNextEntry(new ZipEntry(WORD_MEDIA_DIR + sig.mediaName()));
                zos.write(sig.pngBytes());
                zos.closeEntry();
            }
        }

        return result.toByteArray();
    }

    // =====================================================================
    // Step 1.7: Signature Stamping
    // =====================================================================

    /**
     * A signature image to place in a document.
     *
     * @param anchorPlaceholder the placeholder naming the signer, without braces
     *                          — e.g. {@code dean_name}. The image is stamped on
     *                          the signature line belonging to that name.
     */
    public record StampedSignature(String anchorPlaceholder, byte[] pngBytes, int widthPx, int heightPx) {
    }

    /**
     * The verification footer printed on a fully-signed document.
     *
     * <p>Appended at the end of the body rather than anchored to a placeholder,
     * so it needs no change to any of the {@code .docx} templates — none of them
     * has anywhere to put this.
     *
     * @param code       the verification code, printed for anyone re-typing it
     * @param caption    the Thai line above the code
     * @param qrPngBytes the QR image, or null to print the code as text only
     */
    public record VerificationStamp(String code, String caption, byte[] qrPngBytes) {
    }

    /** A {@link StampedSignature} with its assigned part name, id and print size. */
    private record PreparedSignature(
            String anchorPlaceholder, byte[] pngBytes,
            String mediaName, String relationshipId, int drawingId,
            long widthEmu, long heightEmu) {
    }

    private static final String WORD_MEDIA_DIR = "word/media/";

    /** EMUs per centimetre. 914400 EMU = 1 inch = 2.54 cm. */
    private static final long EMU_PER_CM = 360000L;

    /** Printed width of a stamped signature. Fits the dotted line in every template. */
    private static final long SIGNATURE_WIDTH_EMU = (long) (3.2 * EMU_PER_CM);

    /** Ceiling on printed height, so a tall image cannot push the following line down. */
    private static final long SIGNATURE_MAX_HEIGHT_EMU = (long) (1.2 * EMU_PER_CM);

    /** EMUs per twip, the unit Word measures line heights in. 1 twip = 1/1440 inch. */
    private static final long EMU_PER_TWIP = 635L;

    /**
     * Height every stamped signature line is pinned to.
     *
     * <p>The ceiling rather than each image's own height: a row stays level only
     * if all of its signature lines are the same height, whatever shape the
     * signatures happen to be.
     */
    private static final long SIGNATURE_LINE_HEIGHT_TWIPS = SIGNATURE_MAX_HEIGHT_EMU / EMU_PER_TWIP;

    /** A span of the document to swap for new markup. */
    private record Edit(int start, int end, String markup) {
    }

    /**
     * Starting id for {@code docPr}/{@code cNvPr} elements.
     *
     * <p>High enough to stay clear of the ids Word already assigned to the logos
     * and headers in these templates, which count up from 1. Duplicate ids make
     * Word declare the file corrupt.
     */
    private static final int SIGNATURE_DRAWING_ID_BASE = 9001;

    /** Characters used to draw the "sign here" rule; several templates mix them. */
    private static final String DOT_LEADER_CHARS = ".…ฯ";

    private List<PreparedSignature> prepareSignatures(List<StampedSignature> signatures) {
        if (signatures == null || signatures.isEmpty()) {
            return List.of();
        }
        List<PreparedSignature> prepared = new ArrayList<>();
        int index = 0;
        for (StampedSignature sig : signatures) {
            if (sig.pngBytes() == null || sig.pngBytes().length == 0 || sig.anchorPlaceholder() == null) {
                continue;
            }
            index++;

            // Scale to a fixed width, then shrink further if that would make the
            // image taller than a line of text.
            long width = SIGNATURE_WIDTH_EMU;
            long height = sig.widthPx() > 0
                    ? Math.round(width * (double) sig.heightPx() / sig.widthPx())
                    : SIGNATURE_MAX_HEIGHT_EMU;
            if (height > SIGNATURE_MAX_HEIGHT_EMU) {
                width = Math.round(width * (double) SIGNATURE_MAX_HEIGHT_EMU / height);
                height = SIGNATURE_MAX_HEIGHT_EMU;
            }

            prepared.add(new PreparedSignature(
                    sig.anchorPlaceholder(),
                    sig.pngBytes(),
                    "hrcpsig" + index + ".png",
                    "rIdHrcpSig" + index,
                    SIGNATURE_DRAWING_ID_BASE + index,
                    width,
                    Math.max(height, 1)));
        }
        return prepared;
    }

    /**
     * Prepares the verification QR as one more image part.
     *
     * @param usedSlots how many signature images already have names assigned, so
     *                  the QR does not collide with them
     * @return the prepared part, or null when there is no QR to print
     */
    private PreparedSignature prepareVerificationQr(VerificationStamp verification, int usedSlots) {
        if (verification == null || verification.qrPngBytes() == null
                || verification.qrPngBytes().length == 0) {
            return null;
        }
        int index = usedSlots + 1;
        long side = (long) (2.2 * EMU_PER_CM);
        return new PreparedSignature(
                null, // placed at the end of the body, not at an anchor
                verification.qrPngBytes(),
                "hrcpsig" + index + ".png",
                "rIdHrcpSig" + index,
                SIGNATURE_DRAWING_ID_BASE + index,
                side, side);
    }

    /**
     * Appends the verification footer to the end of the document body.
     *
     * <p>Inserted before {@code <w:sectPr>} — the section properties must stay
     * the last child of {@code <w:body>}, and putting content after them makes
     * Word treat the file as damaged.
     */
    private String appendVerificationBlock(String xml, VerificationStamp verification, PreparedSignature qr) {
        int bodyEnd = xml.lastIndexOf("</w:body>");
        if (bodyEnd == -1) {
            return xml;
        }

        // Anchor to the final sectPr if the body has one.
        int insertAt = bodyEnd;
        int sectPr = xml.lastIndexOf("<w:sectPr", bodyEnd);
        if (sectPr != -1) {
            insertAt = sectPr;
        }

        String small = "<w:rPr><w:rFonts w:ascii=\"TH Sarabun New\" w:hAnsi=\"TH Sarabun New\""
                + " w:cs=\"TH Sarabun New\"/><w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/><w:cs/></w:rPr>";
        String centered = "<w:pPr><w:jc w:val=\"center\"/></w:pPr>";

        StringBuilder block = new StringBuilder();
        // A rule, so the footer reads as system-added rather than part of the form.
        block.append("<w:p><w:pPr><w:pBdr><w:top w:val=\"single\" w:sz=\"4\" w:space=\"1\" w:color=\"auto\"/>")
                .append("</w:pBdr><w:jc w:val=\"center\"/></w:pPr></w:p>");

        block.append("<w:p>").append(centered).append("<w:r>").append(small)
                .append("<w:t xml:space=\"preserve\">").append(escapeXml(verification.caption()))
                .append("</w:t></w:r></w:p>");

        if (qr != null) {
            block.append("<w:p>").append(centered).append(signatureDrawingRun(qr)).append("</w:p>");
        }

        block.append("<w:p>").append(centered).append("<w:r>").append(small)
                .append("<w:t xml:space=\"preserve\">").append(escapeXml(verification.code()))
                .append("</w:t></w:r></w:p>");

        return xml.substring(0, insertAt) + block + xml.substring(insertAt);
    }

    /** Declares the png part type, unless the template already does. */
    private String ensurePngContentType(String xml) {
        if (xml.contains("Extension=\"png\"")) {
            return xml;
        }
        int insertAt = xml.indexOf("<Types");
        if (insertAt == -1) {
            return xml;
        }
        insertAt = xml.indexOf('>', insertAt);
        if (insertAt == -1) {
            return xml;
        }
        insertAt++;
        return xml.substring(0, insertAt)
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + xml.substring(insertAt);
    }

    /** Adds one image relationship per signature to the document's rels part. */
    private String appendImageRelationships(String xml, List<PreparedSignature> signatures) {
        int closeAt = xml.lastIndexOf("</Relationships>");
        if (closeAt == -1) {
            return xml;
        }
        StringBuilder rels = new StringBuilder();
        for (PreparedSignature sig : signatures) {
            rels.append("<Relationship Id=\"").append(sig.relationshipId())
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\"")
                    .append(" Target=\"media/").append(sig.mediaName()).append("\"/>");
        }
        return xml.substring(0, closeAt) + rels + xml.substring(closeAt);
    }

    /**
     * Builds the run that actually shows the picture.
     *
     * <p>The {@code a} and {@code pic} namespaces are declared inline rather than
     * on the document root — that is what Word itself emits, and it means the
     * templates need no modification. {@code wp} and {@code r} are already
     * declared on {@code <w:document>} in every template here.
     */
    private String signatureDrawingRun(PreparedSignature sig) {
        String name = "Signature " + sig.drawingId();
        return "<w:r><w:rPr><w:sz w:val=\"2\"/><w:szCs w:val=\"2\"/></w:rPr><w:drawing>"
                + "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
                + "<wp:extent cx=\"" + sig.widthEmu() + "\" cy=\"" + sig.heightEmu() + "\"/>"
                + "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>"
                + "<wp:docPr id=\"" + sig.drawingId() + "\" name=\"" + name + "\"/>"
                + "<wp:cNvGraphicFramePr>"
                + "<a:graphicFrameLocks"
                + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" noChangeAspect=\"1\"/>"
                + "</wp:cNvGraphicFramePr>"
                + "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                + "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:nvPicPr>"
                + "<pic:cNvPr id=\"" + sig.drawingId() + "\" name=\"" + name + "\"/>"
                + "<pic:cNvPicPr/>"
                + "</pic:nvPicPr>"
                + "<pic:blipFill>"
                + "<a:blip r:embed=\"" + sig.relationshipId() + "\"/>"
                + "<a:stretch><a:fillRect/></a:stretch>"
                + "</pic:blipFill>"
                + "<pic:spPr>"
                + "<a:xfrm><a:off x=\"0\" y=\"0\"/>"
                + "<a:ext cx=\"" + sig.widthEmu() + "\" cy=\"" + sig.heightEmu() + "\"/></a:xfrm>"
                + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>"
                + "</pic:spPr>"
                + "</pic:pic>"
                + "</a:graphicData>"
                + "</a:graphic>"
                + "</wp:inline>"
                + "</w:drawing></w:r>";
    }

    /**
     * Places every prepared signature into the document body.
     *
     * <p>Works back to front so that inserting text never invalidates the offsets
     * of signatures still to be placed.
     */
    private String insertSignatures(String xml, List<PreparedSignature> signatures) {
        List<Edit> edits = new ArrayList<>();

        // Cells that gain a signature line of their own. Collected before any
        // spacer is worked out, because a cell that is being signed must never
        // also be padded.
        Set<Integer> signedCellStarts = new HashSet<>();
        // Cells already padded, keyed by where the cell starts. Two signers in
        // one row would otherwise each pad the third cell, stacking two blank
        // lines into it.
        Set<Integer> paddedCellStarts = new HashSet<>();

        record Placement(PreparedSignature sig, int[] namePara, int[] signLine) {
        }
        List<Placement> placements = new ArrayList<>();

        for (PreparedSignature sig : signatures) {
            // The verification QR shares this list for its image part but is
            // placed by position, not by anchor.
            if (sig.anchorPlaceholder() == null) {
                continue;
            }
            int[] namePara = findSignatureNameParagraph(xml, sig.anchorPlaceholder());
            if (namePara == null) {
                log.warn("No signature anchor found for placeholder {} — signature not stamped",
                        sig.anchorPlaceholder());
                continue;
            }
            int[] signLine = precedingSignatureLine(xml, namePara[0]);
            placements.add(new Placement(sig, namePara, signLine));

            // Only the branch that adds a paragraph makes its cell taller, so
            // only that one puts the row out of step.
            if (signLine == null) {
                int[] cell = enclosingElement(xml, namePara[0], "w:tc");
                if (cell != null) {
                    signedCellStarts.add(cell[0]);
                }
            }
        }

        for (Placement placement : placements) {
            PreparedSignature sig = placement.sig();
            int[] namePara = placement.namePara();

            if (placement.signLine() != null) {
                // The template draws a "ลงชื่อ ......" rule: sign on that line and
                // drop the dot leader, so the image is not pushed off the margin.
                int[] signLine = placement.signLine();
                String rewritten = stampOntoSignatureLine(xml.substring(signLine[0], signLine[1]), sig);
                edits.add(new Edit(signLine[0], signLine[1], rewritten));
                continue;
            }

            // No rule to sign on, so add a line above the name. It reuses the
            // name paragraph's own properties, which is what keeps the image
            // aligned with the name underneath it in every template.
            String pPr = paragraphProperties(xml, namePara[0], namePara[1]);
            edits.add(new Edit(namePara[0], namePara[0],
                    "<w:p>" + withFixedSignatureHeight(pPr) + signatureDrawingRun(sig) + "</w:p>"));

            edits.addAll(spacerEditsForRowOf(xml, namePara[0], signedCellStarts, paddedCellStarts));
        }

        // Apply back to front so that each edit's offsets are still valid when it
        // is its turn.
        edits.sort(Comparator.comparingInt(Edit::start).reversed());

        StringBuilder out = new StringBuilder(xml);
        for (Edit edit : edits) {
            out.replace(edit.start(), edit.end(), edit.markup());
        }
        return out.toString();
    }

    /**
     * Finds the paragraph holding the signer's printed name.
     *
     * <p>An anchor such as {@code {{applicant_name}}} usually appears more than
     * once — once in the body prose and once under the signature line. The
     * signature one is always the parenthesised form, {@code ({{name}})}, which
     * is what this picks out. Without that test doc_0 and p2doc_1 would be
     * stamped in the middle of a sentence.
     *
     * @return {@code {start, end}} of the paragraph, or null if there is none
     */
    private int[] findSignatureNameParagraph(String xml, String placeholderKey) {
        String token = "{{" + placeholderKey + "}}";
        int[] fallback = null;

        int from = 0;
        while (true) {
            int hit = xml.indexOf(token, from);
            if (hit == -1) {
                break;
            }
            from = hit + token.length();

            int[] para = enclosingParagraph(xml, hit);
            if (para == null) {
                continue;
            }
            String text = paragraphText(xml.substring(para[0], para[1])).trim();
            if (text.startsWith("(") && text.contains(")")) {
                return para;
            }
            fallback = para;
        }
        return fallback;
    }

    /**
     * The signature rule immediately above a name, if the template draws one.
     *
     * <p>Recognised either by the word "ลงชื่อ" or by a run of dot leaders — some
     * templates (doc_2, p2doc_6) print the rule with no label at all.
     *
     * @return {@code {start, end}} of that paragraph, or null when there is none
     */
    private int[] precedingSignatureLine(String xml, int nameParagraphStart) {
        int[] previous = paragraphEndingBefore(xml, nameParagraphStart);
        if (previous == null) {
            return null;
        }
        String text = paragraphText(xml.substring(previous[0], previous[1]));
        if (text.contains("ลงชื่อ")) { // ลงชื่อ
            return previous;
        }
        long leaders = text.chars().filter(c -> DOT_LEADER_CHARS.indexOf(c) >= 0).count();
        return leaders >= 5 ? previous : null;
    }

    /**
     * Keeps the names in a signature row level when only some of them signed.
     *
     * <p>Adding the signature line makes its cell one paragraph taller, and a
     * table row is as tall as its tallest cell. Cells are top-aligned unless the
     * template says otherwise — none of these do — so a name in a cell that did
     * not grow rides up to the top of the row and ends up floating well above
     * the job title printed underneath it, while the signed name sits low. Every
     * name in the row gets the same blank line, so they stay on one level and
     * each stays against its own title.
     *
     * <p>Bottom-aligning the row would be shorter, but doc_2's signature cells
     * are comment boxes whose text runs from the top edge down; pushing that
     * whole block to the bottom would open a hole above it. A blank line leaves
     * the existing layout alone.
     *
     * @param signedCellStarts cells getting a signature of their own, which must
     *                         not also be padded
     * @param paddedCellStarts cells already padded; added to as it goes, so that
     *                         two signers in one row do not both pad a third
     */
    private List<Edit> spacerEditsForRowOf(String xml, int signedNameParagraphStart,
            Set<Integer> signedCellStarts, Set<Integer> paddedCellStarts) {

        int[] row = enclosingElement(xml, signedNameParagraphStart, "w:tr");
        if (row == null) {
            return List.of(); // not a table: nothing to keep in line
        }

        List<Edit> spacers = new ArrayList<>();
        for (int[] cell : childElements(xml, row[0], row[1], "w:tc")) {
            if (signedCellStarts.contains(cell[0]) || !paddedCellStarts.add(cell[0])) {
                continue;
            }
            int[] namePara = signatureNameParagraphIn(xml, cell[0], cell[1]);
            if (namePara == null) {
                // Not a signature cell — padding it would drop a blank line into
                // unrelated content.
                paddedCellStarts.remove(cell[0]);
                continue;
            }
            String pPr = paragraphProperties(xml, namePara[0], namePara[1]);
            spacers.add(new Edit(namePara[0], namePara[0], "<w:p>" + withFixedSignatureHeight(pPr) + "</w:p>"));
        }
        return spacers;
    }

    /**
     * Pins a paragraph to the height a stamped signature occupies.
     *
     * <p>Applied to the signature line and to the blank line standing in for a
     * missing one alike. Without it the row is only as level as the signatures
     * happen to be: images are scaled to a fixed width, so a wide flat signature
     * prints shorter than a tall one, and two people signing the same row would
     * still have their names at different heights.
     */
    private String withFixedSignatureHeight(String pPr) {
        String spacing = "<w:spacing w:before=\"0\" w:after=\"0\" w:line=\""
                + SIGNATURE_LINE_HEIGHT_TWIPS + "\" w:lineRule=\"atLeast\"/>";
        String rPr = "<w:rPr><w:sz w:val=\"2\"/><w:szCs w:val=\"2\"/></w:rPr>";

        if (pPr == null || pPr.isEmpty()) {
            return "<w:pPr>" + spacing + rPr + "</w:pPr>";
        }
        if (pPr.endsWith("/>")) { // <w:pPr/> — no children yet
            return "<w:pPr>" + spacing + rPr + "</w:pPr>";
        }
        // Replace any spacing and font sizes the template set, so the line height
        // is governed purely by the signature drawing without font baseline descent.
        String cleaned = pPr.replaceAll("<w:spacing[^>]*/>", "")
                .replaceAll("<w:rPr>.*?</w:rPr>", "");
        int open = cleaned.indexOf('>');
        return open == -1 ? pPr : cleaned.substring(0, open + 1) + spacing + rPr + cleaned.substring(open + 1);
    }

    /**
     * The signer's printed name inside one table cell, if it holds one.
     *
     * <p>Recognised the same way {@link #findSignatureNameParagraph} does it —
     * the parenthesised form around a placeholder. Reading placeholders works
     * here because stamping runs after defragmentation but before replacement,
     * so the tokens are still present and each sits in a single run.
     *
     * @return {@code {start, end}} of the paragraph, or null when the cell has
     *         no name in it
     */
    private int[] signatureNameParagraphIn(String xml, int cellStart, int cellEnd) {
        for (int[] para : childElements(xml, cellStart, cellEnd, "w:p")) {
            String text = paragraphText(xml.substring(para[0], para[1])).trim();
            if (text.startsWith("(") && text.contains(")") && text.contains("{{")) {
                return para;
            }
        }
        return null;
    }

    /**
     * Puts the image on an existing signature rule.
     *
     * <p>The dot leader is replaced by the picture rather than pushed aside: a
     * line reading "ลงชื่อ ....... [signature]" would wrap on the narrower
     * templates and looks nothing like a signed document.
     */
    private String stampOntoSignatureLine(String paragraph, PreparedSignature sig) {
        String stripped = removeDotLeaders(paragraph);

        // Place the picture after the last run of the label so it sits where the
        // rule used to be, not before the word "ลงชื่อ".
        int lastRunEnd = stripped.lastIndexOf("</w:r>");
        if (lastRunEnd == -1) {
            int bodyEnd = stripped.lastIndexOf("</w:p>");
            return bodyEnd == -1
                    ? stripped
                    : stripped.substring(0, bodyEnd) + signatureDrawingRun(sig) + stripped.substring(bodyEnd);
        }
        lastRunEnd += "</w:r>".length();
        return stripped.substring(0, lastRunEnd) + signatureDrawingRun(sig) + stripped.substring(lastRunEnd);
    }

    /** Blanks dot leaders inside {@code <w:t>} text, leaving all markup intact. */
    private String removeDotLeaders(String paragraph) {
        StringBuilder out = new StringBuilder(paragraph.length());
        int pos = 0;
        while (pos < paragraph.length()) {
            int open = paragraph.indexOf("<w:t", pos);
            if (open == -1) {
                out.append(paragraph, pos, paragraph.length());
                break;
            }
            int tagEnd = paragraph.indexOf('>', open);
            if (tagEnd == -1 || paragraph.charAt(tagEnd - 1) == '/') {
                out.append(paragraph, pos, tagEnd == -1 ? paragraph.length() : tagEnd + 1);
                pos = tagEnd == -1 ? paragraph.length() : tagEnd + 1;
                continue;
            }
            int close = paragraph.indexOf("</w:t>", tagEnd);
            if (close == -1) {
                out.append(paragraph, pos, paragraph.length());
                break;
            }
            out.append(paragraph, pos, tagEnd + 1);

            String text = paragraph.substring(tagEnd + 1, close);
            // Only strip actual leaders — three or more in a row — so ordinary
            // full stops in a sentence survive.
            out.append(text.replaceAll("[" + java.util.regex.Pattern.quote(DOT_LEADER_CHARS) + "]{3,}", " "));

            out.append("</w:t>");
            pos = close + "</w:t>".length();
        }
        return out.toString();
    }

    /** Copies a paragraph's {@code <w:pPr>} block, or empty when it has none. */
    private String paragraphProperties(String xml, int paragraphStart, int paragraphEnd) {
        String paragraph = xml.substring(paragraphStart, paragraphEnd);
        int open = paragraph.indexOf("<w:pPr");
        if (open == -1) {
            return "";
        }
        int tagEnd = paragraph.indexOf('>', open);
        if (tagEnd != -1 && paragraph.charAt(tagEnd - 1) == '/') {
            return paragraph.substring(open, tagEnd + 1);
        }
        int close = paragraph.indexOf("</w:pPr>", open);
        return close == -1 ? "" : paragraph.substring(open, close + "</w:pPr>".length());
    }

    // =====================================================================
    // Paragraph scanning helpers
    // =====================================================================

    /**
     * Whether {@code <w:p} at this offset opens a paragraph.
     *
     * <p>Guards against {@code <w:pPr>} and {@code <w:pStyle>}, which share the
     * prefix and would otherwise be mistaken for paragraph starts.
     */
    private static boolean isParagraphTagAt(String xml, int idx) {
        int after = idx + 4; // past "<w:p"
        if (after >= xml.length()) {
            return false;
        }
        char c = xml.charAt(after);
        return c == '>' || c == '/' || Character.isWhitespace(c);
    }

    /**
     * The innermost {@code <w:p>...</w:p>} containing an offset.
     *
     * <p>Depth-counted rather than a nearest-tag search: paragraphs really do
     * nest inside text boxes and {@code mc:AlternateContent} blocks — doc_6 has
     * exactly that — and a {@code lastIndexOf("<w:p")} would land inside the
     * wrong one.
     *
     * @return {@code {start, end}} where end is past {@code </w:p>}, or null
     */
    private int[] enclosingParagraph(String xml, int offset) {
        Deque<Integer> open = new ArrayDeque<>();
        int pos = 0;
        while (pos < xml.length()) {
            int nextOpen = xml.indexOf("<w:p", pos);
            while (nextOpen != -1 && !isParagraphTagAt(xml, nextOpen)) {
                nextOpen = xml.indexOf("<w:p", nextOpen + 4);
            }
            int nextClose = xml.indexOf("</w:p>", pos);

            if (nextOpen == -1 && nextClose == -1) {
                break;
            }

            if (nextClose == -1 || (nextOpen != -1 && nextOpen < nextClose)) {
                int tagEnd = xml.indexOf('>', nextOpen);
                if (tagEnd == -1) {
                    break;
                }
                boolean selfClosing = xml.charAt(tagEnd - 1) == '/';
                if (!selfClosing) {
                    open.push(nextOpen);
                }
                pos = tagEnd + 1;
            } else {
                int end = nextClose + "</w:p>".length();
                Integer start = open.poll();
                if (start != null && start <= offset && offset < end) {
                    return new int[] { start, end };
                }
                pos = end;
            }
        }
        return null;
    }

    /**
     * The innermost {@code <tag>...</tag>} containing an offset.
     *
     * <p>Depth-counted for the same reason {@link #enclosingParagraph} is: these
     * templates nest tables inside table cells, so a {@code lastIndexOf} would
     * happily return an outer row that the offset is not really a child of.
     *
     * @param tag qualified name without brackets, e.g. {@code "w:tc"}
     * @return {@code {start, end}} where end is past the closing tag, or null
     */
    private int[] enclosingElement(String xml, int offset, String tag) {
        String open = "<" + tag;
        String close = "</" + tag + ">";
        Deque<Integer> stack = new ArrayDeque<>();
        int pos = 0;

        while (pos < xml.length()) {
            int nextOpen = indexOfElement(xml, open, pos);
            int nextClose = xml.indexOf(close, pos);

            if (nextOpen == -1 && nextClose == -1) {
                break;
            }
            if (nextClose == -1 || (nextOpen != -1 && nextOpen < nextClose)) {
                int tagEnd = xml.indexOf('>', nextOpen);
                if (tagEnd == -1) {
                    break;
                }
                if (xml.charAt(tagEnd - 1) != '/') { // self-closing has no body
                    stack.push(nextOpen);
                }
                pos = tagEnd + 1;
            } else {
                int end = nextClose + close.length();
                Integer start = stack.poll();
                if (start != null && start <= offset && offset < end) {
                    return new int[] { start, end };
                }
                pos = end;
            }
        }
        return null;
    }

    /**
     * Direct {@code <tag>} children of a span, in document order.
     *
     * <p>Only the outermost level: asking a row for its cells must not also
     * return the cells of a table nested inside one of them.
     */
    private List<int[]> childElements(String xml, int from, int to, String tag) {
        String open = "<" + tag;
        String close = "</" + tag + ">";
        List<int[]> children = new ArrayList<>();
        int depth = 0;
        int currentStart = -1;
        int pos = from;

        while (pos < to) {
            int nextOpen = indexOfElement(xml, open, pos);
            int nextClose = xml.indexOf(close, pos);
            if (nextOpen >= to) {
                nextOpen = -1;
            }
            if (nextClose >= to) {
                nextClose = -1;
            }
            if (nextOpen == -1 && nextClose == -1) {
                break;
            }

            if (nextClose == -1 || (nextOpen != -1 && nextOpen < nextClose)) {
                int tagEnd = xml.indexOf('>', nextOpen);
                if (tagEnd == -1) {
                    break;
                }
                if (xml.charAt(tagEnd - 1) != '/') {
                    if (depth == 0) {
                        currentStart = nextOpen;
                    }
                    depth++;
                }
                pos = tagEnd + 1;
            } else {
                int end = nextClose + close.length();
                depth--;
                if (depth == 0 && currentStart != -1) {
                    children.add(new int[] { currentStart, end });
                    currentStart = -1;
                }
                pos = end;
            }
        }
        return children;
    }

    /**
     * Finds an element's opening tag, ignoring longer names that share a prefix.
     *
     * <p>{@code <w:p} would otherwise match {@code <w:pPr>}, and {@code <w:tc}
     * would match {@code <w:tcPr>} — the same trap {@link #isParagraphTagAt}
     * exists to avoid.
     */
    private static int indexOfElement(String xml, String openTag, int from) {
        int hit = xml.indexOf(openTag, from);
        while (hit != -1) {
            int after = hit + openTag.length();
            if (after < xml.length()) {
                char c = xml.charAt(after);
                if (c == '>' || c == '/' || Character.isWhitespace(c)) {
                    return hit;
                }
            }
            hit = xml.indexOf(openTag, hit + openTag.length());
        }
        return -1;
    }

    /**
     * The paragraph closing immediately before a given offset.
     *
     * <p>Skips paragraphs that merely nest around the target — only a sibling
     * that has already closed counts as "the line above".
     *
     * @return {@code {start, end}}, or null when nothing precedes it
     */
    private int[] paragraphEndingBefore(String xml, int offset) {
        int searchFrom = 0;
        int[] best = null;
        Deque<Integer> open = new ArrayDeque<>();

        while (searchFrom < xml.length()) {
            int nextOpen = xml.indexOf("<w:p", searchFrom);
            while (nextOpen != -1 && !isParagraphTagAt(xml, nextOpen)) {
                nextOpen = xml.indexOf("<w:p", nextOpen + 4);
            }
            int nextClose = xml.indexOf("</w:p>", searchFrom);
            if (nextOpen == -1 && nextClose == -1) {
                break;
            }

            if (nextClose == -1 || (nextOpen != -1 && nextOpen < nextClose)) {
                if (nextOpen >= offset) {
                    break;
                }
                int tagEnd = xml.indexOf('>', nextOpen);
                if (tagEnd == -1) {
                    break;
                }
                if (xml.charAt(tagEnd - 1) != '/') {
                    open.push(nextOpen);
                }
                searchFrom = tagEnd + 1;
            } else {
                int end = nextClose + "</w:p>".length();
                if (end > offset) {
                    break;
                }
                Integer start = open.poll();
                if (start != null) {
                    best = new int[] { start, end };
                }
                searchFrom = end;
            }
        }
        return best;
    }

    /** The visible text of a paragraph: every {@code <w:t>} body, concatenated. */
    private String paragraphText(String paragraph) {
        StringBuilder text = new StringBuilder();
        int pos = 0;
        while (true) {
            int open = paragraph.indexOf("<w:t", pos);
            if (open == -1) {
                break;
            }
            int tagEnd = paragraph.indexOf('>', open);
            if (tagEnd == -1) {
                break;
            }
            if (paragraph.charAt(tagEnd - 1) == '/') {
                pos = tagEnd + 1;
                continue;
            }
            int close = paragraph.indexOf("</w:t>", tagEnd);
            if (close == -1) {
                break;
            }
            text.append(paragraph, tagEnd + 1, close);
            pos = close + "</w:t>".length();
        }
        return text.toString();
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
    /**
     * เทมเพลตบางฉบับ (เช่น p2doc_7) วาง placeholder ไว้ในบล็อกที่ตั้งใจให้ซ้ำได้
     * ({{?research_working_list}} … {{/research_working_list}}) จึงตั้งชื่อแบบ
     * ไม่มีเลขต่อท้าย เช่น {{is_published}} {{diss_journal}} แต่ฟอร์มส่งชื่อของ
     * รายการแรกมาเป็น is_published_1 / diss_journal_1 เสมอ
     *
     * ยังไม่มีการ implement การซ้ำบล็อก ดังนั้นเอกสารจึงแสดงได้แค่รายการแรก —
     * method นี้ทำหน้าที่เชื่อมชื่อของรายการแรกเข้ากับ placeholder ฐาน มิฉะนั้น
     * placeholder จะถูกลบทิ้งใน step 2.5 และช่องติ๊กจะหายไปทั้งกล่อง
     * (ไม่ขึ้นทั้ง ☑ และ ☐)
     *
     * เขียนทับเฉพาะกรณีที่ยังไม่มีค่าของชื่อฐานอยู่แล้ว เพื่อไม่ไปรบกวนเอกสาร
     * ที่ใช้ชื่อฐานเป็นฟิลด์จริงของตัวเอง
     */
    private void aliasFirstRowFields(Map<String, String> placeholders) {
        Map<String, String> toAdd = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith("_1"))
                continue;
            String base = key.substring(0, key.length() - 2);
            if (base.isEmpty() || placeholders.containsKey(base))
                continue;
            toAdd.putIfAbsent(base, entry.getValue());
        }
        placeholders.putAll(toAdd);
    }

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

    /** จุดเดียวสำหรับปรับ placeholder ก่อนแทนค่าลงเทมเพลต */
    private void preprocessPlaceholders(int documentType, Map<String, String> placeholders) {
        if (documentType == 4) {
            preprocessDoc4Placeholders(placeholders);
        } else if (documentType == 7) {
            preprocessDoc7Placeholders(placeholders);
        }
    }

    /**
     * เอกสารที่ 7: ครั้งที่ประชุมและวันที่ต้องพิมพ์เป็นเลขไทยในเอกสาร
     * แปลงตอนสร้างไฟล์เท่านั้น ข้อมูลที่บันทึกไว้ยังเป็นเลขอาราบิกเพื่อให้ฟอร์มแก้ไขได้ตามปกติ
     */
    private void preprocessDoc7Placeholders(Map<String, String> placeholders) {
        for (String key : new String[] { "meeting_no", "meeting_date", "sign_date" }) {
            String val = placeholders.get(key);
            if (val != null && !val.isBlank()) {
                placeholders.put(key, toThaiDigits(val));
            }
        }
    }

    /** แปลงตัวเลข Arabic เป็นเลขไทย เช่น "1/2569" → "๑/๒๕๖๙" */
    private static String toThaiDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= '0' && c <= '9' ? (char) ('๐' + (c - '0')) : c);
        }
        return sb.toString();
    }

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
    // Blank-form fillers — ช่องที่ผู้ขอไม่ได้กรอก
    // =====================================================================

    /**
     * ช่องผลงานของตำแหน่งที่ผู้ขอไม่ได้เสนอ (เช่น ยื่น ผศ. แต่แบบฟอร์มมีหัวข้อของ
     * รศ./ศ. อยู่ด้วย) เดิมถูกลบทิ้งจนเหลือบรรทัดว่างเปล่า ทำให้เอกสารที่พิมพ์ออกมา
     * ไม่เหมือนแบบฟอร์มเปล่าที่ยังมีจุดไข่ปลาและช่องติ๊กให้กรอกด้วยมือ
     * ตัวเติมชุดนี้จึงคืนจุดไข่ปลา/ช่องติ๊กว่างให้เฉพาะช่องเหล่านั้น
     * ส่วน placeholder อื่นยังคืนค่าว่างเหมือนเดิม
     */
    private static final String LONG_BLANK = ".".repeat(45);
    private static final String SHORT_BLANK = ".".repeat(12);

    /** ช่องติ๊ก "เคยใช้/ไม่เคยใช้" และช่องติ๊กของวิธีที่ ๓ */
    private static final java.util.regex.Pattern CHECKBOX_BLANK_KEY = java.util.regex.Pattern.compile(
            "^(asst|assoc|prof)_(not_used|is_used)_(reseach|research|other|book)_[0-9]+$"
                    + "|^(assoc|prof)_m3_(q1|q2|first|corresp)(_[0-9]+)?$");

    /** ช่องยาวที่กินทั้งบรรทัด — ชื่อผลงาน/ชื่อโครงการ/แหล่งทุน */
    private static final java.util.regex.Pattern LONG_BLANK_KEY = java.util.regex.Pattern.compile(
            "^(asst|assoc|prof)_(research|other|book)_working_[0-9]+$"
                    + "|^(assoc|prof)_method3_research_[0-9]+$"
                    + "|^(assoc|prof)_pi_(project|source)(_[0-9]+)?$");

    /**
     * ช่องสั้นที่แทรกกลางประโยค — ปี พ.ศ./ระดับคุณภาพ/จำนวนอ้างอิง และข้อ ๒.๓/๒.๔
     * ของแบบ ก.พ.ว. มข. ๐๓ (วิธี/สาขาวิชา/วันที่แต่งตั้ง ผศ. และ รศ.) ซึ่งผู้ขอที่
     * ยังไม่เคยดำรงตำแหน่งนั้นจะไม่ได้กรอก
     */
    private static final java.util.regex.Pattern SHORT_BLANK_KEY = java.util.regex.Pattern.compile(
            "^(asst|assoc|prof)_used_(research|other|book)_(year|level)_[0-9]+$"
                    + "|^(assoc|prof)_(scopus_stories_count|scopus_citation_count|h_index)$"
                    + "|^(assistant|associate)_(method|department|appointment_date)$"
                    + "|^(lecturer_appointment_date|current_salary|birth_date|age|years|months)$");

    private static final java.util.regex.Pattern LEFTOVER_PLACEHOLDER = java.util.regex.Pattern.compile(
            "\\{\\{([^}]+)\\}\\}");

    /** ค่าที่ใช้แทน placeholder ซึ่งผู้ขอไม่ได้กรอก */
    private String blankFormFiller(String key) {
        if (CHECKBOX_BLANK_KEY.matcher(key).matches()) {
            return "\u2610";
        }
        if (LONG_BLANK_KEY.matcher(key).matches()) {
            return LONG_BLANK;
        }
        if (SHORT_BLANK_KEY.matcher(key).matches()) {
            return SHORT_BLANK;
        }
        return "";
    }

    /**
     * แทน {{...}} ที่ยังเหลือหลัง step 2 (ไม่มี key นั้นในข้อมูลฟอร์มเลย)
     * ด้วยตัวเติมเดียวกับ step 2 — ช่องที่ไม่เข้าเกณฑ์จะถูกลบทิ้งเหมือนเดิม
     */
    private String fillRemainingPlaceholders(String xml) {
        java.util.regex.Matcher m = LEFTOVER_PLACEHOLDER.matcher(xml);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(blankFormFiller(m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
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

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationService.class);

    // =====================================================================
    // PDF Conversion (LibreOffice CLI & Hybrid Two-Tier Cache)
    // =====================================================================

    /** จำนวน slot พร้อมกันสำหรับ LibreOffice conversion */
    private static final int MAX_PDF_SLOTS = 4;
    private static final BlockingQueue<Integer> SLOT_POOL = new ArrayBlockingQueue<>(MAX_PDF_SLOTS);
    static {
        for (int i = 0; i < MAX_PDF_SLOTS; i++) {
            SLOT_POOL.offer(i);
        }
    }

    /** timeout ต่อการแปลง 1 ครั้ง — กัน process ค้างถาวร */
    private static final long PDF_TIMEOUT_SECONDS = 60;

    /** cache ผลการค้นหา soffice: null = ยังไม่เคยหา, "" = หาแล้วไม่เจอ */
    private volatile String cachedSofficePath = null;

    /**
     * L1 In-Memory LRU cache ของ PDF ที่แปลงแล้ว
     */
    private static final int PDF_CACHE_SIZE = 100;
    private final Map<String, byte[]> pdfCache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > PDF_CACHE_SIZE;
                }
            });

    /**
     * L2 Persistent Disk Cache Directory
     */
    private static final Path DISK_CACHE_DIR = Path.of(System.getProperty("java.io.tmpdir"), "hrcp-pdf-cache");

    /**
     * Persistent Profile Pool Directory (ไม่ต้องสร้าง/ลบโปรไฟล์ใหม่ทุกรอบเพื่อลด cold start I/O)
     */
    private static final Path BASE_PROFILE_DIR = Path.of(System.getProperty("java.io.tmpdir"), "hrcp-lo-profiles");

    /** LibreOffice พร้อมใช้งานหรือไม่ — ใช้ตัดสินใจว่าจะ preview เป็น PDF ได้ไหม */
    public boolean isPdfConversionAvailable() {
        return !resolveLibreOffice().isEmpty();
    }

    /**
     * แปลง DOCX → PDF โดยใช้ Two-Tier Cache (L1 Memory + L2 Disk Cache)
     *
     * <p>ถ้าไบต์ที่ส่งเข้ามาเป็น PDF อยู่แล้วจะคืนกลับไปเลย ไม่แปลงซ้ำ เพราะการ
     * ให้ LibreOffice import PDF แล้ว export ใหม่ จะทำลายการ map ตัวอักษรไทย
     * จนอ่านไม่ออก และมันพังแบบ "ได้ไฟล์ที่ดูปกติแต่ข้อความเพี้ยน" ไม่ใช่ error
     * จึงหลุดสายตาได้ง่ายมาก กันไว้ที่นี่ทีเดียวให้ครอบทุกจุดที่เรียกใช้
     */
    public byte[] convertDocxToPdfCached(byte[] docxBytes) throws IOException {
        if (isPdfBytes(docxBytes)) {
            log.warn("convertDocxToPdfCached ได้รับไฟล์ที่เป็น PDF อยู่แล้ว — คืนไฟล์เดิมกลับไป "
                    + "ไม่แปลงซ้ำ (ผู้เรียกน่าจะส่งผลลัพธ์ของ renderPdf มาผิดที่)");
            return docxBytes;
        }

        String key = sha256(docxBytes);
        
        // 1. ตรวจสอบ L1 In-Memory Cache (< 1ms)
        byte[] cached = pdfCache.get(key);
        if (cached != null) {
            return cached;
        }

        // 2. ตรวจสอบ L2 Persistent Disk Cache (~2-5ms)
        Path diskCachedFile = DISK_CACHE_DIR.resolve(key + ".pdf");
        if (Files.exists(diskCachedFile)) {
            try {
                byte[] diskBytes = Files.readAllBytes(diskCachedFile);
                if (diskBytes.length > 0) {
                    pdfCache.put(key, diskBytes);
                    log.debug("L2 Disk Cache hit for PDF key: {}", key);
                    return diskBytes;
                }
            } catch (IOException e) {
                log.warn("Failed to read from disk cache: {}", e.getMessage());
            }
        }

        // 3. Cache miss → แปลงสดด้วย LibreOffice (~300-500ms ด้วย Profile Pool)
        byte[] pdf = convertDocxToPdf(docxBytes);
        
        // เก็บลงทั้ง L1 และ L2
        pdfCache.put(key, pdf);
        try {
            Files.createDirectories(DISK_CACHE_DIR);
            Files.write(diskCachedFile, pdf, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.warn("Failed to write to disk cache: {}", e.getMessage());
        }

        return pdf;
    }

    /** ไบต์ชุดนี้เป็นไฟล์ PDF อยู่แล้วหรือไม่ (ดูจาก magic bytes) */
    private static boolean isPdfBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-';
    }

    public byte[] convertDocxToPdf(byte[] docxBytes) throws IOException {
        String soffice = resolveLibreOffice();
        if (soffice.isEmpty()) {
            throw new IOException("LibreOffice not found. " +
                    "Windows: https://www.libreoffice.org/download | " +
                    "Mac: brew install --cask libreoffice | " +
                    "Linux: sudo apt install libreoffice-writer");
        }

        Integer slot;
        try {
            slot = SLOT_POOL.poll(PDF_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (slot == null) {
                throw new IOException("PDF conversion timeout waiting for a LibreOffice slot");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PDF conversion interrupted while waiting for a slot", e);
        }

        long startTime = System.currentTimeMillis();
        Path tempDir = Files.createTempDirectory("docx-to-pdf-job-");
        Path tempDocx = tempDir.resolve("input.docx");
        Path tempPdf = tempDir.resolve("input.pdf");
        
        // ใช้ Persistent Profile ประจำ Slot เพื่อรักษา Font cache & Registry ให้ไม่ต้อง Rebuild ทุกรอบ
        Path profileDir = BASE_PROFILE_DIR.resolve("slot-" + slot);
        try {
            Files.createDirectories(profileDir);
        } catch (IOException ignored) {
        }
        
        Files.write(tempDocx, docxBytes);

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    soffice,
                    "-env:UserInstallation=" + profileDir.toUri(),
                    "--headless", "--norestore", "--nolockcheck", "--nodefault", "--nologo",
                    "--convert-to", "pdf",
                    "--outdir", tempDir.toString(), tempDocx.toString());
            
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

            byte[] result = Files.readAllBytes(tempPdf);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("LibreOffice converted DOCX to PDF ({} bytes) in {} ms [Slot {}]", result.length, elapsed, slot);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice conversion interrupted", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            SLOT_POOL.offer(slot);
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
