package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Covers H-03 from the 2026-08-11 audit.
 *
 * ClientIpUtils trusted X-Forwarded-For unconditionally. Because the value is
 * the key for BruteForceProtection, an attacker could send a different header
 * on every login attempt and never accumulate failures.
 *
 * Proxy headers must be handled by Tomcat via
 * server.forward-headers-strategy=native, which validates them against the
 * trusted proxy configuration and rewrites getRemoteAddr(). Application code
 * must therefore read getRemoteAddr() only.
 */
class ClientIpUtilsTest {

    @Test
    @DisplayName("H-03: ต้องไม่เชื่อ X-Forwarded-For ที่ client ส่งมาเอง")
    void doesNotTrustClientSuppliedForwardedForHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(ClientIpUtils.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("H-03: ต้องไม่เชื่อ X-Real-IP ที่ client ส่งมาเอง")
    void doesNotTrustClientSuppliedRealIpHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Real-IP", "9.9.9.9");

        assertThat(ClientIpUtils.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("H-03: ผู้โจมตีสลับ X-Forwarded-For ต้องยังได้ key เดิมทุกครั้ง")
    void rotatingForwardedForHeadersYieldsTheSameKey() {
        String first = resolveWithForwardedFor("10.0.0.1");
        String second = resolveWithForwardedFor("10.0.0.2");
        String third = resolveWithForwardedFor("10.0.0.3");

        assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    private String resolveWithForwardedFor(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return ClientIpUtils.resolveClientIp(request);
    }

    @Test
    @DisplayName("ไม่มี header ใด ๆ ก็ยังคืน remote address ตามปกติ")
    void fallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.55");

        assertThat(ClientIpUtils.resolveClientIp(request)).isEqualTo("192.0.2.55");
    }
}
