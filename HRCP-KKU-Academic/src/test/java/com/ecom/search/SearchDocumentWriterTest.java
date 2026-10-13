package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.search.index.SearchDocumentWriter;
import com.ecom.search.model.ExtractionState;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;
import com.ecom.search.model.SearchVisibility;
import com.ecom.search.repository.SearchDocumentRepository;

/**
 * What a rewrite of an existing row does to the text the extraction worker read.
 *
 * <p>The factory never has a file's text — only the worker does, and it writes
 * straight into the row. So every rebuild of a file-backed row arrives with an
 * empty body, and copying that over would make a renamed file unsearchable by
 * content until nothing re-queued it, which is never.
 */
class SearchDocumentWriterTest {

    private final SearchDocumentRepository index = mock(SearchDocumentRepository.class);
    private final SearchDocumentWriter writer = new SearchDocumentWriter(index);

    private SearchDocument built(String title, String path) {
        SearchDocument d = new SearchDocument();
        d.setEntityType(SearchEntityType.ADMIN_FILE);
        d.setEntityId(1L);
        d.setDocPart("MAIN");
        d.setTitle(title);
        d.setCategory("ไฟล์ระบบ");
        d.setUrl("/admin/file-manager#row-1");
        d.setVisibility(SearchVisibility.ADMIN);
        d.setFilePath(path);
        d.setExtractionState(path == null ? ExtractionState.NONE : ExtractionState.PENDING);
        return d;
    }

    private SearchDocument extractedRow(String path) {
        SearchDocument row = built("old-name.pdf", path);
        row.setBody("ข้อความที่สกัดจากไฟล์");
        row.setExtractionState(ExtractionState.DONE);
        row.setContentHash("stale");
        when(index.findByEntityTypeAndEntityIdAndDocPart(SearchEntityType.ADMIN_FILE, 1L, "MAIN"))
                .thenReturn(Optional.of(row));
        return row;
    }

    @Test
    @DisplayName("เปลี่ยนชื่อไฟล์แล้ว ข้อความที่สกัดไว้ยังอยู่")
    void renameKeepsExtractedText() {
        SearchDocument row = extractedRow("uploads/admin-storage/a.pdf");

        writer.upsert(built("new-name.pdf", "uploads/admin-storage/a.pdf"));

        assertThat(row.getTitle()).isEqualTo("new-name.pdf");
        assertThat(row.getBody()).isEqualTo("ข้อความที่สกัดจากไฟล์");
        assertThat(row.getExtractionState()).isEqualTo(ExtractionState.DONE);
    }

    @Test
    @DisplayName("ชี้ไปไฟล์ใหม่ ต้องเข้าคิวสกัดใหม่ และทิ้งข้อความของไฟล์เก่า")
    void newFileIsQueuedAgain() {
        SearchDocument row = extractedRow("uploads/admin-storage/a.pdf");

        writer.upsert(built("a.pdf", "uploads/admin-storage/b.pdf"));

        assertThat(row.getFilePath()).isEqualTo("uploads/admin-storage/b.pdf");
        assertThat(row.getExtractionState()).isEqualTo(ExtractionState.PENDING);
        assertThat(row.getBody()).isNull();
    }

    @Test
    @DisplayName("แถวที่ยังไม่ได้สกัด รับ body ใหม่ตามปกติ")
    void unextractedRowTakesTheNewBody() {
        SearchDocument row = built("note", null);
        row.setBody("เก่า");
        row.setContentHash("stale");
        when(index.findByEntityTypeAndEntityIdAndDocPart(SearchEntityType.ADMIN_FILE, 1L, "MAIN"))
                .thenReturn(Optional.of(row));
        SearchDocument rebuilt = built("note", null);
        rebuilt.setBody("ใหม่");

        writer.upsert(rebuilt);

        assertThat(row.getBody()).isEqualTo("ใหม่");
    }
}
