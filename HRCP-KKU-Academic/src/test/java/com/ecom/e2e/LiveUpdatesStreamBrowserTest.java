package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;
import com.zaxxer.hikari.HikariDataSource;

/**
 * อัปเดตสดของหน้าคำร้องเจ้าหน้าที่ผ่านการเชื่อมต่อ HTTP จริง (ผ่านทุก filter)
 *
 * <p>MockMvc ไม่เห็นสองเรื่องที่พังของจริง: filter ที่ห่อ response ไว้ก่อนส่ง (เหตุการณ์ไม่ถึงเบราว์เซอร์
 * และ Content-Length 12 ไบต์ทำให้ EventSource ต่อใหม่ทุกสองวินาที) และ open-in-view ที่ถือ DB connection
 * ไว้ตลอดอายุการเชื่อมต่อ — แท็บเดียวที่เปิดค้างทำให้ pool หมดทั้งระบบภายในนาทีเดียว
 */
@DisplayName("E2E: อัปเดตสด — เหตุการณ์ถึงเบราว์เซอร์ และหน้าที่เปิดค้างไม่ถือ DB connection")
class LiveUpdatesStreamBrowserTest extends PlaywrightTestBase {

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private PositionRequestService positionService;

    private int activeConnections() {
        try {
            return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private String signedInCookie() {
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        return browserContext.cookies().stream().map(c -> c.name + "=" + c.value)
                .collect(Collectors.joining("; "));
    }

    /** อ่านทีละบรรทัดใน thread แยก — บรรทัดที่ได้ไปรอใน queue */
    private static BlockingQueue<String> lines(HttpResponse<java.io.InputStream> response) {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    queue.add(line);
                }
                queue.add("<eof>");
            } catch (java.io.IOException e) {
                queue.add("<closed>");
            }
        });
        reader.setDaemon(true);
        reader.start();
        return queue;
    }

    private static boolean awaitLine(BlockingQueue<String> queue, String expected, long seconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            String line = queue.poll(200, TimeUnit.MILLISECONDS);
            if (line != null && line.contains(expected)) {
                return true;
            }
            if ("<eof>".equals(line) || "<closed>".equals(line)) {
                return false;
            }
        }
        return false;
    }

    @Test
    @DisplayName("สตรีมไม่มี Content-Length ส่งเหตุการณ์ changed ได้ และเปิดห้าหน้าไม่หยิบ DB connection")
    void streamDeliversEventsWithoutHoldingConnections() throws Exception {
        toleratingBrowserErrors();
        UserDtls officer = data.admin();
        PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.DOCUMENT_RECEIVED,
                null, "ผู้ช่วยศาสตราจารย์");
        String cookie = signedInCookie();
        Thread.sleep(500);
        int before = activeConnections();

        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        URI live = URI.create(baseUrl() + "/admin/position/request/" + request.getId() + "/live");
        List<HttpResponse<java.io.InputStream>> streams = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            streams.add(http.sendAsync(HttpRequest.newBuilder(live).header("Cookie", cookie)
                    .header("Accept", "text/event-stream").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream()).get(10, TimeUnit.SECONDS));
        }
        try {
            HttpResponse<java.io.InputStream> first = streams.get(0);
            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(first.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
            assertThat(first.headers().firstValue("content-length"))
                    .as("สตรีมที่มี Content-Length จบทันทีที่ส่ง ': connected' — เบราว์เซอร์ต่อใหม่วนไป")
                    .isEmpty();

            BlockingQueue<String> events = lines(first);
            assertThat(awaitLine(events, "connected", 5)).as("ต้องได้ ': connected' ทันทีที่ต่อ").isTrue();

            Thread.sleep(1000);
            assertThat(activeConnections())
                    .as("หน้าคำร้องที่เปิดค้างห้าหน้า ต้องไม่ถือ DB connection ไว้")
                    .isLessThanOrEqualTo(before);

            positionService.updateStatus(request.getId(), PositionRequestStatus.DOCUMENT_VERIFICATION, officer,
                    "ตรวจสอบเอกสาร", false);
            assertThat(awaitLine(events, "event:changed", 10))
                    .as("สถานะเปลี่ยนแล้ว หน้าที่เปิดอยู่ต้องได้เหตุการณ์ changed")
                    .isTrue();
        } finally {
            for (var s : streams) {
                s.body().close();
            }
        }
    }

    @Test
    @DisplayName("หน้าคำร้องที่เปิดค้างในเบราว์เซอร์ต่อ /live ครั้งเดียว ไม่วนต่อใหม่")
    void anOpenPageConnectsOnce() {
        data.admin();
        PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.DOCUMENT_RECEIVED,
                null, "ผู้ช่วยศาสตราจารย์");
        signIn(TestDataFactory.ADMIN_EMAIL, TestDataFactory.PASSWORD);
        AtomicInteger connects = new AtomicInteger();
        page.onRequest(r -> {
            if (r.url().endsWith("/live")) {
                connects.incrementAndGet();
            }
        });
        page.navigate(baseUrl() + "/admin/position/request/" + request.getId());
        page.waitForTimeout(7000);
        assertThat(connects.get()).as("EventSource ต้องค้างการเชื่อมต่อเดียว ไม่ต่อใหม่ทุกสองวินาที").isEqualTo(1);

        // ออกจากหน้านี้ได้ตามปกติ
        page.navigate(baseUrl() + "/admin/position/requests");
        assertThat(page.url()).endsWith("/admin/position/requests");
    }
}
