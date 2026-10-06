package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("งานแปลงล่วงหน้าใช้ LibreOffice ได้ไม่เกินจำนวน process − 1 — เหลือว่างให้ผู้ใช้อย่างน้อย 1 ตัว")
class PrewarmConversionLimitTest {

    @ParameterizedTest(name = "{0} process → แปลงเบื้องหลังพร้อมกันได้ {1} งาน")
    @CsvSource({ "1, 1", "2, 1", "3, 2", "4, 3" })
    void leavesOneProcessForUsers(int processes, int limit) {
        DocumentPrewarmService service = new DocumentPrewarmService(null, null, null, null, Runnable::run, processes);
        assertThat(service.backgroundConversionLimit()).isEqualTo(limit);
    }
}
