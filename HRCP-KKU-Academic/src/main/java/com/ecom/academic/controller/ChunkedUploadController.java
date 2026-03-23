package com.ecom.academic.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.model.AdminFile;
import com.ecom.academic.model.UserFile;
import com.ecom.academic.service.AdminStorageService;
import com.ecom.academic.service.UserStorageService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Chunked upload controller — supports unlimited file size by splitting files into 5MB chunks.
 * Works for both user (/user/academic) and admin (/admin/file-manager) storage.
 */
@Controller
public class ChunkedUploadController {

    private static final long CHUNK_SIZE = 5L * 1024 * 1024; // 5MB
    private static final String TEMP_DIR = "uploads/chunks";

    // Track active uploads: uploadId -> metadata
    private final ConcurrentHashMap<String, UploadSession> activeSessions = new ConcurrentHashMap<>();

    @Autowired
    private UserStorageService userStorageService;

    @Autowired
    private AdminStorageService adminStorageService;

    @Autowired
    private UserRepository userRepository;

    // ==================== Init Upload ====================

    @PostMapping("/api/upload/init")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initUpload(
            @RequestParam String filename,
            @RequestParam long fileSize,
            @RequestParam int totalChunks,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "user") String storageType,
            Principal principal) {

        Map<String, Object> result = new HashMap<>();

        UserDtls user = getUser(principal);
        if (user == null) {
            result.put("success", false);
            result.put("message", "กรุณาเข้าสู่ระบบ");
            return ResponseEntity.ok(result);
        }

        // Check user storage quota (only for user storage)
        if ("user".equals(storageType)) {
            long remaining = userStorageService.getRemainingBytes(user.getId());
            if (fileSize > remaining) {
                result.put("success", false);
                result.put("message", "พื้นที่เก็บข้อมูลไม่พอ (เหลือ " + userStorageService.formatSize(remaining)
                        + ") ไม่สามารถอัปโหลดไฟล์ขนาด " + userStorageService.formatSize(fileSize) + " ได้");
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
        session.uploadId = uploadId;
        session.filename = filename;
        session.fileSize = fileSize;
        session.totalChunks = totalChunks;
        session.receivedChunks = 0;
        session.folderId = folderId;
        session.ownerId = user.getId();
        session.ownerEmail = user.getEmail();
        session.storageType = storageType;
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

        UploadSession session = activeSessions.get(uploadId);
        if (session == null) {
            result.put("success", false);
            result.put("message", "Upload session ไม่พบหรือหมดอายุ");
            return ResponseEntity.ok(result);
        }

        try {
            Path chunkFile = session.chunkDir.resolve("chunk_" + String.format("%06d", chunkIndex));
            Files.copy(chunk.getInputStream(), chunkFile);
            session.receivedChunks++;

            result.put("success", true);
            result.put("received", session.receivedChunks);
            result.put("total", session.totalChunks);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "บันทึก chunk ไม่สำเร็จ: " + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    // ==================== Complete Upload ====================

    @PostMapping("/api/upload/complete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> completeUpload(@RequestParam String uploadId) {

        Map<String, Object> result = new HashMap<>();

        UploadSession session = activeSessions.get(uploadId);
        if (session == null) {
            result.put("success", false);
            result.put("message", "Upload session ไม่พบ");
            return ResponseEntity.ok(result);
        }

        try {
            // Determine storage directory
            String storageRoot;
            if ("admin".equals(session.storageType)) {
                storageRoot = "uploads/admin-storage";
            } else {
                storageRoot = "uploads/user-storage/" + session.ownerId;
            }

            Path storageDir = Path.of(storageRoot);
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

            // Save to DB
            String savedName;
            if ("admin".equals(session.storageType)) {
                AdminFile saved = adminStorageService.saveUploadedFile(
                        session.filename, finalPath.toString(), session.fileSize,
                        detectContentType(session.filename), session.folderId, session.ownerEmail);
                savedName = saved.getOriginalFilename();
            } else {
                UserFile saved = userStorageService.saveUploadedFile(
                        session.filename, finalPath.toString(), session.fileSize,
                        detectContentType(session.filename), session.folderId, session.ownerId);
                savedName = saved.getOriginalFilename();
            }

            // Cleanup chunks
            cleanupChunks(session);
            activeSessions.remove(uploadId);

            result.put("success", true);
            result.put("message", "อัปโหลด '" + savedName + "' สำเร็จ");

        } catch (Exception e) {
            cleanupChunks(session);
            activeSessions.remove(uploadId);
            result.put("success", false);
            result.put("message", "รวมไฟล์ไม่สำเร็จ: " + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    // ==================== Abort Upload ====================

    @PostMapping("/api/upload/abort")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> abortUpload(@RequestParam String uploadId) {
        Map<String, Object> result = new HashMap<>();

        UploadSession session = activeSessions.remove(uploadId);
        if (session != null) {
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
                Files.walk(session.chunkDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        } catch (IOException ignored) {}
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
        String uploadId;
        String filename;
        long fileSize;
        int totalChunks;
        int receivedChunks;
        Long folderId;
        Integer ownerId;
        String ownerEmail;
        String storageType;
        Path chunkDir;
    }
}
