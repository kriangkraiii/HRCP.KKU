package com.ecom.academic.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.PositionDocumentRepository;

/**
 * Background Asynchronous Document Pre-generation & Cache Warming Service
 *
 * เมื่อผู้ใช้บันทึกฟอร์มหรือเปิดหน้ารายละเอียดคำร้อง ระบบจะสั่งให้สร้าง DOCX + แปลงเป็น PDF
 * เก็บลง Two-Tier Cache (Memory + Disk) ล่วงหน้าใน background thread (low priority)
 * ทำให้เมื่อผู้ใช้กด "ดูตัวอย่าง" จะได้ผลลัพธ์ Instant (< 50ms) โดยไม่ต้องรอ
 */
@Service
public class DocumentPrewarmService {

    private static final Logger log = LoggerFactory.getLogger(DocumentPrewarmService.class);

    private final DocumentGenerationService documentGenerationService;
    private final AcademicDocumentRepository academicDocumentRepository;
    private final PositionDocumentRepository positionDocumentRepository;
    private final TeachingEvaluationPartResolver teachingEvaluationPart;
    private final java.util.concurrent.Executor executor;

    /** รุ่นล่าสุดของแต่ละเอกสาร (requestId:type) ที่ขอให้ prewarm — งานที่ตกรุ่นแล้วข้ามไป */
    private final java.util.Map<String, Long> latestPositionVersion = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong versions = new java.util.concurrent.atomic.AtomicLong();

    public DocumentPrewarmService(DocumentGenerationService documentGenerationService,
            AcademicDocumentRepository academicDocumentRepository,
            PositionDocumentRepository positionDocumentRepository,
            @org.springframework.context.annotation.Lazy TeachingEvaluationPartResolver teachingEvaluationPart,
            @org.springframework.beans.factory.annotation.Qualifier("docPrewarmExecutor")
            java.util.concurrent.Executor executor) {
        this.documentGenerationService = documentGenerationService;
        this.academicDocumentRepository = academicDocumentRepository;
        this.positionDocumentRepository = positionDocumentRepository;
        this.teachingEvaluationPart = teachingEvaluationPart;
        this.executor = executor;
    }

    /**
     * ข้อมูลเอกสารเฟส 2 แบบเดียวกับที่หน้าดูตัวอย่างและดาวน์โหลดใช้ (เติมส่วนที่ ๓ จากผลประเมินการสอน)
     * — ไฟล์ .docx ต้องตรงกันทุกไบต์ แคช PDF จึงจะใช้ซ้ำได้
     */
    private String positionPreviewJson(Long requestId, int documentType, String jsonData) {
        return teachingEvaluationPart.fillInto(requestId, documentType, jsonData);
    }

    /**
     * แปลง PDF ล่วงหน้าหลังบันทึก (รวมบันทึกอัตโนมัติ) — พอผู้ใช้กดดูตัวอย่าง PDF มักพร้อมแล้ว
     *
     * <p>บันทึกอัตโนมัติเกิดถี่ เอกสารเดียวกันจึงแปลงเฉพาะรุ่นล่าสุด งานของรุ่นก่อนที่ยังรอคิวอยู่ข้ามไป
     */
    public void schedulePositionPrewarm(Long requestId, int documentType, String jsonData) {
        if (requestId == null || jsonData == null || jsonData.isBlank()) {
            return;
        }
        String key = requestId + ":" + documentType;
        long version = versions.incrementAndGet();
        latestPositionVersion.put(key, version);
        executor.execute(() -> {
            if (!Long.valueOf(version).equals(latestPositionVersion.get(key))) {
                return;
            }
            prewarmPositionNow(requestId, documentType, jsonData);
            latestPositionVersion.remove(key, version);
        });
    }

