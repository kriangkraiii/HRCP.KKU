package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.AcademicDocument;

public interface AcademicDocumentRepository extends JpaRepository<AcademicDocument, Long> {

    List<AcademicDocument> findByRequestId(Long requestId);

    Optional<AcademicDocument> findByRequestIdAndDocumentTypeAndCopyNumber(Long requestId, Integer documentType,
            Integer copyNumber);

    /**
     * ทุกฉบับของเอกสารประเภทหนึ่ง เรียงตามลำดับที่กรอก
     *
     * <p>เอกสารที่ 5 ออกเป็นสามฉบับ ฉบับละกรรมการหนึ่งท่าน และลำดับมีความหมาย —
     * สำเนาที่ 1 คือประธานอนุกรรมการ ที่ 3 คืออนุกรรมการและเลขานุการ เดิมเมธอดนี้ไม่มี
     * {@code ORDER BY} เลย ลำดับจึงขึ้นกับฐานข้อมูล และผู้เรียกที่หยิบ {@code get(0)}
     * กับที่หยิบแถวท้ายได้คนละฉบับกันโดยไม่มีใครรู้
     */
    List<AcademicDocument> findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(Long requestId, Integer documentType);

    List<AcademicDocument> findByRequestIdOrderByDocumentTypeAscCopyNumberAsc(Long requestId);

    // File manager queries
    List<AcademicDocument> findByIsDeletedFalseOrIsDeletedIsNull();

    List<AcademicDocument> findByIsDeletedTrue();

    /** The most recently generated file, for the startup storage check. */
    Optional<AcademicDocument> findFirstByGeneratedFilePathIsNotNullOrderByIdDesc();
}
