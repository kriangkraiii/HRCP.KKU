package com.ecom.academic.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ecom.academic.dto.FileItemDTO;
import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.repository.AcademicAttachmentRepository;
import com.ecom.academic.repository.AcademicDocumentRepository;

@Service
public class FileManagementService {

    private final AcademicDocumentRepository documentRepository;
    private final AcademicAttachmentRepository attachmentRepository;

    public FileManagementService(AcademicDocumentRepository documentRepository,
            AcademicAttachmentRepository attachmentRepository) {
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public List<FileItemDTO> getAllFiles() {
        List<FileItemDTO> files = new ArrayList<>();

        List<AcademicDocument> docs = documentRepository.findByIsDeletedFalseOrIsDeletedIsNull();
        for (AcademicDocument doc : docs) {
            if (doc.getGeneratedFilePath() == null)
                continue;
            files.add(toDTO(doc));
        }

        List<AcademicAttachment> attachments = attachmentRepository.findByIsDeletedFalseOrIsDeletedIsNull();
        for (AcademicAttachment att : attachments) {
            files.add(toDTO(att));
        }

        files.sort(Comparator.comparing(FileItemDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return files;
    }

    public List<FileItemDTO> getTrashFiles() {
        List<FileItemDTO> files = new ArrayList<>();

        List<AcademicDocument> docs = documentRepository.findByIsDeletedTrue();
        for (AcademicDocument doc : docs) {
            if (doc.getGeneratedFilePath() == null)
                continue;
            files.add(toDTO(doc));
        }

        List<AcademicAttachment> attachments = attachmentRepository.findByIsDeletedTrue();
        for (AcademicAttachment att : attachments) {
            files.add(toDTO(att));
        }

        files.sort(Comparator.comparing(FileItemDTO::getDeletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return files;
    }

    public void softDelete(String type, Long id) {
        if ("doc".equals(type)) {
            documentRepository.findById(id).ifPresent(doc -> {
                doc.setIsDeleted(true);
                doc.setDeletedAt(LocalDateTime.now());
                documentRepository.save(doc);
            });
        } else if ("att".equals(type)) {
            attachmentRepository.findById(id).ifPresent(att -> {
                att.setIsDeleted(true);
                att.setDeletedAt(LocalDateTime.now());
                attachmentRepository.save(att);
            });
        }
    }

    public void restore(String type, Long id) {
        if ("doc".equals(type)) {
            documentRepository.findById(id).ifPresent(doc -> {
                doc.setIsDeleted(false);
                doc.setDeletedAt(null);
                documentRepository.save(doc);
            });
        } else if ("att".equals(type)) {
            attachmentRepository.findById(id).ifPresent(att -> {
                att.setIsDeleted(false);
                att.setDeletedAt(null);
                attachmentRepository.save(att);
            });
        }
    }

    public void permanentDelete(String type, Long id) {
        if ("doc".equals(type)) {
            documentRepository.findById(id).ifPresent(doc -> {
                deleteFileFromDisk(doc.getGeneratedFilePath());
                documentRepository.delete(doc);
            });
        } else if ("att".equals(type)) {
            attachmentRepository.findById(id).ifPresent(att -> {
                deleteFileFromDisk(att.getStoredFilePath());
                attachmentRepository.delete(att);
            });
        }
    }

    public void emptyTrash() {
        List<AcademicDocument> trashedDocs = documentRepository.findByIsDeletedTrue();
        for (AcademicDocument doc : trashedDocs) {
            deleteFileFromDisk(doc.getGeneratedFilePath());
            documentRepository.delete(doc);
        }

        List<AcademicAttachment> trashedAtts = attachmentRepository.findByIsDeletedTrue();
        for (AcademicAttachment att : trashedAtts) {
            deleteFileFromDisk(att.getStoredFilePath());
            attachmentRepository.delete(att);
        }
    }

    public FileItemDTO getFileById(String type, Long id) {
        if ("doc".equals(type)) {
            return documentRepository.findById(id).map(this::toDTO).orElse(null);
        } else if ("att".equals(type)) {
            return attachmentRepository.findById(id).map(this::toDTO).orElse(null);
        }
        return null;
    }

    public long getTrashCount() {
        return documentRepository.findByIsDeletedTrue().size()
                + attachmentRepository.findByIsDeletedTrue().size();
    }

    private void deleteFileFromDisk(String filePath) {
        if (filePath == null)
            return;
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            // Log but don't fail
        }
    }

    private FileItemDTO toDTO(AcademicDocument doc) {
        FileItemDTO dto = new FileItemDTO();
        dto.setFileId("doc-" + doc.getId());
        dto.setFileName(extractFileName(doc.getGeneratedFilePath()));
        dto.setFileExtension(extractExtension(doc.getGeneratedFilePath()));
        dto.setFileSize(getFileSize(doc.getGeneratedFilePath()));
        dto.setSourceType("DOCUMENT");
        dto.setFilePath(doc.getGeneratedFilePath());
        dto.setDocumentLabel(
                doc.getDocumentLabel() != null ? doc.getDocumentLabel() : "เอกสารประเภท " + doc.getDocumentType());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setIsDeleted(doc.getIsDeleted());
        dto.setDeletedAt(doc.getDeletedAt());

        if (doc.getRequest() != null) {
            populateRequestInfo(dto, doc.getRequest());
        }
        return dto;
    }

    private FileItemDTO toDTO(AcademicAttachment att) {
        FileItemDTO dto = new FileItemDTO();
        dto.setFileId("att-" + att.getId());
        dto.setFileName(att.getOriginalFilename());
        dto.setFileExtension(extractExtension(att.getOriginalFilename()));
        dto.setFileSize(att.getFileSize());
        dto.setSourceType("ATTACHMENT");
        dto.setFilePath(att.getStoredFilePath());
        dto.setDocumentLabel("ไฟล์แนบ");
        dto.setCreatedAt(att.getUploadedAt());
        dto.setIsDeleted(att.getIsDeleted());
        dto.setDeletedAt(att.getDeletedAt());

        if (att.getRequest() != null) {
            populateRequestInfo(dto, att.getRequest());
        }
        return dto;
    }

    private void populateRequestInfo(FileItemDTO dto, com.ecom.academic.model.AcademicRequest request) {
        dto.setRequestId(request.getId());
        dto.setRequestCode(request.getRequestCode());

        if (request.getCurrentStatus() != null) {
            dto.setRequestStatusLabel(request.getCurrentStatus().getThaiLabel());
            dto.setRequestActive(!request.getCurrentStatus().isTerminal());
        }

        if (request.getApplicant() != null) {
            dto.setApplicantName(request.getApplicant().getName());
            dto.setApplicantEmail(request.getApplicant().getEmail());
        }
    }

    private String extractFileName(String path) {
        if (path == null)
            return "unknown";
        return Path.of(path).getFileName().toString();
    }

    private String extractExtension(String filename) {
        if (filename == null)
            return "";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toUpperCase() : "";
    }

    private Long getFileSize(String filePath) {
        if (filePath == null)
            return 0L;
        try {
            Path path = Path.of(filePath);
            if (Files.exists(path))
                return Files.size(path);
        } catch (IOException e) {
            // ignore
        }
        return 0L;
    }
}
