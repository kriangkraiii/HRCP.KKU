package com.ecom.academic.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecom.academic.dto.FileItemDTO;
import com.ecom.academic.service.FileManagementService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@Controller
@RequestMapping("/admin/file-manager")
public class FileManagerController {

    @Autowired
    private FileManagementService fileManagementService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String fileManager(Model model, Principal principal) {
        addUserToModel(model, principal);

        List<FileItemDTO> files = fileManagementService.getAllFiles();
        long trashCount = fileManagementService.getTrashCount();

        long totalSize = files.stream().mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0).sum();

        model.addAttribute("files", files);
        model.addAttribute("totalFiles", files.size());
        model.addAttribute("totalSize", formatSize(totalSize));
        model.addAttribute("trashCount", trashCount);
        model.addAttribute("viewMode", "files");

        return "academic/admin/file_manager";
    }

    @GetMapping("/trash")
    public String trash(Model model, Principal principal) {
        addUserToModel(model, principal);

        List<FileItemDTO> trashFiles = fileManagementService.getTrashFiles();
        List<FileItemDTO> activeFiles = fileManagementService.getAllFiles();

        long totalSize = activeFiles.stream().mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0).sum();

        model.addAttribute("files", trashFiles);
        model.addAttribute("totalFiles", activeFiles.size());
        model.addAttribute("totalSize", formatSize(totalSize));
        model.addAttribute("trashCount", trashFiles.size());
        model.addAttribute("viewMode", "trash");

        return "academic/admin/file_manager";
    }

    @PostMapping("/delete/{type}/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> softDelete(@PathVariable String type, @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            fileManagementService.softDelete(type, id);
            result.put("success", true);
            result.put("message", "ย้ายไปถังขยะแล้ว");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/restore/{type}/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restore(@PathVariable String type, @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            fileManagementService.restore(type, id);
            result.put("success", true);
            result.put("message", "กู้คืนสำเร็จ");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/permanent-delete/{type}/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> permanentDelete(@PathVariable String type, @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            fileManagementService.permanentDelete(type, id);
            result.put("success", true);
            result.put("message", "ลบถาวรสำเร็จ");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/empty-trash")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> emptyTrash() {
        Map<String, Object> result = new HashMap<>();
        try {
            fileManagementService.emptyTrash();
            result.put("success", true);
            result.put("message", "ล้างถังขยะสำเร็จ");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/download/{type}/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable String type, @PathVariable Long id) {
        try {
            FileItemDTO fileItem = fileManagementService.getFileById(type, id);
            if (fileItem == null || fileItem.getFilePath() == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = Path.of(fileItem.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null)
                contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileItem.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileBytes.length)
                    .body(new ByteArrayResource(fileBytes));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private void addUserToModel(Model model, Principal principal) {
        if (principal != null) {
            UserDtls user = userRepository.findByEmail(principal.getName());
            model.addAttribute("user", user);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
