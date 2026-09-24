package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.UserSignature;
import com.ecom.support.AbstractFlowTest;

@DisplayName("ไฟล์ลายเซ็นที่มีอยู่ก่อนแล้ว ถูกคัดลอกเข้าฐานข้อมูลตอนแอปเริ่ม")
class StoredBlobBackfillTest extends AbstractFlowTest {

    @Autowired
    private SignatureImageStorage storage;

    @Autowired
    private BlobMirror mirror;

    @Autowired
    private com.ecom.academic.service.DigitalCertificateStorage certificateStorage;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @DisplayName("ไฟล์ที่มีแถวชี้อยู่แต่ยังไม่มีสำเนา ถูกคัดลอกเข้า — เครื่องอื่นจึงอ่านได้")
    void filesARowPointsAtAreCopied() throws Exception {
        UserSignature signature = data.signatureFor(data.applicant());
        byte[] original = storage.read(signature.getImagePath());
        // As if stored by a build from before the database copy existed.
        mirror.delete(BlobMirror.SIGNATURE, signature.getImagePath());

        new StoredBlobBackfill(mirror, storage, certificateStorage, jdbc, true).backfill();

        Files.delete(storage.getBaseDir().resolve(signature.getImagePath()));
        assertThat(storage.read(signature.getImagePath())).isEqualTo(original);
    }
}
