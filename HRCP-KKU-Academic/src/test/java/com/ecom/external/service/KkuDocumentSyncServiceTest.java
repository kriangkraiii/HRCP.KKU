package com.ecom.external.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ecom.external.model.FsSyncState;
import com.ecom.external.model.KkuRegulationDoc;
import com.ecom.external.repository.FsSyncStateRepository;
import com.ecom.external.repository.KkuRegulationDocRepository;

class KkuDocumentSyncServiceTest {

    private KkuDocumentParser parser;
    private KkuRegulationDocRepository docRepo;
    private FsSyncStateRepository syncStateRepo;
    private KkuDocumentSyncService syncService;

    @BeforeEach
    void setUp() {
        parser = mock(KkuDocumentParser.class);
        docRepo = mock(KkuRegulationDocRepository.class);
        syncStateRepo = mock(FsSyncStateRepository.class);
        when(syncStateRepo.findById(KkuDocumentSyncService.SYNC_TYPE)).thenReturn(Optional.of(new FsSyncState(KkuDocumentSyncService.SYNC_TYPE)));

        syncService = new KkuDocumentSyncService(parser, docRepo, syncStateRepo);
    }

    @Test
    void getGroupedDocuments_groupsByCategoryCorrectly() {
        KkuRegulationDoc doc1 = new KkuRegulationDoc("ข้อบังคับ", "ข้อบังคับ 2565", "http://example.com/1.pdf", "1.pdf", 1);
        KkuRegulationDoc doc2 = new KkuRegulationDoc("ข้อบังคับ", "ข้อบังคับ 2566", "http://example.com/2.pdf", "2.pdf", 2);
        KkuRegulationDoc doc3 = new KkuRegulationDoc("ประกาศ", "ประกาศ 2566", "http://example.com/3.pdf", "3.pdf", 3);

        when(docRepo.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(doc1, doc2, doc3));

        List<KkuDocumentSyncService.CategoryGroup> groups = syncService.getGroupedDocuments();

        assertNotNull(groups);
        assertEquals(2, groups.size());

        assertEquals("ข้อบังคับ", groups.get(0).getTitle());
        assertEquals(2, groups.get(0).getDocs().size());

        assertEquals("ประกาศ", groups.get(1).getTitle());
        assertEquals(1, groups.get(1).getDocs().size());
    }

    @Test
    void fixStaleIsNewFlags_correctsErroneouslyMarkedNewDocs() {
        KkuRegulationDoc oldDoc1 = new KkuRegulationDoc("ข้อบังคับ", "ข้อบังคับ พ.ศ. 2565", "url1", "key1", 1);
        oldDoc1.setPublishedYear("2565");
        oldDoc1.setIsNew(true); // Erroneously true in DB

        KkuRegulationDoc oldDoc2 = new KkuRegulationDoc("ข้อบังคับ", "ข้อบังคับ พ.ศ. 2560", "url2", "key2", 2);
        oldDoc2.setPublishedYear("2560");
        oldDoc2.setIsNew(true); // Erroneously true in DB

        KkuRegulationDoc newDoc = new KkuRegulationDoc("ข้อบังคับ", "ข้อบังคับ พ.ศ. 2569", "url3", "key3", 3);
        newDoc.setPublishedYear("2569");
        newDoc.setIsNew(true); // Should stay true

        when(docRepo.findAll()).thenReturn(List.of(oldDoc1, oldDoc2, newDoc));

        syncService.fixStaleIsNewFlags();

        assertFalse(oldDoc1.getIsNew());
        assertFalse(oldDoc2.getIsNew());
        assertTrue(newDoc.getIsNew());

        verify(docRepo).save(oldDoc1);
        verify(docRepo).save(oldDoc2);
    }

    @Test
    void isActuallyNew_identifiesNewDocsAccurately() {
        assertTrue(KkuDocumentSyncService.isActuallyNew("ข้อบังคับ พ.ศ. 2569", "2569"));
        assertTrue(KkuDocumentSyncService.isActuallyNew("ประกาศฯ ( ใหม่ )", "2566"));
        assertTrue(KkuDocumentSyncService.isActuallyNew("ประกาศฯ 🆕", "2565"));
        assertTrue(KkuDocumentSyncService.isActuallyNew("ประกาศฯ NEW", null));

        assertFalse(KkuDocumentSyncService.isActuallyNew("ข้อบังคับ พ.ศ. 2565", "2565"));
        assertFalse(KkuDocumentSyncService.isActuallyNew("ข้อบังคับ พ.ศ. 2566", "2566"));
        assertFalse(KkuDocumentSyncService.isActuallyNew("ข้อบังคับ พ.ศ. 2560", "2560"));
        assertFalse(KkuDocumentSyncService.isActuallyNew("ลักษณะการมีส่วนร่วมในผลงานทางวิชาการทั่วไป", null));
    }
}
