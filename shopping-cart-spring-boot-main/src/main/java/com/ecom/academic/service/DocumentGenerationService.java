package com.ecom.academic.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DocumentGenerationService {

    private static final String TEMPLATE_DIR = "templates/docx/";
    private static final String OUTPUT_BASE_DIR = "uploads/academic/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateDocument(Long requestId, int documentType, String jsonData, Integer copyNumber)
            throws IOException {
        Map<String, Object> dataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> placeholders = flattenMap(dataMap, "");

        String templateFile = TEMPLATE_DIR + "doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        String outputDir = OUTPUT_BASE_DIR + requestId + "/";
        Files.createDirectories(Path.of(outputDir));

        String outputFileName;
        if (copyNumber != null && copyNumber > 0) {
            outputFileName = "doc_" + documentType + "_copy_" + copyNumber + ".docx";
        } else {
            outputFileName = "doc_" + documentType + ".docx";
        }
        String outputPath = outputDir + outputFileName;

        try (FileInputStream fis = new FileInputStream(resource.getFile());
                XWPFDocument document = new XWPFDocument(fis)) {

            replacePlaceholdersInParagraphs(document.getParagraphs(), placeholders);

            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        replacePlaceholdersInParagraphs(cell.getParagraphs(), placeholders);
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                document.write(fos);
            }
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

    /**
     * สร้าง DOCX preview ในหน่วยความจำ (ไม่บันทึกไฟล์)
     * ใช้ template docx + แทนที่ placeholder แล้วส่ง DOCX bytes กลับ
     */
    public byte[] generatePreviewDocx(int documentType, String jsonData) throws IOException {
        Map<String, Object> dataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> placeholders = flattenMap(dataMap, "");

        String templateFile = TEMPLATE_DIR + "doc_" + documentType + ".docx";
        ClassPathResource resource = new ClassPathResource(templateFile);

        try (FileInputStream fis = new FileInputStream(resource.getFile());
                XWPFDocument document = new XWPFDocument(fis)) {

            replacePlaceholdersInParagraphs(document.getParagraphs(), placeholders);

            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        replacePlaceholdersInParagraphs(cell.getParagraphs(), placeholders);
                    }
                }
            }

            // เขียน XWPFDocument เป็น DOCX bytes ใน memory
            ByteArrayOutputStream docxOut = new ByteArrayOutputStream();
            document.write(docxOut);
            return docxOut.toByteArray();
        }
    }

    /**
     * สร้าง DOCX preview สำหรับ doc_4 (สำเนาเดียว สำหรับ preview)
     */
    public byte[] generatePreviewDocxForCopy(int documentType, String jsonData,
            String committeeName, String committeePosition) throws IOException {
        Map<String, Object> dataMap = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
        });
        dataMap.put("committee_name", committeeName);
        dataMap.put("committee_position", committeePosition);

        String modifiedJson = objectMapper.writeValueAsString(dataMap);
        return generatePreviewDocx(documentType, modifiedJson);
    }

    public byte[] getDocumentBytes(String filePath) throws IOException {
        return Files.readAllBytes(Path.of(filePath));
    }

    public File getDocumentFile(String filePath) {
        return new File(filePath);
    }

    private void replacePlaceholdersInParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> placeholders) {
        for (XWPFParagraph paragraph : paragraphs) {
            String fullText = paragraph.getText();
            if (fullText == null || !fullText.contains("{{"))
                continue;

            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                if (fullText.contains(placeholder)) {
                    fullText = fullText.replace(placeholder, entry.getValue() != null ? entry.getValue() : "");
                }
            }

            // Clear existing runs and set the replaced text
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs == null || runs.isEmpty())
                continue;

            // Preserve formatting from first run
            XWPFRun firstRun = runs.get(0);
            String fontFamily = firstRun.getFontFamily();
            int fontSize = firstRun.getFontSize();
            boolean isBold = firstRun.isBold();

            // Remove all runs
            for (int i = runs.size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }

            // Add new run with replaced text
            XWPFRun newRun = paragraph.createRun();
            newRun.setText(fullText);
            if (fontFamily != null)
                newRun.setFontFamily(fontFamily);
            if (fontSize > 0)
                newRun.setFontSize(fontSize);
            newRun.setBold(isBold);
        }
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
