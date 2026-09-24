package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.UserSignature;
import com.ecom.service.SignatureImageStorage;
import com.ecom.support.AbstractFlowTest;

/**
 * รูปลายเซ็นที่สร้างบนเครื่องหนึ่ง ต้องเปิดได้จากอีกเครื่องที่ใช้ฐานข้อมูลเดียวกัน
 */
@DisplayName("รูปลายเซ็นมีสำเนาในฐานข้อมูล เครื่องที่ไม่มีไฟล์ก็เปิดได้")
class SignatureImageFromAnotherMachineTest extends AbstractFlowTest {

    @Autowired
    private SignatureImageStorage storage;

    @Test
    @DisplayName("ไฟล์รูปลายเซ็นไม่อยู่บนดิสก์เครื่องนี้ — อ่านจากฐานข้อมูลได้และเขียนไฟล์กลับให้")
    void aSignatureMadeElsewhereIsReadFromTheDatabase() throws Exception {
        UserSignature signature = data.signatureFor(data.applicant());
        byte[] original = storage.read(signature.getImagePath());
        Files.delete(storage.getBaseDir().resolve(signature.getImagePath()));

        assertThat(storage.read(signature.getImagePath())).isEqualTo(original);
        assertThat(storage.getBaseDir().resolve(signature.getImagePath())).isRegularFile();
    }

    @Test
    @DisplayName("ลบรูปลายเซ็น — สำเนาในฐานข้อมูลถูกลบด้วย ไม่ฟื้นกลับมาเอง")
    void deletingRemovesTheDatabaseCopyToo() throws Exception {
        UserSignature signature = data.signatureFor(data.applicant());

        storage.deleteIfPresent(signature.getImagePath());

        assertThat(storage.read(signature.getImagePath())).isNull();
    }
}
