package com.ecom.academic.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.model.AcademicDocumentEditLog;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.AcademicRevisionFile;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicRevisionFileRepository;
import com.ecom.model.UserDtls;
import com.ecom.service.UploadPaths;
import com.ecom.util.DocumentFileTypeValidator;
import com.ecom.util.FileUtils;

/**
 * ผู้ยื่นส่งเอกสารที่แก้ไขแล้วกลับมา (Flow ข้อ 13) — หลายไฟล์และลิงก์ในครั้งเดียว
 *
 * <p>ทั้งรอบส่งพร้อมกันแล้วจึงเปลี่ยนสถานะเป็น {@link RequestStatus#REVISION_SUBMITTED}
 * ถ้าให้อัปทีละไฟล์ สถานะจะเปลี่ยนตั้งแต่ไฟล์แรก และเจ้าหน้าที่อาจส่งต่ออนุกรรมการไปก่อนไฟล์ครบ
 */
@Service
public class AcademicRevisionService {

    private static final Logger log = LoggerFactory.getLogger(AcademicRevisionService.class);

    /** จำนวนไฟล์และลิงก์รวมต่อรอบ */
    public static final int MAX_ITEMS = 10;

    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_TITLE_LENGTH = 500;

    public record LinkInput(String url, String title) {
    }

    private final AcademicRevisionFileRepository repository;
    private final AcademicRequestService requestService;
    private final UploadPaths uploadPaths;
    private final DocumentFileTypeValidator fileTypeValidator;

    public AcademicRevisionService(AcademicRevisionFileRepository repository,
            AcademicRequestService requestService,
            UploadPaths uploadPaths,
            DocumentFileTypeValidator fileTypeValidator) {
        this.repository = repository;
        this.requestService = requestService;
        this.uploadPaths = uploadPaths;
        this.fileTypeValidator = fileTypeValidator;
    }

