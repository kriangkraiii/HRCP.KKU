package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ecom.support.AbstractFlowTest;

/**
 * ไฟล์ในโฟลเดอร์ uploads ต้องโหลดตรงผ่าน URL ไม่ได้
 *
 * <p>เดิม {@code spring.web.resources.static-locations} มี {@code file:uploads/} และ
 * {@code /static/**} เปิดให้ทุกคนโดยไม่ต้องล็อกอิน ส่วน {@code WebConfig} ก็เปิด
 * {@code /uploads/**} ให้ทุกคนที่ล็อกอิน ไฟล์ .p12 รูปลายเซ็น และเอกสารที่ลงนามแล้ว
 * จึงโหลดได้ถ้ารู้ชื่อไฟล์ เทสต์นี้วางไฟล์จริงลงในโฟลเดอร์ uploads ที่ config เดิมเสิร์ฟ
 * แล้วยืนยันว่าไม่มีทางไหนอ่านเนื้อไฟล์ออกมาได้
 *
 * <p>ตั้ง static-locations แบบเดิมไว้ในเทสต์โดยเจตนา: ไฟล์ properties ของ production ไม่อยู่ใน git
 * และอาจยังมีค่าเก่านี้อยู่ การป้องกันจึงต้องอยู่ในโค้ด ไม่ใช่ในไฟล์ตั้งค่า
 */
@TestPropertySource(properties = {
        "spring.web.resources.static-locations=classpath:/static/,file:uploads/",
        "spring.mvc.static-path-pattern=/static/**"
})
@DisplayName("โฟลเดอร์ uploads ไม่ถูกเสิร์ฟเป็นไฟล์ static")
class UploadRootNotServedTest extends AbstractFlowTest {

    private static final String SECRET = "leak-probe-" + UUID.randomUUID();

    private Path probe;

    @BeforeEach
    void placeProbe() throws Exception {
        // The old handlers resolved "file:uploads/" against the working directory.
        probe = Path.of("uploads", "certificates", "probe_" + UUID.randomUUID() + ".p12");
        Files.createDirectories(probe.getParent());
        Files.writeString(probe, SECRET, StandardCharsets.UTF_8);
    }

    @AfterEach
    void removeProbe() throws Exception {
        Files.deleteIfExists(probe);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/static/certificates/", "/uploads/certificates/" })
    void notReadableAnonymously(String prefix) throws Exception {
        assertNotServed(get(prefix + probe.getFileName()));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/static/certificates/", "/uploads/certificates/" })
    void notReadableWhenLoggedIn(String prefix) throws Exception {
        assertNotServed(get(prefix + probe.getFileName()).with(user("someone@kku.ac.th").roles("USER")));
    }

    private void assertNotServed(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("ไฟล์ใน uploads ถูกเสิร์ฟออกไป").isNotEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain(SECRET);
    }
}
