package com.ecom.academic.service.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ค่าที่เจ้าหน้าที่กรอกทีหลัง (หมายเหตุ เลขที่หนังสือ ฯลฯ) ใส่ลงช่องที่จองไว้ในไฟล์ลงนามได้หรือไม่
 *
 * <p>ช่องพวกนี้มีขนาดตายตัวตั้งแต่สร้างไฟล์ตั้งต้น ({@code field_layout_json} ของซอง) ถ้าไม่ตรวจก่อน
 * ค่าที่ยาวเกินจะไปล้มตอนคนถัดไปลงนาม — คนที่แก้ช่องนั้นไม่ได้ (ซอง 71 วันที่ 30 ก.ย. 2569:
 * หมายเหตุ "ไม่เห็นเอกสาร" ของเจ้าหน้าที่ ทำให้ผู้ยื่นลงนามไม่ได้) ใช้กติกาเดียวกับตอนเขียนลงไฟล์จริง
 * ({@link PdfIncrementService#fits})
 */
public final class LateFieldFit {

    /** A value that does not fit its box. */
    public record Problem(String field, String label, String value) {
    }

    private static final PdfIncrementService PDF = new PdfIncrementService(ThaiText.sarabun());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern REMARK = Pattern.compile("^text_(\\d+)$");

    private LateFieldFit() {
    }

    /**
     * @param layoutJson the envelope's {@code field_layout_json}; null or blank checks nothing
     * @param values     field → value; fields the layout has no box for are skipped
     * @return the values that do not fit, in layout order
     */
    public static List<Problem> check(String layoutJson, Map<String, String> values) {
        List<Problem> problems = new ArrayList<>();
        if (layoutJson == null || layoutJson.isBlank() || values == null || values.isEmpty()) {
            return problems;
        }
        JsonNode texts;
        try {
            texts = JSON.readTree(layoutJson).path("texts");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return problems;
        }
        for (JsonNode box : texts) {
            String field = box.path("field").asText();
            String value = values.get(field);
            if (value == null || value.isBlank()) {
                continue;
            }
            value = value.trim();
            if (!PDF.fits(value, (float) box.path("fontSize").asDouble(PdfIncrementService.TEXT_SIZE),
                    (float) box.path("width").asDouble(), box.path("lines").asInt(1))) {
                problems.add(new Problem(field, labelOf(field), value));
            }
        }
        return problems;
    }

    /** ชื่อช่องที่ผู้ใช้รู้จัก ไม่ใช่ชื่อตัวแปร */
    public static String labelOf(String field) {
        Matcher remark = REMARK.matcher(field);
        if (remark.matches()) {
            return "หมายเหตุข้อ " + remark.group(1);
        }
        return switch (field) {
            case "memo_no" -> "เลขที่หนังสือ";
            case "date" -> "วันที่เอกสาร";
            case "verify_date" -> "วันที่ตรวจสอบ";
            case "position_title" -> "ตำแหน่ง";
            default -> field.endsWith("_name") ? "ชื่อ" : field.contains("comment") ? "ความเห็น" : field;
        };
    }

    /** ข้อความบอกเจ้าหน้าที่ว่าช่องไหนต้องย่อ */
    public static String message(List<Problem> problems) {
        StringBuilder names = new StringBuilder();
        for (Problem p : problems) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append('“').append(p.label()).append(" — ").append(p.value()).append('”');
        }
        return "ข้อความยาวเกินช่องในเอกสาร: " + names + " กรุณาย่อให้สั้นลงแล้วลองอีกครั้ง";
    }
}
