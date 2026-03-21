package com.ecom.academic.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.model.UserFile;
import com.ecom.academic.model.UserFolder;
import com.ecom.academic.service.UserStorageService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@Controller
@RequestMapping("/user/academic/storage")
public class UserFileManagerController {

    @Autowired
    private UserStorageService storageService;

    @Autowired
    private UserRepository userRepository;

    private UserDtls getUser(Principal principal) {
        return principal != null ? userRepository.findByEmail(principal.getName()) : null;
    }

    // ================== Main View ==================

    @GetMapping
    public String storage(@RequestParam(required = false) Long folderId, Model model, Principal principal) {
        UserDtls user = getUser(principal);
        if (user == null) return "redirect:/signin";
        model.addAttribute("user", user);

        Integer uid = user.getId();
        model.addAttribute("subFolders", storageService.listSubFolders(uid, folderId));
        model.addAttribute("storageFiles", storageService.listFiles(uid, folderId));
        model.addAttribute("breadcrumb", storageService.getBreadcrumb(folderId, uid));
        model.addAttribute("currentFolder", folderId != null ? storageService.getFolder(folderId, uid) : null);
        model.addAttribute("currentFolderId", folderId);
        model.addAttribute("viewMode", "storage");
        addStats(model, uid);

        return "academic/applicant/user_storage";
    }

    @GetMapping("/trash")
    public String trash(Model model, Principal principal) {
        UserDtls user = getUser(principal);
        if (user == null) return "redirect:/signin";
        model.addAttribute("user", user);

        Integer uid = user.getId();
        model.addAttribute("storageTrashFiles", storageService.getTrashFiles(uid));
        model.addAttribute("viewMode", "storage-trash");
        addStats(model, uid);

        return "academic/applicant/user_storage";
    }

    // ================== Folder CRUD ==================

    @PostMapping("/folder/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFolder(
            @RequestParam String name, @RequestParam(required = false) Long parentId, Principal principal) {
        return doAction(principal, uid -> {
            UserFolder f = storageService.createFolder(name, parentId, uid);
            return "สร้างโฟลเดอร์ '" + f.getName() + "' สำเร็จ";
        });
    }

    @PostMapping("/folder/rename")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> renameFolder(
            @RequestParam Long id, @RequestParam String name, Principal principal) {
        return doAction(principal, uid -> { storageService.renameFolder(id, name, uid); return "เปลี่ยนชื่อสำเร็จ"; });
    }

    @PostMapping("/folder/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFolder(@RequestParam Long id, Principal principal) {
        return doAction(principal, uid -> { storageService.deleteFolder(id, uid); return "ลบโฟลเดอร์สำเร็จ"; });
    }

    // ================== File CRUD ==================

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file, @RequestParam(required = false) Long folderId, Principal principal) {
        return doAction(principal, uid -> {
            UserFile saved = storageService.uploadFile(file, folderId, uid);
            return "อัปโหลด '" + saved.getOriginalFilename() + "' สำเร็จ";
        });
    }

    @PostMapping("/file/rename")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> renameFile(
            @RequestParam Long id, @RequestParam String name, Principal principal) {
        return doAction(principal, uid -> { storageService.renameFile(id, name, uid); return "เปลี่ยนชื่อสำเร็จ"; });
    }

    @PostMapping("/file/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteFile(@RequestParam Long id, Principal principal) {
        return doAction(principal, uid -> { storageService.softDeleteFile(id, uid); return "ย้ายไปถังขยะแล้ว"; });
    }

    @PostMapping("/file/restore")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restoreFile(@RequestParam Long id, Principal principal) {
        return doAction(principal, uid -> { storageService.restoreFile(id, uid); return "กู้คืนสำเร็จ"; });
    }

    @PostMapping("/file/permanent-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> permanentDelete(@RequestParam Long id, Principal principal) {
        return doAction(principal, uid -> { storageService.permanentDeleteFile(id, uid); return "ลบถาวรสำเร็จ"; });
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        if (user == null) return ResponseEntity.status(401).build();

        try {
            UserFile file = storageService.getFile(id, user.getId());
            if (file == null) return ResponseEntity.notFound().build();

            Path filePath = Path.of(file.getStoredFilePath());
            if (!Files.exists(filePath)) return ResponseEntity.notFound().build();

            byte[] bytes = Files.readAllBytes(filePath);
            String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                    .contentType(MediaType.parseMediaType(ct))
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ================== Trash ==================

    @PostMapping("/empty-trash")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> emptyTrash(Principal principal) {
        return doAction(principal, uid -> { storageService.emptyTrash(uid); return "ล้างถังขยะสำเร็จ"; });
    }

    // ================== Helpers ==================

    private void addStats(Model model, Integer uid) {
        model.addAttribute("totalFiles", storageService.getTotalFileCount(uid));
        model.addAttribute("totalSize", storageService.formatSize(storageService.getTotalSize(uid)));
        model.addAttribute("trashCount", storageService.getTrashCount(uid));
        model.addAttribute("maxStorage", storageService.formatSize(storageService.getMaxStorageBytes()));
        model.addAttribute("usagePercent", storageService.getUsagePercentage(uid));
        model.addAttribute("remainingStorage", storageService.formatSize(storageService.getRemainingBytes(uid)));
    }

    @FunctionalInterface
    private interface ActionFn { String execute(Integer uid) throws Exception; }

    private ResponseEntity<Map<String, Object>> doAction(Principal principal, ActionFn action) {
        Map<String, Object> result = new HashMap<>();
        try {
            UserDtls user = getUser(principal);
            if (user == null) { result.put("success", false); result.put("message", "กรุณาเข้าสู่ระบบ"); return ResponseEntity.ok(result); }
            String msg = action.execute(user.getId());
            result.put("success", true);
            result.put("message", msg);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
