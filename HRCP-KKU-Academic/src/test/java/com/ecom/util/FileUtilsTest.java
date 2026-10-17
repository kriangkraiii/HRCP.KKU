package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ชื่อไฟล์ที่ผู้ใช้ส่งมาแปลก ๆ ต้องได้ชื่อที่ปลอดภัย ไม่ใช่ 500 */
class FileUtilsTest {

    @Test
    void aRootPathGetsARandomName() {
        assertThat(FileUtils.sanitizeFilename("/")).isNotBlank().doesNotContain("/");
    }

    @Test
    void aNulCharacterIsDroppedNotFatal() {
        assertThat(FileUtils.sanitizeFilename("report\0.pdf")).endsWith("_report.pdf");
    }

    @Test
    void directoriesAreStripped() {
        assertThat(FileUtils.sanitizeFilename("../../etc/passwd")).endsWith("_passwd").doesNotContain("/");
    }
}
