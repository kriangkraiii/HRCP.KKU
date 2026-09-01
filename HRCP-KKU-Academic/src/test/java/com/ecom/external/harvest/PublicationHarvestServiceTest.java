package com.ecom.external.harvest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.harvest.config.HarvestProperties;
import com.ecom.external.harvest.model.HarvestContext;
import com.ecom.external.harvest.model.HarvestResult;
import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.ExternalAuthorMappingRepository;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.FsSyncStateRepository;
import com.ecom.external.service.FsSyncWriter;
import com.ecom.service.SystemAlertService;

class PublicationHarvestServiceTest {

    private PublicationSourceAdapter adapter1;
    private PublicationSourceAdapter adapter2;
    private HarvestProperties props;
    private FsFacultyRepository facultyRepo;
    private ExternalAuthorMappingRepository mappingRepo;
    private FsSyncStateRepository syncStateRepo;
    private FsSyncWriter writer;
    private SystemAlertService alerts;
    private PublicationHarvestService service;

    @BeforeEach
    void setUp() {
        adapter1 = mock(PublicationSourceAdapter.class);
        when(adapter1.sourceName()).thenReturn("OPENALEX");
        when(adapter1.isEnabled()).thenReturn(true);

        adapter2 = mock(PublicationSourceAdapter.class);
        when(adapter2.sourceName()).thenReturn("CROSSREF");
        when(adapter2.isEnabled()).thenReturn(true);

        props = new HarvestProperties();
        props.setEnabled(true);
        props.setYearFrom(2020);

        facultyRepo = mock(FsFacultyRepository.class);
        mappingRepo = mock(ExternalAuthorMappingRepository.class);
        syncStateRepo = mock(FsSyncStateRepository.class);
        writer = mock(FsSyncWriter.class);
        alerts = mock(SystemAlertService.class);

        service = new PublicationHarvestService(
                List.of(adapter1, adapter2),
                props,
                facultyRepo,
                mappingRepo,
                syncStateRepo,
                writer,
                alerts,
                new HarvestProgressTracker()
        );
    }

    @Test
    @DisplayName("harvestAll runs all adapters concurrently via virtual threads and writes batches")
    void harvestAllRunsAdaptersAndWritesBatches() {
        FsFaculty faculty = new FsFaculty();
        faculty.setFsUserId(1L);
        faculty.setFirstName("Somchai");
        faculty.setLastName("Jaidee");
        faculty.setNameEn("Somchai Jaidee");
        when(facultyRepo.findAll()).thenReturn(List.of(faculty));
        when(mappingRepo.findAll()).thenReturn(List.of());

        RawPublication pub1 = RawPublication.builder()
                .targetFsUserId(1L)
                .title("Paper 1")
                .dataSource("OPENALEX")
                .build();

        RawPublication pub2 = RawPublication.builder()
                .targetFsUserId(1L)
                .title("Paper 2")
                .dataSource("CROSSREF")
                .build();

        when(adapter1.harvest(any(HarvestContext.class)))
                .thenReturn(HarvestResult.ok("OPENALEX", List.of(pub1), null, 1, 100L, "ok"));
        when(adapter2.harvest(any(HarvestContext.class)))
                .thenReturn(HarvestResult.ok("CROSSREF", List.of(pub2), null, 1, 150L, "ok"));

        List<HarvestResult> results = service.harvestAll();

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(HarvestResult::success);

        // Verify writer batch calls
        verify(writer, times(2)).writeHarvestedBatch(anyList(), anyDouble());
        verify(alerts).success(eq("เก็บเกี่ยวผลงานวิจัยรอบวัน"), any());
    }

    @Test
    @DisplayName("harvestSource runs only the requested adapter")
    void harvestSourceRunsSpecificAdapter() {
        FsFaculty faculty = new FsFaculty();
        faculty.setFsUserId(1L);
        when(facultyRepo.findAll()).thenReturn(List.of(faculty));
        when(mappingRepo.findAll()).thenReturn(List.of());

        RawPublication pub = RawPublication.builder()
                .targetFsUserId(1L)
                .title("Single Paper")
                .dataSource("OPENALEX")
                .build();

        when(adapter1.harvest(any(HarvestContext.class)))
                .thenReturn(HarvestResult.ok("OPENALEX", List.of(pub), null, 1, 50L, "ok"));

        HarvestResult result = service.harvestSource("OPENALEX");

        assertThat(result.success()).isTrue();
        assertThat(result.sourceName()).isEqualTo("OPENALEX");
        assertThat(result.publications()).hasSize(1);

        verify(adapter1).harvest(any());
        verify(adapter2, times(0)).harvest(any());
    }
}
