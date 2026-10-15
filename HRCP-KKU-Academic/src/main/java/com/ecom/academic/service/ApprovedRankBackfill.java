package com.ecom.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ตอนเริ่มระบบ: คำร้องที่สภามหาวิทยาลัยอนุมัติไปก่อนที่ระบบจะอัปเดตตำแหน่งให้ตอนอนุมัติ — ปรับตำแหน่งในบัญชีให้ตรง
 * ({@link PositionRequestService#backfillApprovedRanks})
 *
 * <p>รันทุกครั้งที่เริ่มได้ เพราะไม่แตะบัญชีที่ตำแหน่งถึงแล้ว ล้มก็แค่ log การเริ่มระบบไม่ควรหยุดเพราะเรื่องนี้
 */
@Component
public class ApprovedRankBackfill {

    private static final Logger log = LoggerFactory.getLogger(ApprovedRankBackfill.class);

    private final PositionRequestService positionService;

    public ApprovedRankBackfill(PositionRequestService positionService) {
        this.positionService = positionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            int changed = positionService.backfillApprovedRanks();
            log.info("Approved rank backfill: {} account(s) updated to the rank the council approved", changed);
        } catch (Exception e) {
            log.warn("ปรับตำแหน่งตามมติสภามหาวิทยาลัยไม่สำเร็จ: {}", e.toString());
        }
    }
}
