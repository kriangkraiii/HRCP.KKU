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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.model.UserFile;
import com.ecom.academic.model.UserFolder;
import com.ecom.academic.repository.UserFileRepository;
import com.ecom.academic.repository.UserFolderRepository;

@Service
public class UserStorageService {

    private static final String STORAGE_ROOT = "uploads/user-storage";

    private final long maxStorageBytes;
    private final Set<String> allowedExtensions;
    private final UserFolderRepository folderRepo;
    private final UserFileRepository fileRepo;

    public UserStorageService(
            UserFolderRepository folderRepo,
            UserFileRepository fileRepo,
            @Value("${app.storage.user.max-bytes:536870912}") long maxStorageBytes,
            @Value("${app.storage.user.allowed-extensions:pdf,doc,docx,zip}") String allowedExtensionsStr) {
        this.folderRepo = folderRepo;
        this.fileRepo = fileRepo;
        this.maxStorageBytes = maxStorageBytes;
        this.allowedExtensions = Arrays.stream(allowedExtensionsStr.split(","))
                .map(ext -> ext.trim().toLowerCase())
                .collect(Collectors.toSet());
    }

    // ==================== Folder ====================

    public List<UserFolder> listSubFolders(Integer ownerId, Long parentId) {
        if (parentId == null) return folderRepo.findByOwnerIdAndParentIsNullOrderByNameAsc(ownerId);
        return folderRepo.findByOwnerIdAndParentIdOrderByNameAsc(ownerId, parentId);
    }

    public UserFolder getFolder(Long id, Integer ownerId) {
        return folderRepo.findById(id).filter(f -> f.getOwnerId().equals(ownerId)).orElse(null);
    }

    @CacheEvict(value = "storageStats", allEntries = true)
    public UserFolder createFolder(String name, Long parentId, Integer ownerId) {
        UserFolder folder = new UserFolder();
        folder.setName(name.trim());
        folder.setOwnerId(ownerId);
        if (parentId != null) {
            UserFolder parent = getFolder(parentId, ownerId);
            folder.setParent(parent);
        }
        return folderRepo.save(folder);
    }

    public void renameFolder(Long id, String newName, Integer ownerId) {
        UserFolder f = getFolder(id, ownerId);
        if (f != null) { f.setName(newName.trim()); folderRepo.save(f); }
    }

    @CacheEvict(value = "storageStats", allEntries = true)
    public void deleteFolder(Long id, Integer ownerId) {
        UserFolder f = getFolder(id, ownerId);
        if (f != null) { deleteFolderRecursive(f); }
    }

    private void deleteFolderRecursive(UserFolder folder) {
        for (UserFile file : folder.getFiles()) deleteFileFromDisk(file.getStoredFilePath());
        for (UserFolder child : folder.getChildren()) deleteFolderRecursive(child);
        folderRepo.delete(folder);
    }

    // ==================== File Type Validation ====================

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
     * Validates storage quota — throws if adding the given size would exceed the limit.
     */
    public void validateStorageQuota(long incomingBytes, Integer ownerId) {
        long currentUsage = getTotalSize(ownerId);
        if (currentUsage + incomingBytes > maxStorageBytes) {
            throw new IllegalStateException(
                    "พื้นที่เก็บข้อมูลเต็ม (ใช้ไป " + formatSize(currentUsage)
                    + " / " + formatSize(maxStorageBytes)
                    + ") ไม่สามารถอัปโหลดไฟล์ขนาด " + formatSize(incomingBytes) + " ได้");
        }
    }