    private void prewarmPositionNow(Long requestId, int documentType, String jsonData) {
        if (!documentGenerationService.isPdfConversionAvailable()) {
            return;
        }
        try {
            long start = System.currentTimeMillis();
            byte[] docx = documentGenerationService.generateP2PreviewDocx(documentType,
                    positionPreviewJson(requestId, documentType, jsonData));
            documentGenerationService.convertDocxToPdfCached(docx);
            log.debug("Prewarmed Position Doc {} for request {} in {} ms", documentType, requestId,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.debug("Background prewarm for position doc {} skipped/failed: {}", documentType, e.getMessage());
        }
    }

    /**
     * Pre-warm เอกสาร Phase 1 (Academic Request) 1 รายการ
     */
    @Async("docPrewarmExecutor")
    public void prewarmAcademicDocument(Long requestId, int documentType, String jsonData) {
        if (jsonData == null || jsonData.isBlank() || !documentGenerationService.isPdfConversionAvailable()) {
            return;
        }
        try {
            long start = System.currentTimeMillis();
            byte[] docx = documentGenerationService.generatePreviewDocx(documentType, jsonData);
            documentGenerationService.convertDocxToPdfCached(docx);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("Prewarmed Academic Doc {} for request {} in {} ms", documentType, requestId, elapsed);
        } catch (Exception e) {
            log.debug("Background prewarm for academic doc {} skipped/failed: {}", documentType, e.getMessage());
        }
    }

    /**
     * Pre-warm เอกสาร Phase 1 พร้อมสำเนากรรมการ (Doc 4)
     */
    @Async("docPrewarmExecutor")
    public void prewarmAcademicDocumentForCopy(Long requestId, int documentType, String jsonData,
            String committeeName, String committeePosition) {
        if (jsonData == null || jsonData.isBlank() || !documentGenerationService.isPdfConversionAvailable()) {
            return;
        }
        try {
            long start = System.currentTimeMillis();
            byte[] docx = documentGenerationService.generatePreviewDocxForCopy(documentType, jsonData,
                    committeeName, committeePosition);
            documentGenerationService.convertDocxToPdfCached(docx);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("Prewarmed Academic Copy Doc {} for request {} in {} ms", documentType, requestId, elapsed);
        } catch (Exception e) {
            log.debug("Background prewarm for academic copy doc {} skipped/failed: {}", documentType, e.getMessage());
        }
    }

    /**
     * Pre-warm เอกสาร Phase 2 (Position Request) 1 รายการ
     */
    public void prewarmPositionDocument(Long requestId, int documentType, String jsonData) {
        schedulePositionPrewarm(requestId, documentType, jsonData);
    }

    /**
     * Pre-warm เอกสารทั้งหมดในคำร้อง (เมื่อเข้าหน้ารายละเอียดคำร้อง request_detail)
     */
    @Async("docPrewarmExecutor")
    public void prewarmAllRequestDocuments(Long requestId, boolean isPosition) {
        if (requestId == null || !documentGenerationService.isPdfConversionAvailable()) {
            return;
        }
        try {
            if (isPosition) {
                List<PositionDocument> docs = positionDocumentRepository.findByRequestId(requestId);
                for (PositionDocument doc : docs) {
                    if (Boolean.TRUE.equals(doc.getIsDeleted())) continue;
                    String json = doc.getJsonData();
                    if (json != null && !json.isBlank()) {
                        try {
                            byte[] docx = documentGenerationService.generateP2PreviewDocx(doc.getDocumentType(),
                                    positionPreviewJson(requestId, doc.getDocumentType(), json));
                            documentGenerationService.convertDocxToPdfCached(docx);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } else {
                List<AcademicDocument> docs = academicDocumentRepository.findByRequestId(requestId);
                for (AcademicDocument doc : docs) {
                    String json = doc.getJsonData();
                    if (json != null && !json.isBlank()) {
                        try {
                            byte[] docx = documentGenerationService.generatePreviewDocx(doc.getDocumentType(), json);
                            documentGenerationService.convertDocxToPdfCached(docx);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Prewarm all documents for request {} finished with note: {}", requestId, e.getMessage());
        }
    }
}
