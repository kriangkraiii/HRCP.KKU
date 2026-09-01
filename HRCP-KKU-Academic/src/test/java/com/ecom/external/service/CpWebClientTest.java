package com.ecom.external.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ecom.external.config.CpWebProperties;
import com.ecom.external.service.CpWebClient.CpPerson;

class CpWebClientTest {

    @TempDir
    Path tempDir;

    private CpPerson samplePerson() {
        return new CpPerson(
                "somchai@kku.ac.th",
                "สมชาย",
                "ใจดี",
                "ผู้ช่วยศาสตราจารย์ดร.",
                "ผศ.ดร.",
                "Somchai",
                "Jaidee",
                "Assistant Professor Dr.",
                "Asst. Prof. Dr.",
                "หลักสูตรวิทยาการคอมพิวเตอร์",
                "/uploads/somchai.jpg",
                "somchai-jaidee",
                true);
    }

    @Test
    @DisplayName("เมื่อบันทึก snapshot และโหลดกลับมา ต้องได้ข้อมูลที่ตรงกัน 100%")
    void saveAndLoadSnapshotReturnsEqualData() {
        CpWebProperties props = new CpWebProperties();
        Path snapshotPath = tempDir.resolve("test_snapshot.json");
        props.setSnapshotFilePath(snapshotPath.toString());

        CpWebClient client = new CpWebClient(props);
        List<CpPerson> expected = List.of(samplePerson());

        client.saveSnapshot(expected);
        assertThat(Files.exists(snapshotPath)).isTrue();

        List<CpPerson> loaded = client.loadSnapshot();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).email()).isEqualTo("somchai@kku.ac.th");
        assertThat(loaded.get(0).firstName()).isEqualTo("สมชาย");
        assertThat(loaded.get(0).academicRankShort()).isEqualTo("ผศ.ดร.");
        assertThat(loaded.get(0).firstNameEn()).isEqualTo("Somchai");
    }

    @Test
    @DisplayName("เมื่อ Live API ใช้ไม่ได้ (Base URL ปลอม) และมี snapshot อยู่ ระบบต้องโหลด fallback cache สำเร็จ")
    void fallbackCacheLoadsWhenLiveApiIsUnreachable() {
        CpWebProperties props = new CpWebProperties();
        // Point to an invalid local port that will fail connection immediately
        props.setBaseUrl("http://127.0.0.1:54321");
        props.setConnectTimeoutSeconds(1);
        props.setReadTimeoutSeconds(1);
        props.setFallbackCacheEnabled(true);
        props.setMaxRetryAttempts(0);

        Path snapshotPath = tempDir.resolve("cached_people.json");
        props.setSnapshotFilePath(snapshotPath.toString());

        CpWebClient client = new CpWebClient(props);
        client.saveSnapshot(List.of(samplePerson()));

        // fetchAll should fail live API gracefully and return cached snapshot
        List<CpPerson> result = client.fetchAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("somchai@kku.ac.th");

        // และต้องบอกผู้เรียกได้ว่าข้อมูลชุดนี้มาจากแคช ไม่ใช่ของสด
        CpWebClient.DirectoryFetch fetch = client.fetchAllWithFallback();
        assertThat(fetch.usedFallbackCache()).isTrue();
        assertThat(fetch.people()).hasSize(1);
    }

    @Test
    @DisplayName("การอ่านสำเร็จจาก API จริง ต้องไม่ถูกทำเครื่องหมายว่าเป็นข้อมูลสำรอง และต้องบันทึก snapshot ไว้")
    void aLiveReadIsNotFlaggedAsFallbackAndIsSnapshotted() throws Exception {
        String body = """
                {"data":{"pager":{"totalPages":1},"items":[
                  {"email":"somchai@kku.ac.th","slug":"somchai-jaidee","isActived":true,
                   "image":{"url":"/uploads/somchai.jpg"},
                   "userLocalized":[{"languageId":1,"firstname":"สมชาย","lastname":"ใจดี"}]}
                ]}}
                """;

        try (StubDirectory stub = StubDirectory.serving(body)) {
            CpWebProperties props = new CpWebProperties();
            props.setBaseUrl(stub.baseUrl());
            props.setSnapshotFilePath(tempDir.resolve("live_snapshot.json").toString());

            CpWebClient.DirectoryFetch fetch = new CpWebClient(props).fetchAllWithFallback();

            assertThat(fetch.usedFallbackCache()).isFalse();
            assertThat(fetch.people()).hasSize(1);
            assertThat(fetch.people().get(0).firstName()).isEqualTo("สมชาย");
            // A successful read is what refills the cache the next outage will read.
            assertThat(Files.exists(tempDir.resolve("live_snapshot.json"))).isTrue();
        }
    }

    @Test
    @DisplayName("เมื่อการเชื่อมต่อล้มเหลว ต้องลองใหม่ตามจำนวนที่ตั้งไว้ (maxRetryAttempts) แล้วจึงยอมแพ้")
    void aTimedOutRequestIsRetriedAsManyTimesAsConfigured() throws Exception {
        try (StubDirectory stub = StubDirectory.ignoringEveryRequest()) {
            CpWebProperties props = new CpWebProperties();
            props.setBaseUrl(stub.baseUrl());
            props.setConnectTimeoutSeconds(1);
            props.setReadTimeoutSeconds(1);
            props.setMaxRetryAttempts(2);
            props.setSnapshotFilePath(tempDir.resolve("unused.json").toString());

            List<CpPerson> result = new CpWebClient(props).fetchAll();

            assertThat(result).isEmpty();
            // ครั้งแรก + ลองใหม่อีก 2 = 3 ครั้ง
            assertThat(stub.connectionCount()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("ตั้ง maxRetryAttempts เป็น 0 ต้องยิงเพียงครั้งเดียว")
    void noRetriesMeansASingleAttempt() throws Exception {
        try (StubDirectory stub = StubDirectory.ignoringEveryRequest()) {
            CpWebProperties props = new CpWebProperties();
            props.setBaseUrl(stub.baseUrl());
            props.setConnectTimeoutSeconds(1);
            props.setReadTimeoutSeconds(1);
            props.setMaxRetryAttempts(0);
            props.setSnapshotFilePath(tempDir.resolve("unused.json").toString());

            new CpWebClient(props).fetchAll();

            assertThat(stub.connectionCount()).isEqualTo(1);
        }
    }

    /**
     * A throwaway HTTP server on a loopback port.
     *
     * <p>Pointing the client at a port nothing listens on proves it survives a
     * refused connection, but it cannot show how many times it tried. This one
     * counts, so the retry setting is tested by what actually reaches the wire
     * rather than by trusting the code that reads it.
     */
    private static final class StubDirectory implements AutoCloseable {

        private final ServerSocket socket;
        private final Thread accepting;
        private final AtomicInteger connections = new AtomicInteger();
        private final List<Socket> held = Collections.synchronizedList(new ArrayList<>());

        private StubDirectory(String response) throws IOException {
            this.socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            this.accepting = new Thread(() -> acceptLoop(response), "stub-directory");
            this.accepting.setDaemon(true);
            this.accepting.start();
        }

        static StubDirectory serving(String jsonBody) throws IOException {
            return new StubDirectory(jsonBody);
        }

        /**
         * Accepts, counts, and then never answers, so each attempt ends in a read
         * timeout.
         *
         * <p>Hanging up instead would be the obvious way to fail a request, but
         * {@code HttpURLConnection} quietly re-sends a GET whose connection died,
         * so every logical attempt would arrive twice and the count would say
         * nothing about the retry setting under test. A read timeout it does not
         * retry, so what reaches the socket is exactly what the client decided.
         */
        static StubDirectory ignoringEveryRequest() throws IOException {
            return new StubDirectory(null);
        }

        private void acceptLoop(String response) {
            while (!socket.isClosed()) {
                if (response == null) {
                    try {
                        // Held, not closed: an open socket nobody answers is what
                        // makes the client wait out its read timeout. They are all
                        // released in close().
                        held.add(socket.accept());
                        connections.incrementAndGet();
                    } catch (IOException e) {
                        return;
                    }
                    continue;
                }
                try (Socket client = socket.accept()) {
                    connections.incrementAndGet();
                    byte[] payload = response.getBytes(StandardCharsets.UTF_8);
                    String head = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json; charset=utf-8\r\n"
                            + "Content-Length: " + payload.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    OutputStream out = client.getOutputStream();
                    out.write(head.getBytes(StandardCharsets.US_ASCII));
                    out.write(payload);
                    out.flush();
                } catch (IOException e) {
                    return; // the socket was closed, or the client went away
                }
            }
        }

        String baseUrl() {
            return "http://" + socket.getInetAddress().getHostAddress() + ":" + socket.getLocalPort();
        }

        int connectionCount() {
            return connections.get();
        }

        @Override
        public void close() throws IOException {
            socket.close();
            synchronized (held) {
                for (Socket s : held) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // closing a stub socket on the way out is not a test failure
                    }
                }
            }
            accepting.interrupt();
        }
    }

    @Test
    @DisplayName("เมื่อ Live API ใช้ไม่ได้ และไม่มี Snapshot ต้องคืน List ว่างโดยไม่โยน Exception")
    void returnsEmptyListWhenLiveFailsAndNoCache() {
        CpWebProperties props = new CpWebProperties();
        props.setBaseUrl("http://127.0.0.1:54321");
        props.setConnectTimeoutSeconds(1);
        props.setReadTimeoutSeconds(1);
        props.setFallbackCacheEnabled(true);
        props.setMaxRetryAttempts(0);

        Path nonExistent = tempDir.resolve("non_existent.json");
        props.setSnapshotFilePath(nonExistent.toString());

        CpWebClient client = new CpWebClient(props);
        List<CpPerson> result = client.fetchAll();
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("การดาวน์โหลดภาพเมื่อเกิด Connection error ต้องคืน Optional.empty() อย่างนุ่มนวล")
    void fetchImageReturnsEmptyOnConnectionFailure() {
        CpWebProperties props = new CpWebProperties();
        props.setConnectTimeoutSeconds(1);
        props.setReadTimeoutSeconds(1);

        CpWebClient client = new CpWebClient(props);
        Optional<byte[]> imageBytes = client.fetchImage("http://127.0.0.1:54321/invalid-image.jpg");
        assertThat(imageBytes).isEmpty();
    }
}