    /**
     * บันทึกฉบับแก้ทั้งรอบแล้วส่งคำร้องเข้าขั้น "ส่งเอกสารแก้ไขแล้ว"
     *
     * @throws IllegalArgumentException ข้อความภาษาไทยสำหรับแสดงให้ผู้ยื่น เมื่อข้อมูลที่ส่งมาใช้ไม่ได้
     * @throws IllegalStateException เมื่อคำร้องไม่ได้อยู่ในขั้นรอแก้ไข
     */
    @Transactional
    public List<AcademicRevisionFile> submit(AcademicRequest request, List<MultipartFile> files,
            List<LinkInput> links, UserDtls applicant) throws IOException {
        if (request.getCurrentStatus() != RequestStatus.COMPLETED_REVISE) {
            throw new IllegalStateException("คำร้องนี้ไม่ได้อยู่ในขั้นรอแก้ไขเอกสาร");
        }

        List<MultipartFile> realFiles = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        List<LinkInput> realLinks = links == null ? List.of()
                : links.stream().filter(l -> l.url() != null && !l.url().isBlank()).toList();

        if (realFiles.isEmpty() && realLinks.isEmpty()) {
            throw new IllegalArgumentException("กรุณาแนบไฟล์หรือลิงก์เอกสารที่แก้ไขแล้วอย่างน้อย 1 รายการ");
        }
        if (realFiles.size() + realLinks.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("แนบได้ไม่เกิน " + MAX_ITEMS + " รายการต่อครั้ง");
        }
        // ตรวจทุกรายการก่อนเขียนไฟล์ใด ๆ ลงดิสก์ — ไม่ให้เหลือไฟล์ครึ่งรอบเมื่อรายการหลัง ๆ ใช้ไม่ได้
        for (MultipartFile file : realFiles) {
            fileTypeValidator.validate(file.getOriginalFilename());
        }
        for (LinkInput link : realLinks) {
            validateUrl(link.url().trim());
        }

        int round = repository.findLatestRound(request.getId()) + 1;
        Path dir = uploadPaths.dir("academic", String.valueOf(request.getId()), "revisions", "round-" + round);
        Files.createDirectories(dir);

        List<Path> written = new ArrayList<>();
        List<AcademicRevisionFile> saved = new ArrayList<>();
        try {
            for (MultipartFile file : realFiles) {
                Path target = dir.resolve(FileUtils.sanitizeFilename(file.getOriginalFilename()));
                file.transferTo(target);
                written.add(target);
                saved.add(record(request, round, file.getOriginalFilename(), uploadPaths.toStored(target),
                        extensionOf(file.getOriginalFilename()), file.getSize()));
            }
            for (LinkInput link : realLinks) {
                String url = link.url().trim();
                String title = link.title() != null && !link.title().isBlank() ? link.title().trim() : url;
                if (title.length() > MAX_TITLE_LENGTH) {
                    title = title.substring(0, MAX_TITLE_LENGTH - 3) + "...";
                }
                saved.add(record(request, round, title, url, AcademicRevisionFile.LINK, 0L));
            }
            saved = repository.saveAll(saved);

            String summary = String.join(", ", saved.stream().map(AcademicRevisionFile::getOriginalFilename).toList());
            requestService.logDocumentChange(request, AcademicRequestService.REQUEST_FILES_DOC_TYPE,
                    "เอกสารฉบับแก้ไขรอบที่ " + round + ": " + summary, applicant,
                    AcademicDocumentEditLog.EditAction.FILE_UPLOADED);
            requestService.updateStatus(request.getId(), RequestStatus.REVISION_SUBMITTED, applicant,
                    "ผู้ขอกำหนดตำแหน่งส่งเอกสารที่แก้ไขแล้ว " + saved.size() + " รายการ: " + summary);
            return saved;
        } catch (RuntimeException | IOException e) {
            for (Path path : written) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanup) {
                    log.warn("Failed to remove revision file after aborted submit: {}", cleanup.getMessage());
                }
            }
            throw e;
        }
    }

    /** ฉบับแก้ทุกรอบ รอบล่าสุดก่อน */
    public Map<Integer, List<AcademicRevisionFile>> byRound(Long requestId) {
        Map<Integer, List<AcademicRevisionFile>> rounds = new LinkedHashMap<>();
        for (AcademicRevisionFile f : repository.findByRequestIdOrderByRoundDescIdAsc(requestId)) {
            rounds.computeIfAbsent(f.getRound(), r -> new ArrayList<>()).add(f);
        }
        return rounds;
    }

    /** ฉบับแก้รอบล่าสุด ว่างเมื่อยังไม่เคยส่ง */
    public List<AcademicRevisionFile> latestRound(Long requestId) {
        return byRound(requestId).values().stream().findFirst().orElse(List.of());
    }

    /** รายการนี้ต้องเป็นของคำร้องนี้ — กันการเดา id ข้ามคำร้อง */
    public Optional<AcademicRevisionFile> find(Long requestId, Long fileId) {
        return repository.findById(fileId).filter(f -> f.getRequest().getId().equals(requestId));
    }

    /** ลิงก์ส่งต่อไปยังปลายทาง ไฟล์ส่งให้ดาวน์โหลด */
    public ResponseEntity<Resource> serve(AcademicRevisionFile file) throws IOException {
        if (file.isLink()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(file.getStoredPath())).build();
        }
        Path path = uploadPaths.resolve(file.getStoredPath());
        if (path == null || !Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok()
                // ชื่อไทยต้องเข้ารหัสแบบ RFC 5987 — ใส่ตรง ๆ ในส่วน filename เบราว์เซอร์จะทิ้งแล้วตั้งชื่อจาก URL แทน
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                .contentLength(Files.size(path))
                .body(new FileSystemResource(path));
    }

    private static void validateUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("รูปแบบลิงก์ไม่ถูกต้อง (ต้องขึ้นต้นด้วย http:// หรือ https://): " + url);
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("ลิงก์ยาวเกินขีดจำกัด (สูงสุด 2,048 ตัวอักษร)");
        }
        try {
            URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("รูปแบบลิงก์ไม่ถูกต้อง: " + url);
        }
    }

    private static AcademicRevisionFile record(AcademicRequest request, int round, String name, String stored,
            String type, long size) {
        AcademicRevisionFile f = new AcademicRevisionFile();
        f.setRequest(request);
        f.setRound(round);
        f.setOriginalFilename(name);
        f.setStoredPath(stored);
        f.setFileType(type);
        f.setFileSize(size);
        return f;
    }

    private static String extensionOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toUpperCase() : "";
    }
}
