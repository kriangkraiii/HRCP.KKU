package com.ecom.external.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.ecom.external.config.FsApiProperties;
import com.ecom.external.harvest.PublicationHarvestService;
import com.ecom.external.harvest.model.HarvestResult;
import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.FsFacultyChange;
import com.ecom.external.model.FsSyncState;
import com.ecom.external.repository.FsFacultyChangeRepository;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.CpDirectorySyncService;
import com.ecom.external.service.FacultyChangeReviewService;
import com.ecom.external.service.FsSyncService;
import com.ecom.external.service.ManualSyncGuard;

class ExternalSyncPageControllerTest {

    private FsSyncService syncService;
    private FacultyChangeReviewService reviewService;
    private FsFacultyChangeRepository changeRepo;
    private ManualSyncGuard guard;
    private FsFacultyRepository facultyRepo;
    private ScopusPublicationRepository publicationRepo;
    private FsApiProperties props;
    private CpDirectorySyncService cpSyncService;
    private PublicationHarvestService harvestService;

    private ExternalSyncPageController controller;

    @BeforeEach
    void setUp() {
        syncService = mock(FsSyncService.class);
        reviewService = mock(FacultyChangeReviewService.class);
        changeRepo = mock(FsFacultyChangeRepository.class);
        guard = mock(ManualSyncGuard.class);
        facultyRepo = mock(FsFacultyRepository.class);
        publicationRepo = mock(ScopusPublicationRepository.class);
        props = mock(FsApiProperties.class);
        cpSyncService = mock(CpDirectorySyncService.class);
        harvestService = mock(PublicationHarvestService.class);

        when(guard.remaining(anyString())).thenReturn(Duration.ZERO);
        when(changeRepo.findByStatusOrderByReviewedAtDesc(anyString(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(reviewService.pending()).thenReturn(List.of());

        controller = new ExternalSyncPageController(
                syncService,
                reviewService,
                changeRepo,
                guard,
                facultyRepo,
                publicationRepo,
                props,
                cpSyncService,
                harvestService,
                mock(com.ecom.external.service.KkuDocumentSyncService.class),
                "0 30 1 * * *",
                "0 0 3 * * SUN",
                "0 0 2 * * *",
                "0 30 2 * * *",
                "0 30 3 * * SUN",
                "0 0 3 * * ?",
                "0 30 3 * * ?",
                "0 0 8 * * *",
                "0 30 8 * * *",
                "0 0 2 1 * ?"
        );
    }

    @Test
    @DisplayName("GET /admin/external-sync populates all model attributes including multi-source counters, 8 jobs, and 9 cron categories")
    void pagePopulatesAttributes() {
        when(facultyRepo.count()).thenReturn(15L);
        when(publicationRepo.count()).thenReturn(120L);
        when(facultyRepo.findAllWithScopusId()).thenReturn(List.of());

        List<Object[]> sourceCountsData = new ArrayList<>();
        sourceCountsData.add(new Object[] { "SCOPUS", 80L });
        sourceCountsData.add(new Object[] { "OPENALEX", 20L });
        sourceCountsData.add(new Object[] { "DBLP", 15L });
        when(publicationRepo.countGroupByDataSource()).thenReturn(sourceCountsData);

        FsSyncState openalexState = new FsSyncState();
        openalexState.setLastStatus("OK");
        openalexState.setRowsProcessed(20);

        when(syncService.currentState()).thenReturn(Map.of("openalex", openalexState));
        when(harvestService.isRunning()).thenReturn(false);

        Model model = new ConcurrentModel();
        String view = controller.page("sync", model);

        assertThat(view).isEqualTo("admin/external_sync");
        assertThat(model.getAttribute("facultyCount")).isEqualTo(15L);
        assertThat(model.getAttribute("publicationCount")).isEqualTo(120L);

        @SuppressWarnings("unchecked")
        Map<String, Long> sourceCounts = (Map<String, Long>) model.getAttribute("sourceCounts");
        assertThat(sourceCounts).isNotNull();
        assertThat(sourceCounts.get("SCOPUS")).isEqualTo(80L);
        assertThat(sourceCounts.get("OPENALEX")).isEqualTo(20L);
        assertThat(sourceCounts.get("DBLP")).isEqualTo(15L);
        assertThat(sourceCounts.get("CROSSREF")).isEqualTo(0L);

        @SuppressWarnings("unchecked")
        List<ExternalSyncPageController.SyncJobDisplay> syncJobList =
                (List<ExternalSyncPageController.SyncJobDisplay>) model.getAttribute("syncJobList");
        assertThat(syncJobList).hasSize(9);
        assertThat(syncJobList.stream().map(ExternalSyncPageController.SyncJobDisplay::id).toList())
                .containsExactly("users", "college_web", "scopus", "openalex", "crossref", "dblp", "thaijo", "kkuir", "kku_regulations");

        assertThat(model.getAttribute("okCount")).isEqualTo(1L);
        assertThat(model.getAttribute("totalJobs")).isEqualTo(9);

        @SuppressWarnings("unchecked")
        List<ExternalSyncPageController.ScheduleCategory> scheduleCategories =
                (List<ExternalSyncPageController.ScheduleCategory>) model.getAttribute("scheduleCategories");
        assertThat(scheduleCategories).hasSize(3);
        int totalCronItems = scheduleCategories.stream().mapToInt(c -> c.items().size()).sum();
        assertThat(totalCronItems).isEqualTo(10);
    }

    @Test
    @DisplayName("POST /admin/external-sync/harvest-all runs all adapters and sets success flash message")
    void harvestAllExecutesSuccessfully() {
        when(harvestService.isRunning()).thenReturn(false);

        RawPublication pub = RawPublication.builder().title("P1").dataSource("OPENALEX").build();
        HarvestResult r1 = HarvestResult.ok("OPENALEX", List.of(pub), null, 2, 500L, "ok");
        HarvestResult r2 = HarvestResult.ok("CROSSREF", List.of(), null, 1, 300L, "ok");

        when(harvestService.harvestAll()).thenReturn(List.of(r1, r2));

        RedirectAttributes redirect = new RedirectAttributesModelMap();
        String view = controller.harvestAll(redirect);

        assertThat(view).isEqualTo("redirect:/admin/external-sync");
        verify(harvestService).harvestAll();
        assertThat(redirect.getFlashAttributes()).containsKey("succMsg");
        assertThat(redirect.getFlashAttributes().get("succMsg").toString()).contains("2/2 แหล่งข้อมูล รวม 1 รายการ");
    }

    @Test
    @DisplayName("POST /admin/external-sync/harvest/{source} runs single adapter and sets flash message")
    void harvestSourceExecutesSuccessfully() {
        when(harvestService.isRunning()).thenReturn(false);

        RawPublication pub = RawPublication.builder().title("P1").dataSource("DBLP").build();
        HarvestResult result = HarvestResult.ok("DBLP", List.of(pub), null, 1, 200L, "ok");

        when(harvestService.harvestSource("dblp")).thenReturn(result);

        RedirectAttributes redirect = new RedirectAttributesModelMap();
        String view = controller.harvestSource("dblp", redirect);

        assertThat(view).isEqualTo("redirect:/admin/external-sync");
        verify(harvestService).harvestSource("dblp");
        assertThat(redirect.getFlashAttributes()).containsKey("succMsg");
        assertThat(redirect.getFlashAttributes().get("succMsg").toString()).contains("ดึงงานวิจัยจาก DBLP สำเร็จ — ได้รับ 1 รายการ");
    }

    @Test
    @DisplayName("Async REST trigger and progress snapshot endpoints respond with accurate tracking data")
    void asyncTriggerAndProgressEndpointsWork() {
        when(harvestService.isRunning()).thenReturn(false);
        com.ecom.external.harvest.HarvestProgressTracker tracker = new com.ecom.external.harvest.HarvestProgressTracker();
        tracker.start(5, List.of("OPENALEX"));
        when(harvestService.getProgressTracker()).thenReturn(tracker);

        var startResp = controller.harvestStartAsync();
        assertThat(startResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(startResp.getBody()).containsEntry("status", "STARTED");
        verify(harvestService).harvestAllAsync();

        var sourceResp = controller.harvestSourceAsync("openalex");
        assertThat(sourceResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(sourceResp.getBody()).containsEntry("status", "STARTED");
        verify(harvestService).harvestSourceAsync("openalex");

        var progressResp = controller.harvestProgress();
        assertThat(progressResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(progressResp.getBody().running()).isTrue();
        assertThat(progressResp.getBody().totalSteps()).isEqualTo(5);
    }
}
