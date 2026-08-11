package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers M-07 from the 2026-08-11 audit.
 *
 * The activity-log CSV export only doubled quote characters. Fields such as
 * `details` carry the email string typed at a failed login, so an attacker
 * could get a spreadsheet formula into the file an admin later opens in Excel.
 */
class CsvExportEscapingTest {

    @Test
    @DisplayName("M-07: ค่าที่ขึ้นต้นด้วย = ต้องไม่ถูก Excel ตีความเป็นสูตร")
    void neutralisesEqualsPrefix() {
        String escaped = CsvExportUtils.escapeCsv("=HYPERLINK(\"http://evil.com\",\"click\")");

        assertThat(escaped).doesNotStartWith("=");
    }

    @Test
    @DisplayName("M-07: ค่าที่ขึ้นต้นด้วย + - @ tab CR ก็ต้องถูกทำให้ปลอดภัยเช่นกัน")
    void neutralisesAllFormulaTriggers() {
        assertThat(CsvExportUtils.escapeCsv("+1+1")).doesNotStartWith("+");
        assertThat(CsvExportUtils.escapeCsv("-1+1")).doesNotStartWith("-");
        assertThat(CsvExportUtils.escapeCsv("@SUM(A1)")).doesNotStartWith("@");
        assertThat(CsvExportUtils.escapeCsv("\tcmd")).doesNotStartWith("\t");
    }

    @Test
    @DisplayName("M-07: ยังต้อง escape double quote ตามรูปแบบ CSV เดิม")
    void stillDoublesQuotes() {
        assertThat(CsvExportUtils.escapeCsv("say \"hi\"")).isEqualTo("say \"\"hi\"\"");
    }

    @Test
    @DisplayName("M-07: ข้อความปกติต้องไม่ถูกดัดแปลง")
    void leavesOrdinaryTextAlone() {
        assertThat(CsvExportUtils.escapeCsv("แก้ไขบัญชีผู้ใช้ ID:123")).isEqualTo("แก้ไขบัญชีผู้ใช้ ID:123");
        assertThat(CsvExportUtils.escapeCsv("user@test.com")).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("M-07: null ต้องกลายเป็นค่าว่าง")
    void mapsNullToEmpty() {
        assertThat(CsvExportUtils.escapeCsv(null)).isEmpty();
    }
}
