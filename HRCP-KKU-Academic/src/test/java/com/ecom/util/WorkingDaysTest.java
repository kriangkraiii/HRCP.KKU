package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("วันทำการ (จันทร์–ศุกร์) — นับวันแรกเป็นวันถัดจากวันเริ่ม")
class WorkingDaysTest {

    @Test
    @DisplayName("รับเรื่องวันศุกร์ วันทำการที่ 3 คือวันพุธ")
    void skipsTheWeekend() {
        LocalDate friday = LocalDate.of(2026, 10, 9);
        assertThat(WorkingDays.plus(friday, 3)).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    @DisplayName("เริ่มวันเสาร์ วันทำการแรกคือวันจันทร์")
    void startingOnAWeekendCountsFromMonday() {
        assertThat(WorkingDays.plus(LocalDate.of(2026, 10, 10), 1)).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    @DisplayName("45 วันทำการ = 9 สัปดาห์พอดี")
    void fortyFiveWorkingDaysIsNineWeeks() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        assertThat(WorkingDays.plus(monday, 45)).isEqualTo(monday.plusWeeks(9));
    }

    @Test
    @DisplayName("ไม่มีวันเริ่ม — ไม่มีวันครบกำหนด")
    void nullStartGivesNull() {
        assertThat(WorkingDays.plus(null, 3)).isNull();
    }
}
