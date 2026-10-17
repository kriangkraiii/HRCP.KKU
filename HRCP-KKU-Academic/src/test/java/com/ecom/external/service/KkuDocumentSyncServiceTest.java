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

    /** ดึงจากหน้าของพนักงาน (5546) และข้าราชการ (5532) ในรอบเดียว — หน้าหนึ่งล่มอีกหน้ายังบันทึกได้ */
    @Test
    void syncNow_readsEverySourcePage() {
        KkuRegulationDoc employee = new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "ข้อบังคับ 2569", "u1", "a.pdf", 1);
        KkuRegulationDoc civil = new KkuRegulationDoc("ข้าราชการ · ประกาศ ก.พ.อ.", "ก.พ.อ. 2568", "u2", "b.pdf", 1);
        when(parser.parse("employee-html", null)).thenReturn(List.of(employee));
        when(parser.parse("civil-html", "ข้าราชการ")).thenReturn(List.of(civil));
        when(docRepo.findByFileKey(any())).thenReturn(Optional.empty());

        KkuDocumentSyncService service = new KkuDocumentSyncService(parser, docRepo, syncStateRepo) {
            @Override
            protected String fetchHtml(String url) {
                return url.contains("5546") ? "employee-html" : "civil-html";
            }
        };
        KkuDocumentSyncService.SyncResult result = service.syncNow();

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(2, result.getTotalParsed());
        verify(docRepo).save(employee);
        verify(docRepo).save(civil);
        assertTrue(civil.getDisplayOrder() > employee.getDisplayOrder(), "เอกสารข้าราชการต่อท้ายเอกสารพนักงาน");
    }

    @Test
    void syncNow_keepsWhatOnePageGaveWhenTheOtherFails() {
        KkuRegulationDoc employee = new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "ข้อบังคับ 2569", "u1", "a.pdf", 1);
        when(parser.parse("employee-html", null)).thenReturn(List.of(employee));
        when(docRepo.findByFileKey(any())).thenReturn(Optional.empty());

        KkuDocumentSyncService service = new KkuDocumentSyncService(parser, docRepo, syncStateRepo) {
            @Override
            protected String fetchHtml(String url) throws java.io.IOException {
                if (url.contains("5532")) {
                    throw new java.io.IOException("blocked");
                }
                return "employee-html";
            }
        };
        KkuDocumentSyncService.SyncResult result = service.syncNow();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("5532"), result.getMessage());
        verify(docRepo).save(employee);
    }

    /** หน้าเอกสารของผู้ยื่นแยกแท็บพนักงาน/ข้าราชการ — หมวดในแท็บไม่ต้องมีคำนำหน้า "ข้าราชการ · " ซ้ำ */
    @Test
    void getDocumentsByAudience_splitsEmployeeAndCivilServantTabs() {
        KkuRegulationDoc employee = new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "ข้อบังคับ 2569", "u1", "a.pdf", 1);
        KkuRegulationDoc civil = new KkuRegulationDoc("ข้าราชการ · ประกาศ ก.พ.อ.", "ก.พ.อ. 2568", "u2", "b.pdf", 1001);
        when(docRepo.findByRetiredAtIsNullOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(employee, civil));

        var tabs = syncService.getDocumentsByAudience();

        assertEquals(List.of("พนักงานมหาวิทยาลัย", "ข้าราชการ"), List.copyOf(tabs.keySet()));
        assertEquals("ข้อบังคับมหาวิทยาลัยขอนแก่น", tabs.get("พนักงานมหาวิทยาลัย").get(0).getTitle());
        assertEquals("ประกาศ ก.พ.อ.", tabs.get("ข้าราชการ").get(0).getTitle());
        assertEquals(1, tabs.get("ข้าราชการ").get(0).getDocs().size());
    }

    /** ฐานข้อมูลที่ซิงค์ไว้ก่อนมีหน้าข้าราชการ — เปิดระบบแล้วต้องดึงใหม่เอง ไม่ต้องรอรอบรายเดือน */
    @Test
    void onStartup_syncsWhenCivilServantDocumentsAreMissing() {
        when(docRepo.count()).thenReturn(42L);
        when(docRepo.existsByCategoryStartingWith("ข้าราชการ")).thenReturn(false);
        java.util.List<String> fetched = new java.util.ArrayList<>();
        KkuDocumentSyncService service = new KkuDocumentSyncService(parser, docRepo, syncStateRepo) {
            @Override
            protected String fetchHtml(String url) {
                fetched.add(url);
                return "";
            }
        };

        service.onStartup();

        assertEquals(2, fetched.size());
    }

    @Test
    void getGroupedDocuments_groupsByCategoryCorrectly() {
        KkuRegulationDoc doc1 = new KkuRegulationDoc("ข้อบังคับ", "ข้อบังคับ 2565", "http://example.com/1.pdf", "1.pdf", 1);
        KkuRegulationDoc doc2 = new KkuRegulationDoc("ข้อบังคับ", "ข้อบังคับ 2566", "http://example.com/2.pdf", "2.pdf", 2);
        KkuRegulationDoc doc3 = new KkuRegulationDoc("ประกาศ", "ประกาศ 2566", "http://example.com/3.pdf", "3.pdf", 3);

        when(docRepo.findByRetiredAtIsNullOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(doc1, doc2, doc3));

        List<KkuDocumentSyncService.CategoryGroup> groups = syncService.getGroupedDocuments();

        assertNotNull(groups);
        assertEquals(2, groups.size());

        assertEquals("ข้อบังคับ", groups.get(0).getTitle());
        assertEquals(2, groups.get(0).getDocs().size());

        assertEquals("ประกาศ", groups.get(1).getTitle());
        assertEquals(1, groups.get(1).getDocs().size());
    }

    /** ป้าย "ใหม่" จาก 🆕/NEW/(ใหม่) ไม่อยู่ในชื่อที่บันทึกไว้ — เปิดระบบใหม่ต้องไม่ล้างป้ายทิ้ง */
    @Test
    void onStartup_keepsTheNewBadgeTheLastSyncSet() {
        KkuRegulationDoc marked = new KkuDocumentParser().parse("""
                <div class="fusion_builder_column"><h2>ประกาศมหาวิทยาลัยขอนแก่น</h2>
                <a href="https://hr2.kku.ac.th/wp-content/uploads/a.pdf">ประกาศ เรื่อง หลักเกณฑ์ พ.ศ. 2565 🆕</a></div>
                """).get(0);
        assertTrue(marked.getIsNew());
        when(docRepo.count()).thenReturn(1L);
        when(docRepo.existsByCategoryStartingWith("ข้าราชการ")).thenReturn(true);
        when(docRepo.findAll()).thenReturn(List.of(marked));

        syncService.onStartup();

        assertTrue(marked.getIsNew());
        verify(docRepo, org.mockito.Mockito.never()).save(any());
    }

    // ---- เอกสารที่ HR ถอดออกจากหน้าเว็บ (docs/PLAN-bughunt-decisions.md ข้อ 5) ----

    private static KkuRegulationDoc doc(String key) {
        return new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "เอกสาร " + key, "https://hr2.kku.ac.th/" + key, key, 1);
    }

    /** อ่านได้ทั้งสองหน้า — หน้าข้าราชการว่างไม่ได้ (นับเป็นอ่านไม่สำเร็จ) จึงให้เอกสารหนึ่งเรื่องเสมอ */
    private KkuDocumentSyncService serviceSeeing(List<KkuRegulationDoc> onEmployeePage, KkuRegulationDoc onCivilPage) {
        when(parser.parse("employee-html", null)).thenReturn(onEmployeePage);
        when(parser.parse("civil-html", "ข้าราชการ")).thenReturn(List.of(onCivilPage));
        return new KkuDocumentSyncService(parser, docRepo, syncStateRepo) {
            @Override
            protected String fetchHtml(String url) {
                return url.contains("5546") ? "employee-html" : "civil-html";
            }
        };
    }

    @Test
    void syncNow_retiresWhatHrTookOffThePage() {
        List<KkuRegulationDoc> stored = new java.util.ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            stored.add(doc("d" + i + ".pdf"));
        }
        KkuRegulationDoc civil = doc("c.pdf");
        stored.add(civil);
        // HR ถอด d9 ออก — เหลือ d1..d8 บนหน้าพนักงาน
        List<KkuRegulationDoc> stillThere = stored.subList(0, 8).stream().map(d -> doc(d.getFileKey())).toList();
        for (KkuRegulationDoc d : stored) {
            when(docRepo.findByFileKey(d.getFileKey())).thenReturn(Optional.of(d));
        }
        when(docRepo.findByRetiredAtIsNullOrderByDisplayOrderAscIdAsc()).thenReturn(stored);

        KkuDocumentSyncService.SyncResult result = serviceSeeing(stillThere, doc("c.pdf")).syncNow();

        assertTrue(result.isSuccess(), result.getMessage());
        assertNotNull(stored.get(8).getRetiredAt(), "d9 หายจากหน้าเว็บ HR ต้องถูกถอด");
        assertTrue(stored.subList(0, 8).stream().allMatch(d -> d.getRetiredAt() == null));
        assertTrue(result.getMessage().contains("ถอดออก 1"), result.getMessage());
    }

    @Test
    void syncNow_doesNotRetireWhenOnePageFailed() {
        KkuRegulationDoc civil = doc("c.pdf");
        when(docRepo.findByFileKey(any())).thenReturn(Optional.empty());
        when(parser.parse("employee-html", null)).thenReturn(List.of(doc("d1.pdf")));
        KkuDocumentSyncService service = new KkuDocumentSyncService(parser, docRepo, syncStateRepo) {
            @Override
            protected String fetchHtml(String url) throws java.io.IOException {
                if (url.contains("5532")) {
                    throw new java.io.IOException("blocked");
                }
                return "employee-html";
            }
        };

        service.syncNow();

        assertEquals(null, civil.getRetiredAt(), "หน้าข้าราชการพัง — เอกสารของหน้านั้นต้องไม่ถูกถอด");
        verify(docRepo, org.mockito.Mockito.never()).findByRetiredAtIsNullOrderByDisplayOrderAscIdAsc();
    }

    @Test
    void syncNow_refusesToRetireMostOfTheCatalogueUntilConfirmed() {
        List<KkuRegulationDoc> stored = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            stored.add(doc("d" + i + ".pdf"));
        }
        for (KkuRegulationDoc d : stored) {
            when(docRepo.findByFileKey(d.getFileKey())).thenReturn(Optional.of(d));
        }
        when(docRepo.findByRetiredAtIsNullOrderByDisplayOrderAscIdAsc()).thenReturn(stored);
        // หน้าเว็บเปลี่ยนรูปแบบ parser อ่านได้แค่ 2 เรื่อง — ดูเหมือน 8 จาก 10 เรื่องหายไป
        List<KkuRegulationDoc> parsedOnly = List.of(doc("d1.pdf"));
        KkuDocumentSyncService service = serviceSeeing(parsedOnly, doc("d2.pdf"));

        KkuDocumentSyncService.SyncResult blocked = service.syncNow();

        assertFalse(blocked.isSuccess());
        assertTrue(blocked.getMessage().contains(KkuDocumentSyncService.RETIRE_BLOCKED_MARKER), blocked.getMessage());
        assertTrue(stored.stream().allMatch(d -> d.getRetiredAt() == null), "ยังไม่ยืนยัน — ไม่ถอดสักเรื่อง");

        KkuDocumentSyncService.SyncResult confirmed = service.syncNow(true);

        assertTrue(confirmed.isSuccess(), confirmed.getMessage());
        assertEquals(8, stored.stream().filter(d -> d.getRetiredAt() != null).count());
    }

    @Test
    void syncNow_bringsBackADocumentHrPutBack() {
        KkuRegulationDoc returned = doc("d1.pdf");
        returned.setRetiredAt(java.time.LocalDateTime.now().minusDays(40));
        when(docRepo.findByFileKey("d1.pdf")).thenReturn(Optional.of(returned));
        when(docRepo.findByFileKey("c.pdf")).thenReturn(Optional.empty());
        when(docRepo.findByRetiredAtIsNullOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(returned));

        serviceSeeing(List.of(doc("d1.pdf")), doc("c.pdf")).syncNow();

        assertEquals(null, returned.getRetiredAt());
    }

    /** WordPress เก็บไฟล์ชื่อเดียวกันได้ถ้าอัปโหลดคนละเดือน — เป็นสองเอกสาร ไม่ใช่ไฟล์เดียวที่ซ้ำ */
    @Test
    void syncNow_keepsTwoFilesThatShareOnlyAName() {
        KkuRegulationDoc older = new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "แบบฟอร์ม ก.พ.อ. 03",
                "https://hr2.kku.ac.th/wp-content/uploads/2023/05/form.pdf", "form.pdf", 1);
        KkuRegulationDoc newer = new KkuRegulationDoc("ข้อบังคับมหาวิทยาลัยขอนแก่น", "แบบฟอร์มประเมินผลการสอน",
                "https://hr2.kku.ac.th/wp-content/uploads/2026/01/form.pdf", "form.pdf", 2);
        KkuRegulationDoc sameFileOnCivilPage = new KkuRegulationDoc("ข้าราชการ · ข้อบังคับมหาวิทยาลัยขอนแก่น",
                "แบบฟอร์ม ก.พ.อ. 03", "https://hr2.kku.ac.th/wp-content/uploads/2023/05/form.pdf", "form.pdf", 1);
        when(docRepo.findByFileKey(any())).thenReturn(Optional.empty());

        KkuDocumentSyncService.SyncResult result = serviceSeeing(List.of(older, newer), sameFileOnCivilPage).syncNow();

        assertEquals(2, result.getTotalParsed());
        assertEquals("form.pdf", older.getFileKey());
        assertEquals("2026/01/form.pdf", newer.getFileKey());
        verify(docRepo).save(older);
        verify(docRepo).save(newer);
        verify(docRepo, org.mockito.Mockito.never()).save(sameFileOnCivilPage);
    }
}
