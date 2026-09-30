package com.ecom.academic.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.ExistingProcessAction;
import org.jodconverter.local.office.LocalOfficeManager;
import org.jodconverter.local.office.LocalOfficeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * LibreOffice ที่เปิดค้างไว้ สำหรับแปลง DOCX → PDF
 *
 * <p>เดิมทุกการแปลงเปิด {@code soffice} ใหม่หนึ่ง process แล้วปิดทิ้ง ค่าเปิดโปรแกรมอย่างเดียว
 * ก็ราว 0.7–2 วินาทีต่อครั้ง ขณะที่ตัวการแปลงจริงใช้ไม่ถึง 0.1 วินาที การส่งเอกสารไปลงนาม
 * ต้องแปลงหลายรอบ (หาตำแหน่งช่องลงนามจาก PDF ที่แปลงได้ ถ้าไม่เจอก็ย่อแล้วแปลงใหม่)
 * การลงนามแต่ละครั้งก็แปลงอีก หน้าบันทึกและส่งลงนามจึงช้าเกือบทั้งหมดเพราะค่าเปิดโปรแกรมนี้
 *
 * <p>ที่นี่เปิด LibreOffice ค้างไว้ผ่าน JODConverter แล้วส่งงานเข้าไปทาง UNO ผลลัพธ์เป็น
 * ตัวกรอง {@code writer_pdf_Export} ตัวเดียวกับ {@code --convert-to pdf} ฟอนต์ไทยใส่ไว้ใน
 * โปรไฟล์ต้นแบบแบบเดียวกับทาง CLI ถ้าเปิดไม่ขึ้นหรือแปลงพลาด {@link DocumentGenerationService}
 * จะถอยกลับไปใช้ CLI เดิม จึงไม่มีกรณีที่ของเดิมทำได้แต่ของใหม่ทำไม่ได้
 *
 * <p>พอร์ตสุ่มจากพอร์ตว่างทุกครั้งที่เปิด และห้ามฆ่า process ที่ใช้พอร์ตอยู่ก่อน: บนเครื่อง dev
 * มีแอปหลายตัวและชุดทดสอบรันพร้อมกันได้ พอร์ตตายตัวจะทำให้ตัวหนึ่งไปปิด LibreOffice ของอีกตัว
 */
