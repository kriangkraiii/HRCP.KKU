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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.dto.FileItemDTO;
import com.ecom.academic.model.AdminFile;
import com.ecom.academic.model.AdminFolder;
import com.ecom.academic.service.AdminStorageService;
import com.ecom.academic.service.FileManagementService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/file-manager")
public class FileManagerController {

    @Autowired
    private FileManagementService fileManagementService;

    @Autowired
    private AdminStorageService adminStorageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminLogService adminLogService;

    @Autowired
    private HttpServletRequest httpRequest;

    // ================== Original Document File Manager ==================

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
    public ResponseEntity<Map<String, Object>> softDelete(@PathVariable String type, @PathVariable Long id, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            fileManagementService.softDelete(type, id);
            result.put("success", true);
            result.put("message", "ย้ายไปถังขยะแล้ว");
            logFileAction(principal, "DELETE_ACADEMIC_FILE", "ย้ายไฟล์ไปถังขยะ (" + type + " #" + id + ")");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/restore/{type}/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restore(@PathVariable String type, @PathVariable Long id, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            fileManagementService.restore(type, id);
            result.put("success", true);
            result.put("message", "กู้คืนสำเร็จ");
            logFileAction(principal, "RESTORE_ACADEMIC_FILE", "กู้คืนไฟล์จากถังขยะ (" + type + " #" + id + ")");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/permanent-delete/{type}/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> permanentDelete(@PathVariable String type, @PathVariable Long id, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            fileManagementService.permanentDelete(type, id);
            result.put("success", true);
            result.put("message", "ลบถาวรสำเร็จ");
            logFileAction(principal, "PERMANENT_DELETE_ACADEMIC_FILE", "ลบถาวรไฟล์ (" + type + " #" + id + ")");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/empty-trash")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> emptyTrash(Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            fileManagementService.emptyTrash();
            result.put("success", true);
            result.put("message", "ล้างถังขยะสำเร็จ");
            logFileAction(principal, "EMPTY_TRASH", "ล้างถังขยะเอกสารทั้งหมด");
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
            if (contentType == null) contentType = "application/octet-stream";

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

    // ================== Storage (Cloud-like File Manager) ==================

    @GetMapping("/storage")
    public String storage(@RequestParam(required = false) Long folderId, Model model, Principal principal) {
        addUserToModel(model, principal);

        List<AdminFolder> subFolders = adminStorageService.listSubFolders(folderId);
        List<AdminFile> files = adminStorageService.listFiles(folderId);
        List<AdminFolder> breadcrumb = adminStorageService.getBreadcrumb(folderId);
        AdminFolder currentFolder = folderId != null ? adminStorageService.getFolder(folderId) : null;

        model.addAttribute("subFolders", subFolders);
        model.addAttribute("storageFiles", files);
        model.addAttribute("breadcrumb", breadcrumb);
        model.addAttribute("currentFolder", currentFolder);
        model.addAttribute("currentFolderId", folderId);
        model.addAttribute("viewMode", "storage");

        model.addAttribute("totalFiles", adminStorageService.getTotalFileCount());
        model.addAttribute("totalSize", adminStorageService.formatSize(adminStorageService.getTotalSize()));
        model.addAttribute("totalFolders", adminStorageService.getTotalFolderCount());
        model.addAttribute("trashCount", adminStorageService.getTrashCount());

        return "academic/admin/file_manager";
    }

    @PostMapping("/storage/folder/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFolder(
            @RequestParam String name,
            @RequestParam(required = false) Long parentId,
            Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            String email = principal != null ? principal.getName() : "system";
            AdminFolder folder = adminStorageService.createFolder(name, parentId, email);
            result.put("success", true);
            result.put("message", "สร้างโฟลเดอร์ '" + folder.getName() + "' สำเร็จ");
            result.put("folderId", folder.getId());
            logFileAction(principal, "CREATE_FOLDER", "สร้างโฟลเดอร์ '" + name + "'");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/storage/folder/rename")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> renameFolder(
            @RequestParam Long id, @RequestParam String name, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            adminStorageService.renameFolder(id, name);
            result.put("success", true);
            result.put("message", "เปลี่ยนชื่อโฟลเดอร์สำเร็จ");
            logFileAction(principal, "RENAME_FOLDER", "เปลี่ยนชื่อโฟลเดอร์ #" + id + " เป็น '" + name + "'");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/storage/folder/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFolderAction(@RequestParam Long id, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            adminStorageService.deleteFolder(id);
            result.put("success", true);
            result.put("message", "ลบโฟลเดอร์สำเร็จ");
            logFileAction(principal, "DELETE_FOLDER", "ลบโฟลเดอร์ #" + id);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/storage/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            String email = principal != null ? principal.getName() : "system";
            AdminFile saved = adminStorageService.uploadFile(file, folderId, email);
            result.put("success", true);
            result.put("message", "อัปโหลด '" + saved.getOriginalFilename() + "' สำเร็จ");
            logFileAction(principal, "UPLOAD_FILE", "อัปโหลดไฟล์ '" + saved.getOriginalFilename() + "'");
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "อัปโหลดไม่สำเร็จ: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/storage/file/rename")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> renameFileAction(
            @RequestParam Long id, @RequestParam String name, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            adminStorageService.renameFile(id, name);
            result.put("success", true);
            result.put("message", "เปลี่ยนชื่อไฟล์สำเร็จ");
            logFileAction(principal, "RENAME_FILE", "เปลี่ยนชื่อไฟล์ #" + id + " เป็น '" + name + "'");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/storage/file/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteStorageFile(@RequestParam Long id, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            adminStorageService.softDeleteFile(id);
            result.put("success", true);
            result.put("message", "ย้ายไปถังขยะแล้ว");
            logFileAction(principal, "DELETE_FILE", "ย้ายไฟล์ไปถังขยะ #" + id);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/storage/file/restore")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restoreStorageFile(@RequestParam Long id, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            adminStorageService.restoreFile(id);
            result.put("success", true);
            result.put("message", "กู้คืนสำเร็จ");
            logFileAction(principal, "RESTORE_FILE", "กู้คืนไฟล์จากถังขยะ #" + id);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/storage/file/permanent-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> permanentDeleteStorageFile(@RequestParam Long id, Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            adminStorageService.permanentDeleteFile(id);
            result.put("success", true);
            result.put("message", "ลบถาวรสำเร็จ");
            logFileAction(principal, "PERMANENT_DELETE_FILE", "ลบถาวรไฟล์ #" + id);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/storage/download/{id}")
    public ResponseEntity<?> downloadStorageFile(@PathVariable Long id) {
        try {
            AdminFile file = adminStorageService.getFile(id);
            if (file == null || file.getStoredFilePath() == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = Path.of(file.getStoredFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            String contentType = file.getContentType();
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileBytes.length)
                    .body(new ByteArrayResource(fileBytes));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/storage/trash")
    public String storageTrash(Model model, Principal principal) {
        addUserToModel(model, principal);

        List<AdminFile> trashFiles = adminStorageService.getTrashFiles();

        model.addAttribute("storageTrashFiles", trashFiles);
        model.addAttribute("viewMode", "storage-trash");
        model.addAttribute("totalFiles", adminStorageService.getTotalFileCount());
        model.addAttribute("totalSize", adminStorageService.formatSize(adminStorageService.getTotalSize()));
        model.addAttribute("totalFolders", adminStorageService.getTotalFolderCount());
        model.addAttribute("trashCount", adminStorageService.getTrashCount());

        return "academic/admin/file_manager";
    }

    @PostMapping("/storage/empty-trash")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> emptyStorageTrash(Principal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            adminStorageService.emptyTrash();
            result.put("success", true);
            result.put("message", "ล้างถังขยะสำเร็จ");
            logFileAction(principal, "EMPTY_FILE_TRASH", "ล้างถังขยะคลังไฟล์ทั้งหมด");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    // ================== Helpers ==================

    private void addUserToModel(Model model, Principal principal) {
        if (principal != null) {
            UserDtls user = userRepository.findByEmail(principal.getName());
            model.addAttribute("user", user);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void logFileAction(Principal principal, String action, String details) {
        try {
            String email = principal != null ? principal.getName() : "system";
            UserDtls user = principal != null ? userRepository.findByEmail(email) : null;
            String name = user != null ? user.getName() : email;
            String ip = getClientIpAddress();
            adminLogService.log(email, name, action, details, ip);
        } catch (Exception ignored) {}
    }

    private String getClientIpAddress() {
        String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        return httpRequest.getRemoteAddr();
    }
}
