package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecom.academic.dto.FileItemDTO;
import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.repository.AcademicAttachmentRepository;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.model.UserDtls;

@ExtendWith(MockitoExtension.class)
class FileManagementServiceTest {

    @Mock
    private AcademicDocumentRepository documentRepository;

    @Mock
    private AcademicAttachmentRepository attachmentRepository;

    @InjectMocks
    private FileManagementService fileManagementService;

    private AcademicDocument sampleDoc;
    private AcademicAttachment sampleAtt;
    private AcademicRequest sampleRequest;
    private UserDtls sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new UserDtls();
        sampleUser.setName("Test User");
        sampleUser.setEmail("test@example.com");

        sampleRequest = new AcademicRequest();
        sampleRequest.setId(1L);
        sampleRequest.setApplicant(sampleUser);

        sampleDoc = new AcademicDocument();
        sampleDoc.setId(10L);
        sampleDoc.setRequest(sampleRequest);
        sampleDoc.setDocumentType(1);
        sampleDoc.setDocumentLabel("คำร้อง ก.พ.อ.03");
        sampleDoc.setGeneratedFilePath("uploads/academic/1/doc_1.docx");
        sampleDoc.setIsDeleted(false);
        sampleDoc.setCreatedAt(LocalDateTime.now());

        sampleAtt = new AcademicAttachment();
        sampleAtt.setId(20L);
        sampleAtt.setRequest(sampleRequest);
        sampleAtt.setOriginalFilename("attachment.pdf");
        sampleAtt.setStoredFilePath("uploads/academic/1/attachment.pdf");
        sampleAtt.setFileSize(1024L);
        sampleAtt.setFileType("application/pdf");
        sampleAtt.setIsDeleted(false);
        sampleAtt.setUploadedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("getAllFiles")
    class GetAllFilesTests {

        @Test
        @DisplayName("should return combined documents and attachments")
        void shouldReturnCombinedFiles() {
            when(documentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of(sampleDoc));
            when(attachmentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of(sampleAtt));

            List<FileItemDTO> files = fileManagementService.getAllFiles();

            assertThat(files).hasSize(2);
            assertThat(files).extracting(FileItemDTO::getSourceType)
                    .containsExactlyInAnyOrder("DOCUMENT", "ATTACHMENT");
        }

        @Test
        @DisplayName("should skip documents without generated file path")
        void shouldSkipDocsWithoutPath() {
            AcademicDocument noPathDoc = new AcademicDocument();
            noPathDoc.setId(99L);
            noPathDoc.setRequest(sampleRequest);
            noPathDoc.setGeneratedFilePath(null);

            when(documentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of(noPathDoc));
            when(attachmentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of());

            List<FileItemDTO> files = fileManagementService.getAllFiles();

            assertThat(files).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when no files exist")
        void shouldReturnEmptyWhenNoFiles() {
            when(documentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of());
            when(attachmentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of());

            List<FileItemDTO> files = fileManagementService.getAllFiles();

            assertThat(files).isEmpty();
        }

        @Test
        @DisplayName("should map document fields to DTO correctly")
        void shouldMapDocumentFieldsCorrectly() {
            when(documentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of(sampleDoc));
            when(attachmentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of());

            List<FileItemDTO> files = fileManagementService.getAllFiles();

            assertThat(files).hasSize(1);
            FileItemDTO dto = files.get(0);
            assertThat(dto.getFileId()).isEqualTo("doc-10");
            assertThat(dto.getFileName()).isEqualTo("doc_1.docx");
            assertThat(dto.getFileExtension()).isEqualTo("DOCX");
            assertThat(dto.getSourceType()).isEqualTo("DOCUMENT");
            assertThat(dto.getApplicantName()).isEqualTo("Test User");
            assertThat(dto.getApplicantEmail()).isEqualTo("test@example.com");
            assertThat(dto.getRequestId()).isEqualTo(1L);
            assertThat(dto.getDocumentLabel()).isEqualTo("คำร้อง ก.พ.อ.03");
        }

        @Test
        @DisplayName("should map attachment fields to DTO correctly")
        void shouldMapAttachmentFieldsCorrectly() {
            when(documentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of());
            when(attachmentRepository.findByIsDeletedFalseOrIsDeletedIsNull())
                    .thenReturn(List.of(sampleAtt));

            List<FileItemDTO> files = fileManagementService.getAllFiles();

            assertThat(files).hasSize(1);
            FileItemDTO dto = files.get(0);
            assertThat(dto.getFileId()).isEqualTo("att-20");
            assertThat(dto.getFileName()).isEqualTo("attachment.pdf");
            assertThat(dto.getFileExtension()).isEqualTo("PDF");
            assertThat(dto.getSourceType()).isEqualTo("ATTACHMENT");
            assertThat(dto.getFileSize()).isEqualTo(1024L);
            assertThat(dto.getDocumentLabel()).isEqualTo("ไฟล์แนบ");
        }
    }