@Component
public class LibreOfficeProcessPool {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeProcessPool.class);

    private static final Path TEMPLATE_PROFILE_DIR =
            Path.of(System.getProperty("java.io.tmpdir"), "hrcp-lo-template-profile");

    private final boolean enabled;
    private final int processes;
    private final boolean warmOnStartup;

    private volatile LocalOfficeManager manager;
    private volatile LocalConverter converter;
    /** เปิดไม่ขึ้นแล้วครั้งหนึ่ง — ไม่ลองซ้ำทุก request ให้ช้าเพิ่ม ใช้ CLI ไปจนรีสตาร์ต */
    private volatile boolean unavailable;
    private volatile String sofficePath;

    public LibreOfficeProcessPool(
            @Value("${app.pdf.office-pool.enabled:true}") boolean enabled,
            @Value("${app.pdf.office-pool.processes:2}") int processes,
            @Value("${app.pdf.office-pool.warm-on-startup:true}") boolean warmOnStartup) {
        this.enabled = enabled;
        this.processes = Math.max(1, processes);
        this.warmOnStartup = warmOnStartup;
    }

    /** บอกตำแหน่ง soffice ที่ {@link DocumentGenerationService} หาเจอ ใช้หา office home */
    void useSoffice(String path) {
        if (sofficePath == null && path != null && !path.isEmpty()) {
            sofficePath = path;
        }
    }

    /**
     * เปิด LibreOffice ไว้ล่วงหน้า ผู้ใช้คนแรกจะได้ไม่ต้องรอเปิดโปรแกรม
     *
     * <p>{@code start()} แค่สั่งเปิด process — เอกสารแรกที่แต่ละ process แปลงยังช้าอยู่ราว
     * 1–2 วินาที (โหลดตัวกรองและฟอนต์) จึงแปลงไฟล์ตัวอย่างให้ทุก process หนึ่งรอบพร้อมกัน
     */
    void warmUp(byte[] sampleDocx) {
        if (!enabled || !warmOnStartup) {
            return;
        }
        Thread.ofVirtual().name("libreoffice-warmup").start(() -> {
            try {
                if (ensureStarted() == null || sampleDocx == null) {
                    return;
                }
                long start = System.currentTimeMillis();
                List<Thread> jobs = new ArrayList<>();
                for (int i = 0; i < processes; i++) {
                    jobs.add(Thread.ofVirtual().start(() -> {
                        try {
                            convert(sampleDocx);
                        } catch (IOException e) {
                            log.warn("LibreOffice warm-up conversion failed: {}", e.getMessage());
                        }
                    }));
                }
                for (Thread job : jobs) {
                    job.join();
                }
                log.info("LibreOffice pool warmed up in {} ms", System.currentTimeMillis() - start);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.warn("LibreOffice warm-up failed: {}", e.toString());
            }
        });
    }

    /**
     * แปลง DOCX เป็น PDF ด้วย LibreOffice ที่เปิดค้างไว้
     *
     * @return ไบต์ PDF หรือ null ถ้าใช้ทางนี้ไม่ได้ (ปิดไว้ หรือเปิดไม่ขึ้น) — ผู้เรียกใช้ CLI แทน
     * @throws IOException การแปลงครั้งนี้ล้ม ผู้เรียกลองทาง CLI ต่อได้
     */
    byte[] convert(byte[] docx) throws IOException {
        LocalConverter c = ensureStarted();
        if (c == null) {
            return null;
        }
        long start = System.currentTimeMillis();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64 * 1024, docx.length));
        try {
            c.convert(new ByteArrayInputStream(docx))
                    .as(DefaultDocumentFormatRegistry.DOCX)
                    .to(out)
                    .as(DefaultDocumentFormatRegistry.PDF)
                    .execute();
        } catch (OfficeException e) {
            throw new IOException("LibreOffice (pooled) conversion failed: " + e.getMessage(), e);
        }
        byte[] pdf = out.toByteArray();
        log.info("LibreOffice converted DOCX to PDF ({} bytes) in {} ms [pooled]",
                pdf.length, System.currentTimeMillis() - start);
        return pdf;
    }

    private LocalConverter ensureStarted() {
        if (!enabled || unavailable) {
            return null;
        }
        LocalConverter c = converter;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (converter != null || unavailable) {
                return converter;
            }
            long start = System.currentTimeMillis();
            try {
                prepareTemplateProfile();
                LocalOfficeManager.Builder builder = LocalOfficeManager.builder()
                        .portNumbers(freePorts(processes))
                        .existingProcessAction(ExistingProcessAction.FAIL)
                        .templateProfileDir(TEMPLATE_PROFILE_DIR.toFile())
                        .taskExecutionTimeout(60_000L)
                        .taskQueueTimeout(60_000L)
                        // LibreOffice กินหน่วยความจำขึ้นเรื่อย ๆ ถ้าเปิดยาว ๆ — เริ่ม process ใหม่เป็นระยะ
                        .maxTasksPerProcess(200);
                File home = officeHome();
                if (home != null) {
                    builder.officeHome(home);
                }
                LocalOfficeManager m = builder.build();
                m.start();
                manager = m;
                converter = LocalConverter.make(m);
                log.info("LibreOffice pool started with {} process(es) in {} ms",
                        processes, System.currentTimeMillis() - start);
                return converter;
            } catch (OfficeException | IOException | RuntimeException e) {
                unavailable = true;
                log.warn("LibreOffice pool could not start ({}); falling back to one soffice process per conversion",
                        e.toString());
                stopQuietly();
                return null;
            }
        }
    }

    /**
     * office home จาก soffice ที่หาเจอ: {@code <home>/program/soffice} (Linux/Windows) หรือ
     * {@code <home>/MacOS/soffice} (macOS, home คือ {@code LibreOffice.app/Contents})
     * ถ้า soffice ที่เจอเป็นสคริปต์ครอบ (เช่น Homebrew cask) ตำแหน่งนี้จะไม่ใช่ office home
     * จึงคืน null ให้ JODConverter หาเองจากตำแหน่งติดตั้งมาตรฐาน
     */
    private File officeHome() {
        String path = sofficePath;
        if (path == null) {
            return null;
        }
        try {
            Path bin = Path.of(path).toRealPath().getParent();
            Path home = bin == null ? null : bin.getParent();
            if (home == null) {
                return null;
            }
            LocalOfficeUtils.validateOfficeHome(home.toFile());
            return home.toFile();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void prepareTemplateProfile() throws IOException {
        Files.createDirectories(TEMPLATE_PROFILE_DIR.resolve("user"));
        DocumentGenerationService.installThaiFonts(TEMPLATE_PROFILE_DIR);
    }

    private static int[] freePorts(int count) throws IOException {
        int[] ports = new int[count];
        ServerSocket[] held = new ServerSocket[count];
        try {
            for (int i = 0; i < count; i++) {
                held[i] = new ServerSocket(0);
                ports[i] = held[i].getLocalPort();
            }
        } finally {
            for (ServerSocket s : held) {
                if (s != null) {
                    s.close();
                }
            }
        }
        return ports;
    }

    /** ใช้ LibreOffice ที่เปิดค้างอยู่ได้หรือไม่ (เปิดไปแล้ว) */
    boolean isRunning() {
        return converter != null;
    }

    @PreDestroy
    public synchronized void stop() {
        converter = null;
        stopQuietly();
    }

    private void stopQuietly() {
        LocalOfficeManager m = manager;
        manager = null;
        if (m == null) {
            return;
        }
        try {
            m.stop();
        } catch (OfficeException | RuntimeException e) {
            log.warn("Stopping the LibreOffice pool failed: {}", e.toString());
        }
    }
}
