package com.ecom.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.repository.SignatureRequestRepository;

/**
 * เลื่อนสถานะคำร้องเมื่อซองลายเซ็นปิด
 *
 * <p>เดิมสถานะเลื่อนตอนเจ้าหน้าที่กดบันทึกเอกสาร ซึ่งเร็วกว่าความจริงหนึ่งขั้น — ตอนนั้น
 * หนังสือยังเป็นแค่ร่างที่ยังไม่มีใครลงนามและยังไม่ได้ส่งออกไปไหน ผู้ยื่นจึงเห็นว่าคำร้อง
 * เดินไปแล้วทั้งที่ยังไม่มีอะไรเกิดขึ้นจริง ขั้นตอนจะนับว่าจบเมื่อหนังสือลงนามครบเท่านั้น
 *
 * <p>เรียกจาก {@link SignatureWorkflowService} หลัง commit เสมอ ด้วยเหตุผลเดียวกับที่
 * {@link SignedDocumentArchiver} ทำ: ถ้าการเลื่อนสถานะล้ม ลายเซ็นที่ลงไปแล้วต้องไม่ถูก
 * ย้อนกลับตามไปด้วย ลายเซ็นเรียกคืนไม่ได้ ส่วนสถานะปรับเองทีหลังได้จากหน้าผู้ดูแลระบบ
 *
 * <p>ทิศทางการพึ่งพาสำคัญ: workflow → ตัวนี้ → เซอร์วิสของแต่ละเฟส ห้ามย้อนกลับ เพราะ
 * {@code SignatureWorkflowService} พึ่งเซอร์วิสทั้งสองเฟสอยู่แล้วผ่าน
 * {@link DocumentSnapshotProvider} — ถ้าฝั่งนั้นเรียกกลับมาจะกลายเป็นวง
 */
@Service
public class SignedDocumentStatusAdvancer {

    private static final Logger log = LoggerFactory.getLogger(SignedDocumentStatusAdvancer.class);

    private final SignatureRequestRepository requestRepository;
    private final AcademicRequestService academicService;
    private final PositionRequestService positionService;

    public SignedDocumentStatusAdvancer(SignatureRequestRepository requestRepository,
            AcademicRequestService academicService,
            PositionRequestService positionService) {
        this.requestRepository = requestRepository;
        this.academicService = academicService;
        this.positionService = positionService;
    }

    /**
     * เลื่อนสถานะของคำร้องที่เป็นเจ้าของซองนี้ ถ้าเอกสารฉบับนี้เป็นฉบับที่เลื่อนขั้น
     *
     * <p>ยิงซ้ำได้โดยไม่เสียหาย — รอบลงนามรอบสอง (หลัง "ขอให้เซ็นใหม่") หรือการส่งต่อ
     * ที่ทำให้ซองเดิมปิดอีกครั้ง จะไม่เลื่อนสถานะซ้ำ เพราะด่าน {@code canMoveTo} ใน
     * {@code autoUpdateStatusByDocument} ปฏิเสธการย้ายที่ขั้นปัจจุบันไปไม่ได้อยู่แล้ว
     *
     * <p>{@code REQUIRES_NEW} ไม่ใช่ของประดับ — ตัวนี้ถูกเรียกจาก {@code afterCommit} ของ
     * ทรานแซกชันการลงนาม ตอนนั้นทรานแซกชันเดิม commit ไปแล้วแต่ยังผูกอยู่กับเธรด ถ้าใช้
     * {@code REQUIRED} ตามปกติ การเขียนจะไปเข้าร่วมทรานแซกชันที่ปิดไปแล้ว แล้ว<em>หายเงียบ</em>
     * ไม่มี exception ให้เห็น — สถานะไม่ขยับและไม่มีอะไรบอกว่าเพราะอะไร
     *
     * @param envelopeId ซองที่เพิ่งปิด — โหลดใหม่ที่นี่เพราะ entity ของผู้เรียกอยู่ใน
     *                   ทรานแซกชันที่จบไปแล้ว
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advanceFor(Long envelopeId) {
        SignatureRequest envelope = requestRepository.findById(envelopeId).orElse(null);
        if (envelope == null) {
            log.warn("ซอง {} หายไปก่อนที่จะเลื่อนสถานะคำร้องได้", envelopeId);
            return;
        }

        Long requestId = envelope.getRequestId();
        int documentType = envelope.getDocumentType();

        // ใช้ข้อมูลที่ถูกแช่แข็งไว้ ไม่ใช่แถวเอกสารปัจจุบัน — สิ่งที่คนลงนามให้คือฉบับนั้น
        // และเงื่อนไขการเลื่อนขั้นบางข้ออ่านจากเนื้อเอกสาร (ชื่ออนุกรรมการของเอกสารที่ 4
        // ผลประเมินของเอกสารที่ 7) จึงต้องตัดสินจากสิ่งเดียวกับที่ลงนามไป
        String signedJson = envelope.getFrozenJson();

        // แจ้งผู้ยื่นเสมอ: สถานะเปลี่ยนเพราะหนังสือลงนามครบแล้วจริง ไม่ใช่เพราะใครกดปุ่ม
        // จึงไม่มีจังหวะไหนที่เจ้าหน้าที่จะมาเลือกว่าจะบอกหรือไม่บอก
        try {
            if (envelope.getModule() == SignatureModule.ACADEMIC) {
                academicService.autoUpdateStatusByDocument(requestId, documentType,
                        envelope.getInitiatedBy(), signedJson, true);
            } else {
                positionService.autoUpdateStatusByDocument(requestId, documentType,
                        envelope.getInitiatedBy(), signedJson, true);
            }
        } catch (Exception e) {
            log.warn("เลื่อนสถานะคำร้อง #{} หลังปิดซองเอกสารที่ {} ไม่สำเร็จ: {}",
                    requestId, documentType, e.toString());
        }
    }
}
