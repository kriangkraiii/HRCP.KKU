package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("AuthModeProperties.isDevMode — เครื่องมือ dev เปิดเฉพาะ app.auth.mode=dev")
class AuthModePropertiesDevModeTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "dev, true",
            "' DEV ', true",
            "production, false",
            "sso, false",
            // ค่าที่ไม่รู้จักต้องไม่เปิดเครื่องมือ dev แม้ isPasswordLoginEnabled จะยังเป็นจริง
            "staging, false"
    })
    void onlyTheDevModeCounts(String mode, boolean expected) {
        assertThat(new AuthModeProperties(mode).isDevMode()).isEqualTo(expected);
    }
}