    private String extractExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) return "";
        return filename.substring(dotIdx + 1).toLowerCase();
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    // ==================== File ====================

    public List<UserFile> listFiles(Integer ownerId, Long folderId) {
        if (folderId == null) return fileRepo.findByOwnerIdAndFolderIsNullAndIsDeletedFalseOrderByOriginalFilenameAsc(ownerId);
        return fileRepo.findByOwnerIdAndFolderIdAndIsDeletedFalseOrderByOriginalFilenameAsc(ownerId, folderId);
    }

    @CacheEvict(value = "storageStats", allEntries = true)
    public UserFile uploadFile(MultipartFile multipartFile, Long folderId, Integer ownerId) throws IOException {
        // Validate file type
        validateFileType(multipartFile.getOriginalFilename());

        // Validate storage quota
        validateStorageQuota(multipartFile.getSize(), ownerId);

        Path storageDir = Path.of(STORAGE_ROOT, String.valueOf(ownerId));
        Files.createDirectories(storageDir);

        String originalFilename = multipartFile.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        Path targetPath = storageDir.resolve(UUID.randomUUID() + ext);
        Files.copy(multipartFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        UserFile file = new UserFile();
        file.setOriginalFilename(originalFilename != null ? originalFilename : "unnamed");
        file.setStoredFilePath(targetPath.toString());
        file.setFileSize(multipartFile.getSize());
        file.setContentType(multipartFile.getContentType());
        file.setOwnerId(ownerId);
        if (folderId != null) file.setFolder(getFolder(folderId, ownerId));
        return fileRepo.save(file);
    }

    /**
     * Save a file that was already assembled from chunks (used by ChunkedUploadController).
     */
    @CacheEvict(value = "storageStats", allEntries = true)
    public UserFile saveUploadedFile(String filename, String storedPath, long fileSize,
                                      String contentType, Long folderId, Integer ownerId) {
        UserFile file = new UserFile();
        file.setOriginalFilename(filename);
        file.setStoredFilePath(storedPath);
        file.setFileSize(fileSize);
        file.setContentType(contentType);
        file.setOwnerId(ownerId);
        if (folderId != null) file.setFolder(getFolder(folderId, ownerId));
        return fileRepo.save(file);
    }

    public UserFile getFile(Long id, Integer ownerId) {
        return fileRepo.findById(id).filter(f -> f.getOwnerId().equals(ownerId)).orElse(null);
    }

    public void renameFile(Long id, String newName, Integer ownerId) {
        UserFile f = getFile(id, ownerId);
        if (f != null) { f.setOriginalFilename(newName.trim()); fileRepo.save(f); }
    }

    @CacheEvict(value = "storageStats", allEntries = true)
    public void softDeleteFile(Long id, Integer ownerId) {
        UserFile f = getFile(id, ownerId);
        if (f != null) { f.setIsDeleted(true); f.setDeletedAt(LocalDateTime.now()); fileRepo.save(f); }
    }

    @CacheEvict(value = "storageStats", allEntries = true)
    public void restoreFile(Long id, Integer ownerId) {
        UserFile f = getFile(id, ownerId);
        if (f != null) { f.setIsDeleted(false); f.setDeletedAt(null); fileRepo.save(f); }
    }

    @CacheEvict(value = "storageStats", allEntries = true)
    public void permanentDeleteFile(Long id, Integer ownerId) {
        UserFile f = getFile(id, ownerId);
        if (f != null) { deleteFileFromDisk(f.getStoredFilePath()); fileRepo.delete(f); }
    }

    // ==================== Trash ====================

    public List<UserFile> getTrashFiles(Integer ownerId) { return fileRepo.findByOwnerIdAndIsDeletedTrue(ownerId); }
    public long getTrashCount(Integer ownerId) { return fileRepo.countByOwnerIdAndIsDeletedTrue(ownerId); }

    public void emptyTrash(Integer ownerId) {
        for (UserFile f : getTrashFiles(ownerId)) { deleteFileFromDisk(f.getStoredFilePath()); fileRepo.delete(f); }
    }

    /**
     * Purges trashed files that were deleted more than the specified days ago.
     */
    @org.springframework.transaction.annotation.Transactional
    public int purgeOldTrash(int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        List<UserFile> oldTrash = fileRepo.findByIsDeletedTrueAndDeletedAtBefore(cutoff);
        int count = 0;
        for (UserFile f : oldTrash) {
            deleteFileFromDisk(f.getStoredFilePath());
            fileRepo.delete(f);
            count++;
        }
        return count;
    }

    // ==================== Breadcrumb & Stats ====================

    public List<UserFolder> getBreadcrumb(Long folderId, Integer ownerId) {
        List<UserFolder> bc = new ArrayList<>();
        UserFolder cur = folderId != null ? getFolder(folderId, ownerId) : null;
        while (cur != null) { bc.add(0, cur); cur = cur.getParent(); }
        return bc;
    }

    @Cacheable(value = "storageStats", key = "'fileCount-' + #ownerId")
    public long getTotalFileCount(Integer ownerId) { return fileRepo.findByOwnerIdAndIsDeletedFalse(ownerId).size(); }

    @Cacheable(value = "storageStats", key = "'totalSize-' + #ownerId")
    public long getTotalSize(Integer ownerId) {
        return fileRepo.findByOwnerIdAndIsDeletedFalse(ownerId).stream()
                .mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0).sum();
    }

    public long getTotalFolderCount(Integer ownerId) {
        return folderRepo.findByOwnerIdAndParentIsNullOrderByNameAsc(ownerId).size()
             + folderRepo.count(); // approximate
    }

    public long getMaxStorageBytes() { return maxStorageBytes; }

    public double getUsagePercentage(Integer ownerId) {
        return Math.min(100.0, (getTotalSize(ownerId) * 100.0) / maxStorageBytes);
    }

    public long getRemainingBytes(Integer ownerId) {
        return Math.max(0, maxStorageBytes - getTotalSize(ownerId));
    }

    public String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void deleteFileFromDisk(String filePath) {
        if (filePath == null) return;
        try { Files.deleteIfExists(Path.of(filePath)); } catch (IOException e) { /* log */ }
    }
}
