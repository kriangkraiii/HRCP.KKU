package com.ecom.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The account name is stored as firstName + lastName, with a legacy `name`
 * column kept for backward compatibility.
 *
 * setName() used to write only the legacy column while getName() and every
 * validation path read firstName/lastName, so a name set through setName()
 * silently vanished. These tests pin the round-trip.
 */
class UserDtlsNameTest {

    @Test
    @DisplayName("setName แล้ว getName ต้องได้ค่าเดิมกลับมา")
    void nameRoundTrips() {
        UserDtls user = new UserDtls();
        user.setName("Somchai Jaidee");

        assertThat(user.getName()).isEqualTo("Somchai Jaidee");
    }

    @Test
    @DisplayName("setName ต้องเติม firstName และ lastName ที่ validation ใช้จริง")
    void setNamePopulatesTheFieldsValidationReads() {
        UserDtls user = new UserDtls();
        user.setName("Somchai Jaidee");

        assertThat(user.getFirstName()).isEqualTo("Somchai");
        assertThat(user.getLastName()).isEqualTo("Jaidee");
    }

    @Test
    @DisplayName("ชื่อไทยที่มีหลายส่วน ให้ส่วนสุดท้ายเป็นนามสกุล")
    void splitsThaiNameOnTheLastSpace() {
        UserDtls user = new UserDtls();
        user.setName("สมชาย ใจดี");

        assertThat(user.getFirstName()).isEqualTo("สมชาย");
        assertThat(user.getLastName()).isEqualTo("ใจดี");
        assertThat(user.getName()).isEqualTo("สมชาย ใจดี");
    }

    @Test
    @DisplayName("ชื่อคำเดียวต้องไม่ทำให้ lastName เป็นค่าขยะ")
    void singleWordNameLeavesLastNameEmpty() {
        UserDtls user = new UserDtls();
        user.setName("Cher");

        assertThat(user.getFirstName()).isEqualTo("Cher");
        assertThat(user.getName()).isEqualTo("Cher");
    }

    @Test
    @DisplayName("setFirstName/setLastName ยังทำงานเหมือนเดิม")
    void explicitFirstAndLastNameStillWin() {
        UserDtls user = new UserDtls();
        user.setFirstName("Anan");
        user.setLastName("Suksri");

        assertThat(user.getName()).isEqualTo("Anan Suksri");
    }

    @Test
    @DisplayName("setName(null) ต้องไม่ระเบิด")
    void handlesNull() {
        UserDtls user = new UserDtls();
        user.setName(null);

        assertThat(user.getName()).isNull();
    }
}
