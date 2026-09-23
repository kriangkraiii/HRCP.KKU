package com.ecom.academic.service;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * เติมเลขที่หนังสือและวันที่เอกสารลงเอกสารที่ลงนามไปแล้ว ตอน render เท่านั้น
 *
 * <p>เอกสารที่เวียนลงนามจะถูกแช่แข็งไว้ที่ {@code frozenJson} และ
 * {@link SignedDocumentRenderer} สร้างไฟล์จากสำเนานั้นเสมอ ไม่ใช่จากแถวเอกสารปัจจุบัน
 * ซึ่งถูกต้องแล้วสำหรับเนื้อความ — คนที่เซ็นเป็นคนที่สองต้องเห็นสิ่งเดียวกับคนแรก
 *
 * <p>แต่งานสารบรรณเดินคนละจังหวะ: เลขที่หนังสือออกให้ <em>หลัง</em> เอกสารลงนามครบแล้ว
 * ถ้าไม่เติมทับตอน render เลขที่เจ้าหน้าที่กรอกจะบันทึกลงฐานข้อมูลสำเร็จแต่ไม่ปรากฏบนเอกสาร
 *
 * <p>ช่องอื่นของแอดมินในเอกสารของผู้ยื่นก็เดินจังหวะเดียวกัน ({@link DocumentFieldOwnership#lateFields})
 * — ผู้ยื่นลงนามก่อนส่งคำร้องเสมอ แอดมินจึงกรอกช่องของตัวเองได้หลังจากนั้นเท่านั้น
 *
 * <p>เดินเส้นทางเดียวกับ {@link SignerNameResolver} ทุกประการ — เติมลง JSON ที่กำลังจะ
 * ส่งเข้าเครื่องสร้างเอกสาร ไม่แตะ {@code frozenJson} ที่เก็บไว้และไม่แตะแฮชของมัน
 * หลักฐานลายเซ็นจึงยังตรวจสอบผ่านเหมือนเดิม
 *
 * <p>ต่างกันข้อเดียวแต่สำคัญ: ที่นี่ <em>เขียนทับ</em> ค่าเดิม ขณะที่ตัวเติมชื่อผู้ลงนาม
 * เติมเฉพาะตอนที่ค่าเดิมว่าง ถ้าลอกพฤติกรรมนั้นมาจะไม่ทำงานเลย เพราะซองมักถูกแช่แข็ง
 * ตอนที่ช่องเลขที่หนังสือมีค่าตั้งต้น ("อว 660301.26.3/") อยู่แล้ว
 */
@Component
public class OfficeFieldResolver {

    private static final Logger log = LoggerFactory.getLogger(OfficeFieldResolver.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AcademicRequestService academicService;
    private final PositionRequestService positionService;

    public OfficeFieldResolver(AcademicRequestService academicService,
            PositionRequestService positionService) {
        this.academicService = academicService;
        this.positionService = positionService;
    }

    /**
     * คืน JSON ที่เลขที่หนังสือและวันที่เป็นค่าปัจจุบันจากแถวเอกสาร
     *
     * @return JSON ที่เติมแล้ว หรือค่าเดิมเมื่อไม่มีอะไรต้องเติมหรืออ่านไม่ได้
     */
    public String fillInto(SignatureRequest envelope, String json) {
        if (envelope == null || json == null || json.isBlank()) {
            return json;
        }
        // เฉพาะซองที่ยังถือเอกสารอยู่ — ลงนามครบแล้ว หรือกำลังเวียนต่อ
        //
        // ซองที่กำลังเวียนต้องเติมด้วย: ผู้ยื่นเซ็นเสร็จ ซองเป็น COMPLETED แอดมินกรอกเลขที่
        // หนังสือหรือช่องของตัวเองแล้วส่งต่อ ซองกลับเป็น IN_PROGRESS ถ้าไม่เติมตรงนี้ ค่าที่เพิ่ง
        // เห็นจะหายไปจากทุกคนรวมทั้งผู้ลงนามคนถัดไปจนกว่าจะเซ็นครบ ระหว่างเวียนเซิร์ฟเวอร์ห้าม
        // เขียนอยู่แล้ว ค่าที่เติมจึงเป็นค่าก่อนส่งต่อ ซึ่งคือสิ่งที่ผู้ลงนามคนถัดไปเห็นและเซ็น
        //
        // ซองที่ถูกปฏิเสธหรือยกเลิกไม่เติม — เอกสารถูกปลดล็อกให้แก้ต่อ ค่าที่กรอกทีหลังจะไปโผล่
        // บนภาพของซองเก่า ซองที่เป็นหลักฐานของรอบที่ล้มเหลวต้องแสดงตามที่มันเป็นตอนนั้นจริง ๆ
        if (envelope.getStatus() == null || !envelope.getStatus().locksDocument()) {
            return json;
        }
        Set<String> keys = DocumentFieldOwnership.lateFields(
                envelope.getModule(), envelope.getDocumentType());
        if (keys.isEmpty()) {
            return json;
        }

        Map<String, String> current = latestDataFor(envelope);
        if (current == null || current.isEmpty()) {
            return json;
        }

        try {
            Map<String, Object> data = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });
            boolean changed = false;
            for (String key : keys) {
                String value = current.get(key);
                // ค่าว่างแปลว่ายังไม่ได้ออกเลข ไม่ใช่คำสั่งให้ลบของที่ลงนามไว้ทิ้ง
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (!value.equals(data.get(key))) {
                    data.put(key, value);
                    changed = true;
                }
            }
            return changed ? objectMapper.writeValueAsString(data) : json;
        } catch (Exception e) {
            // เลขที่หนังสือหายดีกว่าเอกสารเปิดไม่ขึ้น
            log.warn("Could not fill office fields into envelope {}: {}", envelope.getId(), e.toString());
            return json;
        }
    }

    private Map<String, String> latestDataFor(SignatureRequest envelope) {
        Long requestId = envelope.getRequestId();
        if (requestId == null) {
            return null;
        }
        return envelope.getModule() == SignatureModule.ACADEMIC
                ? academicService.getLatestDocumentData(requestId, envelope.getDocumentType())
                : positionService.getLatestDocumentData(requestId, envelope.getDocumentType());
    }
}
