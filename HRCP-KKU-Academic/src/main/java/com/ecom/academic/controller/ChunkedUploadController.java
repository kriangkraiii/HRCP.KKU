package com.ecom.academic.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.Principal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.model.AdminFile;
import com.ecom.academic.service.AdminStorageService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Chunked upload controller — supports unlimited file size by splitting files into 5MB chunks.
 *
 * <p>Admin storage (/admin/file-manager) only. This used to serve the applicant
 * file locker as well, defaulting {@code storageType} to "user" for anyone
 * signed in. That locker is now switched off
 * ({@link com.ecom.academic.config.UserStorageProperties}), so the user branch
 * is gone and the whole controller is closed to non-admins.
 *
 * <p>The role gate lives on the class rather than on each mapping because
 * /chunk, /complete and /abort take only an uploadId: a session opened by an
 * admin would otherwise be drivable by anyone holding that id.
 */
@Controller
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class ChunkedUploadController {

    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadController.class);

    private static final long CHUNK_SIZE = 5L * 1024 * 1024; // 5MB
    private static final String TEMP_DIR = "uploads/chunks";
    private static final String ADMIN_STORAGE = "admin";
    private static final int MAX_CHUNKS = 200_000; // 200k * 5MB ≈ 1TB, well past any real upload
    private static final Duration SESSION_TTL = Duration.ofHours(6);

    /**
     * Track active uploads: uploadId -> metadata.
     * Entries expire so that abandoned uploads (browser closed before /complete
     * or /abort) cannot accumulate in memory or leave chunk directories behind.
     */
    private final Cache<String, UploadSession> activeSessions = Caffeine.newBuilder()
            .expireAfterAccess(SESSION_TTL)
            .maximumSize(10_000)
            .<String, UploadSession>removalListener((id, session, cause) -> {
                if (session != null && cause.wasEvicted()) {
                    cleanupChunks(session);
                }
            })
            .build();

    private final AdminStorageService adminStorageService;

    private final UserRepository userRepository;

    public ChunkedUploadController(
            AdminStorageService adminStorageService,
            UserRepository userRepository) {
        this.adminStorageService = adminStorageService;
        this.userRepository = userRepository;
    }

    // ==================== Init Upload ====================

    @PostMapping("/api/upload/init")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initUpload(
            @RequestParam String filename,
            @RequestParam long fileSize,
            @RequestParam int totalChunks,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = ADMIN_STORAGE) String storageType,
            Principal principal) {

        Map<String, Object> result = new HashMap<>();

        UserDtls user = getUser(principal);
        if (user == null) {
            result.put("success", false);
            result.put("message", "กรุณาเข้าสู่ระบบ");
            return ResponseEntity.ok(result);
        }

        // Admin storage is the only destination left. The class-level role gate
        // already turns non-admins away; this rejects an admin asking for the
        // retired "user" destination rather than silently writing to the wrong
        // place.
        if (!ADMIN_STORAGE.equals(storageType) || !"ROLE_ADMIN".equals(user.getRole())) {
            result.put("success", false);
            result.put("message", "ไม่มีสิทธิ์อัปโหลดไปยังพื้นที่เก็บข้อมูลนี้");
            return ResponseEntity.ok(result);
        }

        if (fileSize <= 0 || totalChunks <= 0 || totalChunks > MAX_CHUNKS) {
            result.put("success", false);
            result.put("message", "ข้อมูลการอัปโหลดไม่ถูกต้อง");
            return ResponseEntity.ok(result);
        }

        // Validate file type and storage quota
        try {
            adminStorageService.validateFileType(filename);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }

        // เมื่อตั้งโควตาแอดมินเป็นไม่จำกัด getRemainingBytes() จะคืน -1
        // ซึ่งถ้าเอาไปเทียบตรง ๆ จะกลายเป็นว่าไฟล์ทุกขนาดใหญ่กว่าที่เหลือ
        // แล้วปฏิเสธทุกการอัปโหลดพร้อมข้อความว่าพื้นที่เต็ม
        if (!adminStorageService.isUnlimited()) {
            long remaining = adminStorageService.getRemainingBytes();
            if (fileSize > remaining) {
                result.put("success", false);
                result.put("message", "พื้นที่เก็บข้อมูลของผู้ดูแลระบบเต็ม (เหลือ " + adminStorageService.formatSize(remaining)
                        + " จากทั้งหมด " + adminStorageService.formatSize(adminStorageService.getMaxStorageBytes())
                        + ") ไม่สามารถอัปโหลดไฟล์ขนาด " + adminStorageService.formatSize(fileSize) + " ได้");
                return ResponseEntity.ok(result);
            }
        }

        String uploadId = UUID.randomUUID().toString();
        Path chunkDir = Path.of(TEMP_DIR, uploadId);

        try {
            Files.createDirectories(chunkDir);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "ไม่สามารถเตรียมโฟลเดอร์สำหรับอัปโหลดได้");
            return ResponseEntity.ok(result);
        }

        UploadSession session = new UploadSession();
        session.filename = filename;
        session.fileSize = fileSize;
        session.totalChunks = totalChunks;
        session.folderId = folderId;
        session.ownerEmail = user.getEmail();
        session.chunkDir = chunkDir;

        activeSessions.put(uploadId, session);

        result.put("success", true);
        result.put("uploadId", uploadId);
        result.put("chunkSize", CHUNK_SIZE);
        return ResponseEntity.ok(result);
    }

    // ==================== Upload Chunk ====================

    @PostMapping("/api/upload/chunk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) {

        Map<String, Object> result = new HashMap<>();

        UploadSession session = activeSessions.getIfPresent(uploadId);
        if (session == null) {
            result.put("success", false);
            result.put("message", "Upload session ไม่พบหรือหมดอายุ");
            return ResponseEntity.ok(result);
        }

        if (chunkIndex < 0 || chunkIndex >= session.totalChunks) {
            result.put("success", false);
            result.put("message", "ลำดับ chunk ไม่ถูกต้อง");
            return ResponseEntity.ok(result);
        }

        // Enforce the declared size against what is actually being sent, so the
        // quota check performed at init cannot be sidestepped.
        long afterThisChunk = session.bytesReceived.addAndGet(chunk.getSize());
        if (afterThisChunk > session.fileSize) {
            session.bytesReceived.addAndGet(-chunk.getSize());
            result.put("success", false);
            result.put("message", "ขนาดไฟล์ที่อัปโหลดเกินกว่าที่แจ้งไว้");
            return ResponseEntity.ok(result);
        }

        try {
            Path chunkFile = session.chunkDir.resolve("chunk_" + String.format("%06d", chunkIndex));
            Files.copy(chunk.getInputStream(), chunkFile, StandardCopyOption.REPLACE_EXISTING);
            int received = session.receivedChunks.incrementAndGet();

            result.put("success", true);
            result.put("received", received);
            result.put("total", session.totalChunks);
        } catch (IOException e) {
            session.bytesReceived.addAndGet(-chunk.getSize());
            log.error("Failed to persist chunk {} of upload {}: {}", chunkIndex, uploadId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "บันทึก chunk ไม่สำเร็จ");
        }

        return ResponseEntity.ok(result);
    }

    // ==================== Complete Upload ====================

    @PostMapping("/api/upload/complete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> completeUpload(@RequestParam String uploadId) {

        Map<String, Object> result = new HashMap<>();

        UploadSession session = activeSessions.getIfPresent(uploadId);
        if (session == null) {
            result.put("success", false);
            result.put("message", "Upload session ไม่พบ");
            return ResponseEntity.ok(result);
        }

        try {
            Path storageDir = Path.of("uploads/admin-storage");
            Files.createDirectories(storageDir);

            // Determine file extension
            String ext = "";
            if (session.filename.contains(".")) {
                ext = session.filename.substring(session.filename.lastIndexOf('.'));
            }
            Path finalPath = storageDir.resolve(UUID.randomUUID() + ext);

            // Assemble chunks into final file
            try (OutputStream out = Files.newOutputStream(finalPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                for (int i = 0; i < session.totalChunks; i++) {
                    Path chunkFile = session.chunkDir.resolve("chunk_" + String.format("%06d", i));
                    if (Files.exists(chunkFile)) {
                        Files.copy(chunkFile, out);
                    }
                }
            }

            // Record what was actually written, not what the client claimed at init.
            long actualSize = Files.size(finalPath);

            // Save to DB
            AdminFile saved = adminStorageService.saveUploadedFile(
                    session.filename, finalPath.toString(), actualSize,
                    detectContentType(session.filename), session.folderId, session.ownerEmail);
            String savedName = saved.getOriginalFilename();

            // Cleanup chunks
            cleanupChunks(session);
            activeSessions.invalidate(uploadId);

            result.put("success", true);
            result.put("message", "อัปโหลด '" + savedName + "' สำเร็จ");

        } catch (Exception e) {
            cleanupChunks(session);
            activeSessions.invalidate(uploadId);
            log.error("Failed to assemble upload {}: {}", uploadId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "รวมไฟล์ไม่สำเร็จ");
        }

        return ResponseEntity.ok(result);
    }

    // ==================== Abort Upload ====================

    @PostMapping("/api/upload/abort")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> abortUpload(@RequestParam String uploadId) {
        Map<String, Object> result = new HashMap<>();

        UploadSession session = activeSessions.getIfPresent(uploadId);
        if (session != null) {
            activeSessions.invalidate(uploadId);
            cleanupChunks(session);
        }

        result.put("success", true);
        result.put("message", "ยกเลิกการอัปโหลดแล้ว");
        return ResponseEntity.ok(result);
    }

    // ==================== Helpers ====================

    private UserDtls getUser(Principal principal) {
        return principal != null ? userRepository.findByEmail(principal.getName()) : null;
    }

    private void cleanupChunks(UploadSession session) {
        try {
            if (session.chunkDir != null && Files.exists(session.chunkDir)) {
                try (java.util.stream.Stream<Path> paths = Files.walk(session.chunkDir)) {
                    paths.sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(java.io.File::delete);
                }
            }
        } catch (IOException e) {
            // Leaves a temp directory behind; worth knowing about when disk fills.
            log.warn("Failed to clean up chunk directory {}: {}", session.chunkDir, e.toString());
        }
    }

    private String detectContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".rar")) return "application/x-rar-compressed";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".csv")) return "text/csv";
        return "application/octet-stream";
    }

    // Inner class for session tracking
    private static class UploadSession {
        String filename;
        long fileSize;
        int totalChunks;
        final AtomicLong bytesReceived = new AtomicLong();
        final java.util.concurrent.atomic.AtomicInteger receivedChunks =
                new java.util.concurrent.atomic.AtomicInteger();
        Long folderId;
        String ownerEmail;
        Path chunkDir;
    }
}
