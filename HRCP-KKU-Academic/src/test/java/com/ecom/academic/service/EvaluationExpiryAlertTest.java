package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.model.UserDtls;

@DisplayName("แจ้งเตือนผลประเมินใกล้หมดอายุ — ครั้งเดียวต่อรายการ ไม่ใช่ทุกวัน")
class EvaluationExpiryAlertTest {

    private static UserDtls everyStandardAlertOn() {
        UserDtls user = new UserDtls();
        user.setExpiryAlert6m(true);
        user.setExpiryAlert3m(true);
        user.setExpiryAlert1m(true);
        user.setExpiryAlert1w(true);
        return user;
    }

    @Test
    @DisplayName("เปิดทั้งสี่รายการ ตลอด 200 วันก่อนหมดอายุได้แจ้งเตือนสี่วันพอดี")
    void eachStandardAlertFiresOnItsOwnDayOnly() {
        UserDtls user = everyStandardAlertOn();

        long[] alertDays = LongStream.rangeClosed(-5, 200)
                .filter(days -> EvaluationExpiryScheduler.alertLabelFor(user, days) != null)
                .toArray();

        // งานรันทุกวัน — เดิมเทียบเป็นช่วง จึงได้แจ้งเตือน 181 วันติดกัน
        assertThat(alertDays).containsExactly(7, 30, 90, 180);
        assertThat(EvaluationExpiryScheduler.alertLabelFor(user, 180)).isEqualTo("6 เดือน");
        assertThat(EvaluationExpiryScheduler.alertLabelFor(user, 7)).isEqualTo("1 สัปดาห์");
    }

    @Test
    @DisplayName("ปิดรายการไว้ไม่แจ้ง และการแจ้งเตือนที่ตั้งเองยังแจ้งตรงวัน")
    void disabledAndCustomAlerts() {
        UserDtls user = new UserDtls();
        user.setExpiryAlert1w(false);
        user.setCustomExpiryAlerts("[{\"id\":\"a\",\"value\":2,\"unit\":\"WEEK\",\"days\":14,"
                + "\"label\":\"2 สัปดาห์\",\"enabled\":true}]");

        assertThat(EvaluationExpiryScheduler.alertLabelFor(user, 7)).isNull();
        assertThat(EvaluationExpiryScheduler.alertLabelFor(user, 14)).isEqualTo("2 สัปดาห์");
        assertThat(EvaluationExpiryScheduler.alertLabelFor(user, 13)).isNull();
    }
}
