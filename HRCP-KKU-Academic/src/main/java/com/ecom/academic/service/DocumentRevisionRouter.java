package com.ecom.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.SignatureModule;

/**
 * เปิดเอกสารให้เจ้าของกลับมาแก้ เมื่อการเวียนลงนามถูกหยุดกลางทาง
 *
 * <p><b>ทำไมต้องมีคลาสนี้แทนที่จะเรียกตรง ๆ</b> — ปลายทางมีสองเส้น (เฟส 1 กับ เฟส 2) ซึ่งอยู่
 * คนละ service และทั้งคู่เป็นคลาสใหญ่ที่อ้าง {@link SignatureWorkflowService} กลับมาเอง การให้
 * workflow ฉีดสองตัวนั้นเข้าไปตรง ๆ จะเกิด circular bean dependency ตัวกลางเล็ก ๆ ตัวนี้จึงรับ
 * หน้าที่รู้ว่าโมดูลไหนไปที่ไหน และไม่รู้เรื่องอื่นเลย
 *
 * <p><b>ตีกลับตามเจ้าของเอกสาร</b> — เอกสารของแอดมิน (เช่น คำสั่งแต่งตั้งคณะอนุกรรมการ) ผู้ยื่น
 * มองไม่เห็นและแก้ไม่ได้ การเปิดสิทธิ์แก้ให้เขาจึงไม่มีความหมาย งานนั้นกลับไปที่แอดมินผ่าน
 * การแจ้งเตือนผู้เริ่มเวียนซึ่งมีอยู่แล้ว
 */
@Service
public class DocumentRevisionRouter {

    private static final Logger log = LoggerFactory.getLogger(DocumentRevisionRouter.class);

    private final AcademicRequestService academicRequestService;
    private final PositionRequestService positionRequestService;

    public DocumentRevisionRouter(@Lazy AcademicRequestService academicRequestService,
            @Lazy PositionRequestService positionRequestService) {
        this.academicRequestService = academicRequestService;
        this.positionRequestService = positionRequestService;
    }

    /**
     * @return true เมื่อเอกสารถูกเปิดให้ผู้ยื่นแก้จริง false เมื่อเป็นเอกสารของแอดมิน
     */
    public boolean openForRevision(SignatureModule module, Long requestId, int documentType,
            String note) {

        if (module == null || requestId == null) {
            return false;
        }
        if (!DocumentFieldOwnership.isApplicantOwned(module, documentType)) {
            log.info("Circulation stopped on an admin-owned document — "
                    + "module={} requestId={} documentType={}; leaving it with the initiator",
                    module, requestId, documentType);
            return false;
        }

        // การเปิดสิทธิ์แก้ไม่ควรทำให้การหยุดเวียนล้มเหลว: ถ้าตรงนี้พัง สิ่งที่เสียคือความสะดวก
        // แต่ถ้าปล่อยให้ exception ขึ้นไป ซองจะไม่ถูกปิดและผู้ลงนามจะค้างอยู่ในคิวต่อไป
        try {
            if (module == SignatureModule.ACADEMIC) {
                academicRequestService.openDocumentForRevision(requestId, documentType, note);
            } else {
                positionRequestService.openDocumentForRevision(requestId, documentType, note);
            }
            return true;
        } catch (Exception e) {
            log.error("Could not open document for revision — module={} requestId={} documentType={}: {}",
                    module, requestId, documentType, e.toString());
            return false;
        }
    }
}
