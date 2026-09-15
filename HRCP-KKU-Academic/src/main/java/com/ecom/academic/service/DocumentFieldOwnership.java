package com.ecom.academic.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ecom.academic.model.SignatureModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ใครเขียนช่องไหนได้ — กติกาชุดเดียวที่ใช้ร่วมกันทั้งสองเฟส.
 *
 * <p>เอกสารส่วนใหญ่มีเจ้าของชัดเจนทั้งฉบับ แต่บางฉบับเป็นกระดาษแผ่นเดียวที่สองฝ่ายกรอกคนละส่วน
 * เช่น เอกสารที่ 2 ของเฟส 1 มีคอลัมน์ติ๊กของผู้ยื่นอยู่ข้างคอลัมน์ของเจ้าหน้าที่ การล็อกทั้งฉบับ
 * จึงใช้ไม่ได้ คลาสนี้เก็บ "รายชื่อช่องที่เป็นของแอดมินในเอกสารของผู้ยื่น" ไว้ที่เดียว แล้วให้
 * {@link #merge} เป็นคนบังคับใช้
 *
 * <p>เจตนาสำคัญคือ <em>เซิร์ฟเวอร์ต้องเป็นคนกัน</em> ไม่ใช่ {@code disabled} ใน HTML — ของเดิม
 * อาศัย {@code <input type="hidden">} ที่เบราว์เซอร์ส่งค่ากลับมาเอง ซึ่งปิด JS หรือยิง POST ตรง
 * ก็ทะลุได้ทั้งหมด
 *
 * <p>มีเจ้าของสามกลุ่ม ไม่ใช่สอง — ช่องที่เป็นของ <em>ผู้ลงนาม</em> (ผลการตรวจสอบคุณสมบัติใน
 * เอกสารที่ 5 ของเฟส 2) ไม่มีใครกรอกผ่านฟอร์มเอกสารได้เลย ดู {@link #signerFields}
 */
public final class DocumentFieldOwnership {

    private DocumentFieldOwnership() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** เอกสารที่ผู้ยื่นเป็นเจ้าของ นอกรายการนี้ถือเป็นของแอดมินทั้งหมด */
    private static final Map<SignatureModule, Set<Integer>> APPLICANT_DOCUMENTS = Map.of(
            SignatureModule.ACADEMIC, Set.of(1, 2),
            SignatureModule.POSITION, Set.of(1, 2, 3, 4, 5, 6, 9));

    /**
     * ช่องที่เป็นของแอดมิน <em>ภายในเอกสารของผู้ยื่น</em> เท่านั้น
     *
     * <p>เอกสารที่ไม่อยู่ในนี้แปลว่าแอดมินอ่านได้อย่างเดียวทั้งฉบับ ถ้าต้องแก้ให้ส่งกลับให้ผู้ยื่น
     * ส่วนเอกสารของแอดมินไม่ต้องมีรายการ เพราะแอดมินเป็นเจ้าของทุกช่องอยู่แล้ว
     */
    private static final Map<SignatureModule, Map<Integer, Set<String>>> ADMIN_FIELDS = Map.of(
            SignatureModule.ACADEMIC, Map.of(
                    // เลขที่หนังสือออกโดยสำนักงาน ผู้ยื่นไม่รู้เลขตอนกรอก (วันที่เอกสารเป็นของผู้ยื่น)
                    1, Set.of("memo_no"),
                    // คอลัมน์ "เจ้าหน้าที่" และหมายเหตุ คู่กับคอลัมน์ "เจ้าตัว" ที่ผู้ยื่นติ๊กเอง
                    2, Set.of("chk_off_1", "chk_off_2", "chk_off_3", "chk_off_4", "chk_off_5",
                            "text_1", "text_2", "text_3", "text_4", "text_5",
                            "hr_staff_name")),
            SignatureModule.POSITION, Map.of(
                    3, Set.of("dean_name", "dean_position", "verify_date"),
                    4, Set.of("memo_no", "department_head_name"),
                    6, Set.of("applicant_signature_name", "dean_signature_name", "position_title")));

    /** เอกสารฉบับนี้เป็นของผู้ยื่นหรือไม่ — ที่ไม่รู้จักถือว่าเป็นของแอดมิน ปลอดภัยไว้ก่อน */
    public static boolean isApplicantDocument(SignatureModule module, int documentType) {
        return APPLICANT_DOCUMENTS.getOrDefault(module, Set.of()).contains(documentType);
    }

    /** เอกสารที่ผู้ยื่นเป็นเจ้าของในเฟสนี้ เรียงจากน้อยไปมาก */
    public static List<Integer> applicantDocuments(SignatureModule module) {
        return APPLICANT_DOCUMENTS.getOrDefault(module, Set.of()).stream().sorted().toList();
    }

    /** ช่องที่แอดมินกรอกได้ในเอกสารของผู้ยื่น ว่างเปล่าแปลว่าอ่านอย่างเดียวทั้งฉบับ */
    public static Set<String> adminFields(SignatureModule module, int documentType) {
        if (!isApplicantDocument(module, documentType)) {
            return Set.of();
        }
        return ADMIN_FIELDS.getOrDefault(module, Map.of())
                .getOrDefault(documentType, Set.of());
    }

    /**
     * ช่องที่เป็นของผู้ลงนาม ไม่ใช่ของทั้งแอดมินและผู้ยื่น
     *
     * <p>อ่านจาก {@link SignatureAnchorRegistry} โดยตรง ไม่ตั้งรายการซ้ำ — เพิ่มคำถามให้ผู้ลงนาม
     * ที่ทะเบียนนั้นที่เดียว แล้วการกันช่องตรงนี้ตามเองอัตโนมัติ
     */
    public static Set<String> signerFields(SignatureModule module, int documentType) {
        Set<String> keys = new HashSet<>();
        for (SignatureAnchorRegistry.SignatureSlot slot :
                SignatureAnchorRegistry.slotsOf(module, documentType)) {
            if (slot.choice() != null) {
                keys.add(slot.choice().fieldKey());
            }
        }
        return keys;
    }

    /**
     * รวมสิ่งที่ส่งมากับสิ่งที่เก็บไว้ โดยรับเฉพาะช่องที่ผู้บันทึกเป็นเจ้าของ
     *
     * <p>ช่องที่ผู้บันทึกไม่ได้เป็นเจ้าของจะถูกทิ้งค่าที่ส่งมา แล้วคืนค่าเดิมจาก {@code existing}
     * — ทั้งการแก้และการลบด้วยค่าว่างจึงไม่มีผล ส่วนช่องของผู้ลงนามถูกตัดทิ้งเสมอไม่ว่าใครส่งมา
     *
     * @param actorIsAdmin true เมื่อผู้บันทึกเป็นแอดมิน/เจ้าหน้าที่
     * @param submitted    ค่าที่ส่งมาจากฟอร์ม (ไม่ถูกแก้ไข)
     * @param existing     ค่าที่เก็บอยู่ อาจเป็น null เมื่อยังไม่เคยบันทึก
     * @return map ชุดใหม่ที่พร้อมเขียนลง {@code json_data}
     */
    public static Map<String, String> merge(SignatureModule module, int documentType,
            boolean actorIsAdmin, Map<String, String> submitted, Map<String, String> existing) {

        Map<String, String> result = new LinkedHashMap<>();
        if (existing != null) {
            result.putAll(existing);
        }

        if (!isApplicantDocument(module, documentType)) {
            // เอกสารของแอดมิน: แอดมินเป็นเจ้าของทุกช่อง ผู้ยื่นไม่เป็นเจ้าของสักช่อง
            if (actorIsAdmin && submitted != null) {
                result.putAll(submitted);
            }
        } else if (submitted != null) {
            Set<String> adminOwned = adminFields(module, documentType);
            for (Map.Entry<String, String> entry : submitted.entrySet()) {
                boolean ownedByActor = actorIsAdmin == adminOwned.contains(entry.getKey());
                if (ownedByActor) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        signerFields(module, documentType).forEach(result::remove);
        return result;
    }

    /**
     * รูปแบบ JSON ของ {@link #merge} สำหรับ auto-draft ที่รับ request body ดิบ
     *
     * @return JSON ที่กรองแล้ว หรือ <strong>null</strong> เมื่อ payload ว่างหรือ parse ไม่ได้ —
     *         ผู้เรียกต้องปฏิเสธการบันทึก ไม่ใช่บันทึกของเดิมต่อ เพราะ payload ที่กรองไม่ได้
     *         คือ payload ที่ยังไม่ผ่านการตรวจ
     */
    public static String mergeJson(SignatureModule module, int documentType,
            boolean actorIsAdmin, String jsonData, Map<String, String> existing) {
        if (jsonData == null || jsonData.isBlank()) {
            return null;
        }
        try {
            Map<String, String> submitted = MAPPER.readValue(jsonData,
                    new TypeReference<Map<String, String>>() {
                    });
            return MAPPER.writeValueAsString(
                    merge(module, documentType, actorIsAdmin, submitted, existing));
        } catch (Exception e) {
            return null;
        }
    }
}