    @Nested
    @DisplayName("getTrashFiles")
    class GetTrashFilesTests {

        @Test
        @DisplayName("should return only deleted files")
        void shouldReturnOnlyDeletedFiles() {
            sampleDoc.setIsDeleted(true);
            sampleDoc.setDeletedAt(LocalDateTime.now());

            when(documentRepository.findByIsDeletedTrue())
                    .thenReturn(List.of(sampleDoc));
            when(attachmentRepository.findByIsDeletedTrue())
                    .thenReturn(List.of());

            List<FileItemDTO> trashFiles = fileManagementService.getTrashFiles();

            assertThat(trashFiles).hasSize(1);
            assertThat(trashFiles.get(0).getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("should return empty when trash is empty")
        void shouldReturnEmptyTrash() {
            when(documentRepository.findByIsDeletedTrue()).thenReturn(List.of());
            when(attachmentRepository.findByIsDeletedTrue()).thenReturn(List.of());

            List<FileItemDTO> trashFiles = fileManagementService.getTrashFiles();

            assertThat(trashFiles).isEmpty();
        }
    }

    @Nested
    @DisplayName("softDelete")
    class SoftDeleteTests {

        @Test
        @DisplayName("should mark document as deleted with timestamp")
        void shouldSoftDeleteDocument() {
            when(documentRepository.findById(10L)).thenReturn(Optional.of(sampleDoc));

            fileManagementService.softDelete("doc", 10L);

            ArgumentCaptor<AcademicDocument> captor = ArgumentCaptor.forClass(AcademicDocument.class);
            verify(documentRepository).save(captor.capture());

            AcademicDocument saved = captor.getValue();
            assertThat(saved.getIsDeleted()).isTrue();
            assertThat(saved.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should mark attachment as deleted with timestamp")
        void shouldSoftDeleteAttachment() {
            when(attachmentRepository.findById(20L)).thenReturn(Optional.of(sampleAtt));

            fileManagementService.softDelete("att", 20L);

            ArgumentCaptor<AcademicAttachment> captor = ArgumentCaptor.forClass(AcademicAttachment.class);
            verify(attachmentRepository).save(captor.capture());

            AcademicAttachment saved = captor.getValue();
            assertThat(saved.getIsDeleted()).isTrue();
            assertThat(saved.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should handle non-existent document gracefully")
        void shouldHandleNonExistentDocument() {
            when(documentRepository.findById(999L)).thenReturn(Optional.empty());

            fileManagementService.softDelete("doc", 999L);

            verify(documentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("restore")
    class RestoreTests {

        @Test
        @DisplayName("should restore document by clearing deleted flag")
        void shouldRestoreDocument() {
            sampleDoc.setIsDeleted(true);
            sampleDoc.setDeletedAt(LocalDateTime.now());
            when(documentRepository.findById(10L)).thenReturn(Optional.of(sampleDoc));

            fileManagementService.restore("doc", 10L);

            ArgumentCaptor<AcademicDocument> captor = ArgumentCaptor.forClass(AcademicDocument.class);
            verify(documentRepository).save(captor.capture());

            AcademicDocument saved = captor.getValue();
            assertThat(saved.getIsDeleted()).isFalse();
            assertThat(saved.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("should restore attachment by clearing deleted flag")
        void shouldRestoreAttachment() {
            sampleAtt.setIsDeleted(true);
            sampleAtt.setDeletedAt(LocalDateTime.now());
            when(attachmentRepository.findById(20L)).thenReturn(Optional.of(sampleAtt));

            fileManagementService.restore("att", 20L);

            ArgumentCaptor<AcademicAttachment> captor = ArgumentCaptor.forClass(AcademicAttachment.class);
            verify(attachmentRepository).save(captor.capture());

            AcademicAttachment saved = captor.getValue();
            assertThat(saved.getIsDeleted()).isFalse();
            assertThat(saved.getDeletedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("permanentDelete")
    class PermanentDeleteTests {

        @Test
        @DisplayName("should delete document record from database")
        void shouldPermanentDeleteDocument() {
            when(documentRepository.findById(10L)).thenReturn(Optional.of(sampleDoc));

            fileManagementService.permanentDelete("doc", 10L);

            verify(documentRepository).delete(sampleDoc);
        }

        @Test
        @DisplayName("should delete attachment record from database")
        void shouldPermanentDeleteAttachment() {
            when(attachmentRepository.findById(20L)).thenReturn(Optional.of(sampleAtt));

            fileManagementService.permanentDelete("att", 20L);

            verify(attachmentRepository).delete(sampleAtt);
        }
    }

    @Nested
    @DisplayName("emptyTrash")
    class EmptyTrashTests {

        @Test
        @DisplayName("should delete all trashed documents and attachments")
        void shouldEmptyAllTrash() {
            AcademicDocument trashedDoc = new AcademicDocument();
            trashedDoc.setId(100L);
            trashedDoc.setIsDeleted(true);
            trashedDoc.setGeneratedFilePath("uploads/academic/1/doc_trash.docx");

            AcademicAttachment trashedAtt = new AcademicAttachment();
            trashedAtt.setId(200L);
            trashedAtt.setIsDeleted(true);
            trashedAtt.setStoredFilePath("uploads/academic/1/att_trash.pdf");

            when(documentRepository.findByIsDeletedTrue()).thenReturn(List.of(trashedDoc));
            when(attachmentRepository.findByIsDeletedTrue()).thenReturn(List.of(trashedAtt));

            fileManagementService.emptyTrash();

            verify(documentRepository).delete(trashedDoc);
            verify(attachmentRepository).delete(trashedAtt);
        }

        @Test
        @DisplayName("should handle empty trash gracefully")
        void shouldHandleEmptyTrash() {
            when(documentRepository.findByIsDeletedTrue()).thenReturn(List.of());
            when(attachmentRepository.findByIsDeletedTrue()).thenReturn(List.of());

            fileManagementService.emptyTrash();

            verify(documentRepository, never()).delete(any(AcademicDocument.class));
            verify(attachmentRepository, never()).delete(any(AcademicAttachment.class));
        }
    }

    @Nested
    @DisplayName("getTrashCount")
    class GetTrashCountTests {

        @Test
        @DisplayName("should return total count of trashed files")
        void shouldReturnTrashCount() {
            when(documentRepository.findByIsDeletedTrue()).thenReturn(List.of(sampleDoc));
            when(attachmentRepository.findByIsDeletedTrue()).thenReturn(List.of(sampleAtt));

            long count = fileManagementService.getTrashCount();

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("FileItemDTO")
    class FileItemDTOTests {

        @Test
        @DisplayName("should format file size correctly")
        void shouldFormatFileSizeCorrectly() {
            FileItemDTO dto = new FileItemDTO();

            dto.setFileSize(null);
            assertThat(dto.getFormattedSize()).isEqualTo("-");

            dto.setFileSize(0L);
            assertThat(dto.getFormattedSize()).isEqualTo("-");

            dto.setFileSize(500L);
            assertThat(dto.getFormattedSize()).isEqualTo("500 B");

            dto.setFileSize(2048L);
            assertThat(dto.getFormattedSize()).isEqualTo("2.0 KB");

            dto.setFileSize(1048576L);
            assertThat(dto.getFormattedSize()).isEqualTo("1.0 MB");
        }
    }
}
