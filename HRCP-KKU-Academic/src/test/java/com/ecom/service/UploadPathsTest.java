package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ที่อยู่ไฟล์อัปโหลดทั้งหมดอิง app.upload.dir ที่เดียว")
class UploadPathsTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("พาธในฐานข้อมูลที่ขึ้นต้น uploads/ ชี้ไปใต้ app.upload.dir แม้โฟลเดอร์จะไม่ได้ชื่อ uploads")
    void storedPathsResolveAgainstTheConfiguredRoot() {
        Path root = tempDir.resolve("storage");
        UploadPaths paths = new UploadPaths(root.toString());

        assertThat(paths.resolve("uploads/academic/5/doc_1.docx"))
                .isEqualTo(root.resolve("academic/5/doc_1.docx").toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("แถวที่บันทึกบน Windows ด้วย backslash ก็อ่านได้")
    void windowsSeparatorsAreAccepted() {
        UploadPaths paths = new UploadPaths(tempDir.toString());

        assertThat(paths.resolve("uploads\\position\\7\\attachments\\a.pdf"))
                .isEqualTo(tempDir.resolve("position/7/attachments/a.pdf").toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("บันทึกลงฐานข้อมูลในรูปแบบเดิม uploads/... ด้วย / เสมอ")
    void storedFormIsUnchanged() {
        UploadPaths paths = new UploadPaths(tempDir.toString());
        Path file = paths.dir("academic", "5").resolve("doc_1.docx");

        assertThat(paths.toStored(file)).isEqualTo("uploads/academic/5/doc_1.docx");
        assertThat(paths.resolve(paths.toStored(file))).isEqualTo(file);
    }

    @Test
    @DisplayName("พาธเต็มใช้ตามที่เป็น ส่วนพาธที่ปีนออกนอก root ถูกปฏิเสธ")
    void absoluteAndEscapingPaths() {
        UploadPaths paths = new UploadPaths(tempDir.resolve("root").toString());
        Path outside = tempDir.resolve("elsewhere/file.pdf").toAbsolutePath();

        assertThat(paths.resolve(outside.toString())).isEqualTo(outside);
        assertThat(paths.resolve("uploads/../../etc/passwd")).isNull();
        assertThat(paths.resolve("  ")).isNull();
        assertThat(paths.resolve(null)).isNull();
    }
}
