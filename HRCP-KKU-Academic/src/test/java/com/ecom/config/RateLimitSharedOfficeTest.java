package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.ecom.support.NoRealMailConfig;
import com.ecom.support.TestDataFactory;

/**
 * เพดานคำขอเมื่อทั้งสำนักงานออกเน็ตด้วย IP เดียวกัน
 *
 * <p>เครือข่ายของมหาวิทยาลัยอยู่หลัง NAT — ทุกคนในวิทยาลัยจึงเป็น IP เดียวกันในสายตาเซิร์ฟเวอร์
 * เดิมเพดาน 200 ครั้ง/นาทีนับรวมตาม IP อย่างเดียว คนหนึ่งเปิดเอกสารยาว ๆ (บันทึกร่างอัตโนมัติ
 * ยิงทุกครั้งที่พิมพ์) ก็กินโควตาของทั้งตึก แล้วคณบดีที่กำลังลงนามได้หน้า 429 ไป
 *
 * <p>เทสนี้รันบนพอร์ตจริงผ่าน HTTP จริง เพราะต้องเห็นทั้ง filter chain ที่ Tomcat ใช้จริง —
 * รวมถึงการที่ filter ตัวเดียวกันถูกลงทะเบียนซ้ำสองที่แล้วนับคำขอเดียวเป็นสองครั้ง
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ NoRealMailConfig.class, TestDataFactory.class })
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ratelimitdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "fs.api.enabled=false",
        "fs.sync.on-startup=false",
        "cp.web.enabled=false",
        "cp.sync.on-startup=false",
        "app.alert.email.enabled=false",
        "app.user.email=__no_universal_access__@example.invalid",
        "app.rate-limit.general=20",
        "app.rate-limit.login=1000",
        // เหมือนเครื่องจริง: เชื่อ X-Forwarded-For จาก reverse proxy ในเครื่อง
        "server.forward-headers-strategy=native"
})
@DisplayName("เพดานคำขอ: คนในสำนักงานเดียวกัน (IP เดียวกัน) ต้องไม่แย่งโควตากัน")
class RateLimitSharedOfficeTest {

    private static final int LIMIT = 20;

    @LocalServerPort
    private int port;

    @Autowired
    private TestDataFactory data;

    /**
     * IP ขาออกของ "สำนักงาน" ในเทสนี้ — เทสละ IP เพื่อไม่ให้โควตาข้ามเทสกัน
     *
     * <p>ส่งผ่าน X-Forwarded-For แบบที่ reverse proxy หน้าเครื่องจริงส่ง
     * ({@code server.forward-headers-strategy=native} เชื่อ header นี้จาก localhost)
     */
    private String officeIp;
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_IP =
            new java.util.concurrent.atomic.AtomicInteger(10);

    @BeforeEach
    void people() {
        officeIp = "203.0.113." + NEXT_IP.getAndIncrement();
        data.reset();
        data.applicant();
    }

    @Test
    @DisplayName("ผู้ใช้หนึ่งคนใช้ครบเพดานพอดีต้องไม่โดนบล็อก — คำขอเดียวต้องนับครั้งเดียว")
    void oneRequestCountsOnce() throws Exception {
        Browser somchai = signedIn(TestDataFactory.APPLICANT_EMAIL);
        // แดชบอร์ดที่เปิดตอนเข้าระบบนับเป็นคำขอแรกแล้ว
        for (int i = 2; i <= LIMIT; i++) {
            assertThat(somchai.get("/user/academic/dashboard"))
                    .as("คำขอที่ %d จาก %d ต้องผ่าน", i, LIMIT)
                    .isNotEqualTo(429);
        }
        assertThat(somchai.get("/user/academic/dashboard"))
                .as("เกินเพดานแล้วต้องถูกจำกัดตามเดิม")
                .isEqualTo(429);
    }

    @Test
    @DisplayName("เพื่อนร่วมงานใช้โควตาหมด อีกคนที่ IP เดียวกันยังใช้งานได้")
    void colleaguesBehindOneNatDoNotShareAQuota() throws Exception {
        // บัญชีของเทสนี้เอง — โควตานับตามบัญชีและอยู่ได้หนึ่งนาที ใช้บัญชีร่วมกับเทสอื่นจะติดโควตาข้ามเทส
        String somchaiEmail = "busy@" + TestDataFactory.DOMAIN;
        String maleeEmail = "quiet@" + TestDataFactory.DOMAIN;
        data.user(somchaiEmail, "สมชาย", "งานเยอะ", "ROLE_USER");
        data.user(maleeEmail, "มาลี", "เงียบ ๆ", "ROLE_USER");
        Browser somchai = signedIn(somchaiEmail);
        Browser malee = signedIn(maleeEmail);

        for (int i = 0; i < LIMIT + 5; i++) {
            somchai.get("/user/academic/dashboard");
        }
        assertThat(somchai.get("/user/academic/dashboard")).as("สมชายเกินเพดานของตัวเองแล้ว").isEqualTo(429);

        assertThat(malee.get("/user/academic/dashboard"))
                .as("มาลีไม่ได้ยิงอะไรเลย ต้องไม่โดนหางเลขจากโควตาของสมชาย")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("ผู้ที่ยังไม่เข้าระบบยังถูกจำกัดตาม IP เหมือนเดิม")
    void anonymousTrafficIsStillLimitedByIp() throws Exception {
        Browser stranger = new Browser();
        int last = 0;
        for (int i = 0; i <= LIMIT; i++) {
            last = stranger.get("/signin");
        }
        assertThat(last).isEqualTo(429);
    }

    // ------------------------------------------------------------------

    /**
     * ผู้ใช้หนึ่งคนหนึ่งเบราว์เซอร์ — เก็บคุกกี้เอง เพราะคุกกี้ CSRF ของแอปตั้ง Secure ไว้
     * และ CookieManager ของ Java ไม่ส่งคุกกี้ Secure ผ่าน http://localhost ต่างจากเบราว์เซอร์จริง
     */
    private final class Browser {
        private final HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        private final java.util.Map<String, String> cookies = new java.util.LinkedHashMap<>();

        <T> HttpResponse<T> send(HttpRequest.Builder req, HttpResponse.BodyHandler<T> body) throws Exception {
            req.header("X-Forwarded-For", officeIp);
            if (!cookies.isEmpty()) {
                req.header("Cookie", cookies.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(java.util.stream.Collectors.joining("; ")));
            }
            HttpResponse<T> res = http.send(req.build(), body);
            for (String set : res.headers().allValues("Set-Cookie")) {
                String pair = set.split(";", 2)[0];
                int eq = pair.indexOf('=');
                cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
            return res;
        }

        int get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET(), HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        }
    }

    private Browser signedIn(String email) throws Exception {
        Browser b = new Browser();
        HttpResponse<String> signin = b.send(HttpRequest.newBuilder(uri("/signin")).GET(),
                HttpResponse.BodyHandlers.ofString());
        String page = signin.body();
        Matcher m = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*>").matcher(page);
        assertThat(m.find()).as("หน้าเข้าสู่ระบบต้องมี CSRF token (HTTP %d)", signin.statusCode()).isTrue();
        Matcher v = Pattern.compile("value=\"([^\"]+)\"").matcher(m.group());
        assertThat(v.find()).isTrue();
        String form = "email=" + enc(email) + "&password=" + enc(TestDataFactory.PASSWORD)
                + "&_csrf=" + enc(v.group(1));
        HttpResponse<Void> login = b.send(HttpRequest.newBuilder(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)), HttpResponse.BodyHandlers.discarding());
        assertThat(b.get("/user/academic/dashboard"))
                .as("เข้าสู่ระบบแล้วต้องเปิดแดชบอร์ดได้ (login → %s)",
                        login.headers().firstValue("Location").orElse("-"))
                .isEqualTo(200);
        return b;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
