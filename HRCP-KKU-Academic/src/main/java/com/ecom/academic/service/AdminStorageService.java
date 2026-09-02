package com.ecom.academic.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.model.AdminFile;
import com.ecom.academic.model.AdminFolder;
import com.ecom.academic.repository.AdminFileRepository;
import com.ecom.academic.repository.AdminFolderRepository;

@Service
public class AdminStorageService {

    private static final String STORAGE_ROOT = "uploads/admin-storage";

    private final long maxStorageBytes;
    private final Set<String> allowedExtensions;
    private final AdminFolderRepository folderRepository;
    private final AdminFileRepository fileRepository;

    public AdminStorageService(
            AdminFolderRepository folderRepository,
            AdminFileRepository fileRepository,
            @Value("${app.storage.admin.max-bytes:0}") long maxStorageBytes,
            @Value("${app.storage.admin.allowed-extensions:pdf,doc,docx,zip}") String allowedExtensionsStr) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.maxStorageBytes = maxStorageBytes;
        this.allowedExtensions = Arrays.stream(allowedExtensionsStr.split(","))
                .map(ext -> ext.trim().toLowerCase())
                .collect(Collectors.toSet());
    }

    // ==================== File Type & Quota Validation ====================

    /**
     * Checks if admin storage has unlimited quota (maxStorageBytes <= 0).
     */
    public boolean isUnlimited() {
        return maxStorageBytes <= 0;
    }

    /**
     * Validates that the filename has an allowed document extension.
     * Throws IllegalArgumentException if the file type is not permitted.
     */
    public void validateFileType(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("ชื่อไฟล์ไม่ถูกต้อง");
        }

        String ext = extractExtension(filename);
        if (ext.isEmpty() || !allowedExtensions.contains(ext)) {
            String allowed = String.join(", ", allowedExtensions.stream().sorted().map(e -> "." + e).toList());
            throw new IllegalArgumentException(
                    "ไม่อนุญาตให้อัปโหลดไฟล์ประเภท ." + (ext.isEmpty() ? "(ไม่มีนามสกุล)" : ext)
                    + " — อนุญาตเฉพาะไฟล์เอกสาร: " + allowed);
        }
    }

    /**
     * Validates storage quota — bypassed if storage is unlimited.
     */
    public void validateStorageQuota(long incomingBytes) {
        if (isUnlimited()) {
            return; // Unlimited for admin
        }
        long currentUsage = getTotalSize();
        if (currentUsage + incomingBytes > maxStorageBytes) {
            throw new IllegalStateException(
                    "พื้นที่เก็บข้อมูลของผู้ดูแลระบบเต็ม (ใช้ไป " + formatSize(currentUsage)
                    + " / " + formatSize(maxStorageBytes)
                    + ") ไม่สามารถอัปโหลดไฟล์ขนาด " + formatSize(incomingBytes) + " ได้");
        }
    }

    public long getRemainingBytes() {
        if (isUnlimited()) {
            return -1L;
        }
        long used = getTotalSize();
        return Math.max(0, maxStorageBytes - used);
    }

    public long getMaxStorageBytes() {
        return maxStorageBytes;
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    private String extractExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) return "";
        return filename.substring(dotIdx + 1).toLowerCase();
    }

    // ==================== Folder Operations ====================

    public List<AdminFolder> listSubFolders(Long parentId) {
        if (parentId == null) {
            return folderRepository.findByParentIsNullOrderByNameAsc();
        }
        return folderRepository.findByParentIdOrderByNameAsc(parentId);
    }

    public List<AdminFile> listFiles(Long folderId) {
        if (folderId == null) {
            return fileRepository.findByFolderIsNullAndIsDeletedFalseOrderByOriginalFilenameAsc();
        }
        return fileRepository.findByFolderIdAndIsDeletedFalseOrderByOriginalFilenameAsc(folderId);
    }

    public AdminFolder getFolder(Long id) {
        return folderRepository.findById(id).orElse(null);
    }

    public AdminFolder createFolder(String name, Long parentId, String createdBy) {
        AdminFolder folder = new AdminFolder();
        folder.setName(name.trim());
        folder.setCreatedBy(createdBy);

        if (parentId != null) {
            AdminFolder parent = folderRepository.findById(parentId).orElse(null);
            folder.setParent(parent);
        }

        return folderRepository.save(folder);
    }

    public void renameFolder(Long id, String newName) {
        folderRepository.findById(id).ifPresent(f -> {
            f.setName(newName.trim());
            folderRepository.save(f);
        });
    }

    public void deleteFolder(Long id) {
        folderRepository.findById(id).ifPresent(folder -> {
            deleteFolderRecursive(folder);
        });
    }

    private void deleteFolderRecursive(AdminFolder folder) {
        // Delete all files in this folder from disk
        for (AdminFile file : folder.getFiles()) {
            deleteFileFromDisk(file.getStoredFilePath());
        }
        // Recurse into children (JPA cascades will handle DB deletion)
        for (AdminFolder child : folder.getChildren()) {
            deleteFolderRecursive(child);
        }
        folderRepository.delete(folder);
    }

    // ==================== File Operations ====================

    public AdminFile uploadFile(MultipartFile multipartFile, Long folderId, String uploadedBy) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        validateFileType(originalFilename);
        validateStorageQuota(multipartFile.getSize());

        Path storageDir = Path.of(STORAGE_ROOT);
        Files.createDirectories(storageDir);

        // Generate unique stored filename
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String storedName = UUID.randomUUID() + ext;
        Path targetPath = storageDir.resolve(storedName);

        Files.copy(multipartFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        AdminFile file = new AdminFile();
        file.setOriginalFilename(originalFilename != null ? originalFilename : "unnamed");
        file.setStoredFilePath(targetPath.toString());
        file.setFileSize(multipartFile.getSize());
        file.setContentType(multipartFile.getContentType());
        file.setCreatedBy(uploadedBy);

        if (folderId != null) {
            AdminFolder folder = folderRepository.findById(folderId).orElse(null);
            file.setFolder(folder);
        }

        return fileRepository.save(file);
    }

    /**
     * Save a file that was already assembled from chunks (used by ChunkedUploadController).
     */
    public AdminFile saveUploadedFile(String filename, String storedPath, long fileSize,
                                       String contentType, Long folderId, String uploadedBy) {
        validateFileType(filename);
        validateStorageQuota(fileSize);

        AdminFile file = new AdminFile();
        file.setOriginalFilename(filename);
        file.setStoredFilePath(storedPath);
        file.setFileSize(fileSize);
        file.setContentType(contentType);
        file.setCreatedBy(uploadedBy);
        if (folderId != null) {
            AdminFolder folder = folderRepository.findById(folderId).orElse(null);
            file.setFolder(folder);
        }
        return fileRepository.save(file);
    }

    public AdminFile getFile(Long id) {
        return fileRepository.findById(id).orElse(null);
    }

    public void renameFile(Long id, String newName) {
        fileRepository.findById(id).ifPresent(f -> {
            f.setOriginalFilename(newName.trim());
            fileRepository.save(f);
        });
    }

    public void softDeleteFile(Long id) {
        fileRepository.findById(id).ifPresent(f -> {
            f.setIsDeleted(true);
            f.setDeletedAt(LocalDateTime.now());
            fileRepository.save(f);
        });
    }

    public void restoreFile(Long id) {
        fileRepository.findById(id).ifPresent(f -> {
            f.setIsDeleted(false);
            f.setDeletedAt(null);
            fileRepository.save(f);
        });
    }

    public void permanentDeleteFile(Long id) {
        fileRepository.findById(id).ifPresent(f -> {
            deleteFileFromDisk(f.getStoredFilePath());
            fileRepository.delete(f);
        });
    }

    public void moveFile(Long fileId, Long targetFolderId) {
        fileRepository.findById(fileId).ifPresent(f -> {
            if (targetFolderId == null) {
                f.setFolder(null);
            } else {
                AdminFolder folder = folderRepository.findById(targetFolderId).orElse(null);
                f.setFolder(folder);
            }
            fileRepository.save(f);
        });
    }

    // ==================== Trash Operations ====================

    public List<AdminFile> getTrashFiles() {
        return fileRepository.findByIsDeletedTrue();
    }

    public long getTrashCount() {
        return fileRepository.countByIsDeletedTrue();
    }

    public void emptyTrash() {
        List<AdminFile> trashed = fileRepository.findByIsDeletedTrue();
        for (AdminFile f : trashed) {
            deleteFileFromDisk(f.getStoredFilePath());
            fileRepository.delete(f);
        }
    }

    /**
     * Purges trashed files that were deleted more than the specified days ago.
     */
    @org.springframework.transaction.annotation.Transactional
    public int purgeOldTrash(int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        List<AdminFile> oldTrash = fileRepository.findByIsDeletedTrueAndDeletedAtBefore(cutoff);
        int count = 0;
        for (AdminFile f : oldTrash) {
            deleteFileFromDisk(f.getStoredFilePath());
            fileRepository.delete(f);
            count++;
        }
        return count;
    }

    // ==================== Breadcrumb ====================

    public List<AdminFolder> getBreadcrumb(Long folderId) {
        List<AdminFolder> breadcrumb = new ArrayList<>();
        AdminFolder current = folderId != null ? folderRepository.findById(folderId).orElse(null) : null;
        while (current != null) {
            breadcrumb.add(0, current);
            current = current.getParent();
        }
        return breadcrumb;
    }

    // ==================== Stats ====================

    public long getTotalFileCount() {
        return fileRepository.findByIsDeletedFalse().size();
    }

    public long getTotalSize() {
        return fileRepository.findByIsDeletedFalse().stream()
                .mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0)
                .sum();
    }

    public long getTotalFolderCount() {
        return folderRepository.count();
    }

    // ==================== Helpers ====================

    private void deleteFileFromDisk(String filePath) {
        if (filePath == null) return;
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            // Log but don't fail
        }
    }

    public String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
