package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers M-09 from the 2026-08-11 audit.
 *
 * Download endpoints built the Content-Disposition header by concatenating a
 * stored filename straight into a quoted string. Filenames originate from the
 * upload request, so a quote character broke out of the quoted-string and let
 * the caller dictate the rest of the header.
 */
class ContentDispositionTest {

    @Test
    @DisplayName("M-09: เครื่องหมาย \" ในชื่อไฟล์ต้องไม่ทำให้ header เพี้ยน")
    void quoteInFilenameCannotBreakOutOfTheHeader() {
        String header = FileUtils.contentDisposition("evil\".pdf");

        long quoteCount = header.chars().filter(c -> c == '"').count();
        assertThat(quoteCount)
                .as("only the two delimiters of the quoted-string may remain")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("M-09: CR/LF ในชื่อไฟล์ต้องถูกตัดออก")
    void stripsCarriageReturnsAndLineFeeds() {
        String header = FileUtils.contentDisposition("a\r\nSet-Cookie: x=1.pdf");

        assertThat(header).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    @DisplayName("M-09: ชื่อไฟล์ภาษาไทยต้องยังดาวน์โหลดได้ผ่าน filename*")
    void encodesThaiFilenameWithRfc5987() {
        String header = FileUtils.contentDisposition("เอกสาร.pdf");

        assertThat(header).contains("filename*=UTF-8''");
        assertThat(header).contains("%E0%B9%80"); // percent-encoded Thai
    }

    @Test
    @DisplayName("M-09: ชื่อไฟล์ปกติต้องยังอ่านออกใน header")
    void keepsPlainAsciiFilenameReadable() {
        String header = FileUtils.contentDisposition("report.pdf");

        assertThat(header).contains("filename=\"report.pdf\"");
    }

    @Test
    @DisplayName("M-09: ชื่อไฟล์ว่างต้องมีค่า fallback")
    void fallsBackWhenFilenameMissing() {
        assertThat(FileUtils.contentDisposition(null)).contains("filename=");
    }
}
