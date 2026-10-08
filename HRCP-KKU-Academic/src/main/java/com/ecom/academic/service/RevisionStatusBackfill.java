package com.ecom.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ตอนเริ่มระบบ: คำร้องเฟส 2 ที่ถูกส่งกลับให้แก้ก่อนการส่งกลับจะเปลี่ยนสถานะ ปรับเป็น "ส่งแก้ไข"
 * ({@link PositionRequestService#backfillRevisionRequested})
 *
 * <p>รันทุกครั้งที่เริ่มได้ เพราะไม่แตะคำร้องที่ถูกต้องอยู่แล้ว ล้มก็แค่ log — สถานะปรับเองจากหน้าแอดมินได้
 * การเริ่มระบบไม่ควรหยุดเพราะเรื่องนี้
 */
@Component
public class RevisionStatusBackfill {

    private static final Logger log = LoggerFactory.getLogger(RevisionStatusBackfill.class);

    private final PositionRequestService positionService;

    public RevisionStatusBackfill(PositionRequestService positionService) {
        this.positionService = positionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            int moved = positionService.backfillRevisionRequested();
            log.info("Revision status backfill: {} position request(s) moved to REVISION_REQUESTED", moved);
        } catch (Exception e) {
            log.warn("ปรับสถานะคำร้องที่มีเอกสารส่งกลับค้างอยู่ไม่สำเร็จ: {}", e.toString());
        }
    }
}
