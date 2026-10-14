package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.model.UserDtls;

@DisplayName("KkuEmailDomains: แยกบุคลากร มข. ต่างคณะ ออกจากผู้ลงนามนอก มข. ด้วยโดเมนอีเมล")
class KkuEmailDomainsTest {

    private final KkuEmailDomains kku = new KkuEmailDomains("kku.ac.th, @KKUMail.com");

    @Test
    @DisplayName("โดเมนของ KKU SSO รวม subdomain นับเป็น มข. — โดเมนที่แค่หน้าตาคล้ายไม่นับ")
    void recognisesKkuAddresses() {
        assertThat(kku.isKku("a@kku.ac.th")).isTrue();
        assertThat(kku.isKku("A@KKU.AC.TH")).isTrue();
        assertThat(kku.isKku("a@en.kku.ac.th")).isTrue();
        assertThat(kku.isKku("a@kkumail.com")).isTrue();

        assertThat(kku.isKku("a@notkku.ac.th")).isFalse();
        assertThat(kku.isKku("a@kku.ac.th.evil.com")).isFalse();
        assertThat(kku.isKku("a@gmail.com")).isFalse();
        assertThat(kku.isKku("kku.ac.th")).isFalse();
        assertThat(kku.isKku(null)).isFalse();
    }

    @Test
    @DisplayName("นอก มข. = ROLE_EXTERNAL ที่อีเมลไม่ใช่โดเมน มข. — ผู้ใช้ทั่วไปไม่นับ แม้อีเมลจะเป็นของที่อื่น")
    void outsideKkuMeansAnExternalAccountWithANonKkuAddress() {
        assertThat(kku.isOutsideKku(user("wipa@cmu.ac.th", UserDtls.ROLE_EXTERNAL))).isTrue();
        assertThat(kku.isOutsideKku(user("wipa@kku.ac.th", UserDtls.ROLE_EXTERNAL))).isFalse();
        assertThat(kku.isOutsideKku(user("wipa@kkumail.com", UserDtls.ROLE_EXTERNAL))).isFalse();
        assertThat(kku.isOutsideKku(user("someone@gmail.com", "ROLE_USER"))).isFalse();
        assertThat(kku.isOutsideKku(user("admin@gmail.com", "ROLE_ADMIN"))).isFalse();
        assertThat(kku.isOutsideKku(null)).isFalse();
    }

    private static UserDtls user(String email, String role) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setRole(role);
        return u;
    }
}
