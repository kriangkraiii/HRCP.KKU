// [ปิดการใช้งาน] คลังไฟล์ส่วนตัวของผู้ยื่น (user storage)
//
// ระบบเคยมีคลังไฟล์สองชุดที่เป็นฝาแฝดกัน ฝั่งแอดมิน (/admin/file-manager)
// กับฝั่งผู้ยื่น (/user/academic/storage) ตอนนี้เหลือใช้เฉพาะฝั่งแอดมิน
//
// ตาราง user_file และ user_folder ถูก drop ไปแล้วด้วย V16__drop_user_storage.sql
// และไฟล์ใน uploads/user-storage/ ถูกลบทิ้ง โค้ดข้างล่างจึงไม่มีอะไรรองรับ
// เก็บไว้เป็นคอมเมนต์ตามที่ตกลงกันไว้ ไม่ได้ลบทิ้ง
//
// ถ้าจะเปิดกลับ ต้องสร้างตารางทั้งสองขึ้นใหม่ก่อน ไม่ใช่แค่ปลดคอมเมนต์

// package com.ecom.academic.controller;
//
// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.security.Principal;
// import java.util.HashMap;
// import java.util.Map;
//
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.ResponseBody;
// import org.springframework.web.multipart.MultipartFile;
//
// import com.ecom.academic.model.UserFile;
// import com.ecom.academic.model.UserFolder;
// import com.ecom.academic.service.UserStorageService;
// import com.ecom.model.UserDtls;
// import com.ecom.repository.UserRepository;
//
// @Controller
// @RequestMapping("/user/academic/storage")
// public class UserFileManagerController {
//
//     private final UserStorageService storageService;
//
//     private final UserRepository userRepository;
//
//     public UserFileManagerController(UserStorageService storageService, UserRepository userRepository) {
//         this.storageService = storageService;
//         this.userRepository = userRepository;
//     }
//
//     private UserDtls getUser(Principal principal) {
//         return principal != null ? userRepository.findByEmail(principal.getName()) : null;
//     }
//
//     // ================== Main View ==================
//
//     @GetMapping
//     public String storage(@RequestParam(required = false) Long folderId, Model model, Principal principal) {
//         UserDtls user = getUser(principal);
//         if (user == null) return "redirect:/signin";
//         model.addAttribute("user", user);
//
//         Integer uid = user.getId();
//         model.addAttribute("subFolders", storageService.listSubFolders(uid, folderId));
//         model.addAttribute("storageFiles", storageService.listFiles(uid, folderId));
//         model.addAttribute("breadcrumb", storageService.getBreadcrumb(folderId, uid));
//         model.addAttribute("currentFolder", folderId != null ? storageService.getFolder(folderId, uid) : null);
//         model.addAttribute("currentFolderId", folderId);
//         model.addAttribute("viewMode", "storage");
//         addStats(model, uid);
//
//         return "academic/applicant/user_storage";
//     }
//
//     @GetMapping("/trash")
//     public String trash(Model model, Principal principal) {
//         UserDtls user = getUser(principal);
//         if (user == null) return "redirect:/signin";
//         model.addAttribute("user", user);
//
//         Integer uid = user.getId();
//         model.addAttribute("storageTrashFiles", storageService.getTrashFiles(uid));
//         model.addAttribute("viewMode", "storage-trash");
//         addStats(model, uid);
//
//         return "academic/applicant/user_storage";
//     }
//
//     // ================== Folder CRUD ==================
//
//     @PostMapping("/folder/create")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> createFolder(
//             @RequestParam String name, @RequestParam(required = false) Long parentId, Principal principal) {
//         return doAction(principal, user -> {
//             UserFolder f = storageService.createFolder(name, parentId, user.getId());
//             return "สร้างโฟลเดอร์ '" + f.getName() + "' สำเร็จ";
//         });
//     }
//
//     @PostMapping("/folder/rename")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> renameFolder(
//             @RequestParam Long id, @RequestParam String name, Principal principal) {
//         return doAction(principal, user -> { storageService.renameFolder(id, name, user.getId()); return "เปลี่ยนชื่อสำเร็จ"; });
//     }
//
//     @PostMapping("/folder/delete")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> deleteFolder(@RequestParam Long id, Principal principal) {
//         return doAction(principal, user -> { storageService.deleteFolder(id, user.getId()); return "ลบโฟลเดอร์สำเร็จ"; });
//     }
//
//     // ================== File CRUD ==================
//
//     @PostMapping("/upload")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> upload(
//             @RequestParam("file") MultipartFile file, @RequestParam(required = false) Long folderId, Principal principal) {
//         return doAction(principal, user -> {
//             UserFile saved = storageService.uploadFile(file, folderId, user.getId());
//             return "อัปโหลด '" + saved.getOriginalFilename() + "' สำเร็จ";
//         });
//     }
//
//     @PostMapping("/file/rename")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> renameFile(
//             @RequestParam Long id, @RequestParam String name, Principal principal) {
//         return doAction(principal, user -> { storageService.renameFile(id, name, user.getId()); return "เปลี่ยนชื่อสำเร็จ"; });
//     }
//
//     @PostMapping("/file/delete")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> deleteFile(@RequestParam Long id, Principal principal) {
//         return doAction(principal, user -> { storageService.softDeleteFile(id, user.getId()); return "ย้ายไปถังขยะแล้ว"; });
//     }
//
//     @PostMapping("/file/restore")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> restoreFile(@RequestParam Long id, Principal principal) {
//         return doAction(principal, user -> { storageService.restoreFile(id, user.getId()); return "กู้คืนสำเร็จ"; });
//     }
//
//     @PostMapping("/file/permanent-delete")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> permanentDelete(@RequestParam Long id, Principal principal) {
//         return doAction(principal, user -> { storageService.permanentDeleteFile(id, user.getId()); return "ลบถาวรสำเร็จ"; });
//     }
//
//     @GetMapping("/download/{id}")
//     public ResponseEntity<?> download(@PathVariable Long id, Principal principal) {
//         UserDtls user = getUser(principal);
//         if (user == null) return ResponseEntity.status(401).build();
//
//         try {
//             UserFile file = storageService.getFile(id, user.getId());
//             if (file == null) return ResponseEntity.notFound().build();
//
//             Path filePath = Path.of(file.getStoredFilePath());
//             if (!Files.exists(filePath)) return ResponseEntity.notFound().build();
//
//             String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
//
//             String rawFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
//             String safeFilename = java.net.URLEncoder.encode(rawFilename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
//             String asciiFilename = rawFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
//
//             return ResponseEntity.ok()
//                     .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + safeFilename)
//                     .contentType(MediaType.parseMediaType(ct))
//                     .contentLength(Files.size(filePath))
//                     .body(new org.springframework.core.io.FileSystemResource(filePath));
//         } catch (IOException e) {
//             return ResponseEntity.internalServerError().build();
//         }
//     }
//
//     // ================== Trash ==================
//
//     @PostMapping("/empty-trash")
//     @ResponseBody
//     public ResponseEntity<Map<String, Object>> emptyTrash(Principal principal) {
//         return doAction(principal, user -> { storageService.emptyTrash(user.getId()); return "ล้างถังขยะสำเร็จ"; });
//     }
//
//     // ================== Helpers ==================
//
//     private void addStats(Model model, Integer uid) {
//         model.addAttribute("totalFiles", storageService.getTotalFileCount(uid));
//         model.addAttribute("totalSize", storageService.formatSize(storageService.getTotalSize(uid)));
//         model.addAttribute("trashCount", storageService.getTrashCount(uid));
//         model.addAttribute("maxStorage", storageService.formatSize(storageService.getMaxStorageBytes()));
//         model.addAttribute("usagePercent", storageService.getUsagePercentage(uid));
//         model.addAttribute("remainingStorage", storageService.formatSize(storageService.getRemainingBytes(uid)));
//     }
//
//     @FunctionalInterface
//     private interface ActionFn { String execute(UserDtls user) throws Exception; }
//
//     private ResponseEntity<Map<String, Object>> doAction(Principal principal, ActionFn action) {
//         Map<String, Object> result = new HashMap<>();
//         try {
//             UserDtls user = getUser(principal);
//             if (user == null) { result.put("success", false); result.put("message", "กรุณาเข้าสู่ระบบ"); return ResponseEntity.ok(result); }
//             String msg = action.execute(user);
//             result.put("success", true);
//             result.put("message", msg);
//         } catch (Exception e) {
//             result.put("success", false);
//             result.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
//         }
//         return ResponseEntity.ok(result);
//     }
// }
