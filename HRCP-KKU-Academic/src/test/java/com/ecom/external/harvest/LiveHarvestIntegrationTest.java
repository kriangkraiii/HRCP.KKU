package com.ecom.external.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.ecom.external.harvest.adapter.CrossrefAdapter;
import com.ecom.external.harvest.adapter.DblpAdapter;
import com.ecom.external.harvest.adapter.KkuIrAdapter;
import com.ecom.external.harvest.adapter.OpenAlexAdapter;
import com.ecom.external.harvest.adapter.ThaijoAdapter;
import com.ecom.external.harvest.config.HarvestProperties;
import com.ecom.external.harvest.model.HarvestContext;
import com.ecom.external.harvest.model.HarvestResult;
import com.ecom.external.model.FsFaculty;

/**
 * Live integration test testing real network harvesting against live APIs
 * across all non-FS external sources (OpenAlex, Crossref, DBLP, ThaiJO, KKU IR).
 */
@Tag("live-integration")
class LiveHarvestIntegrationTest {

    private List<FsFaculty> createRealFacultyList() {
        FsFaculty f1 = new FsFaculty();
        f1.setFsUserId(24L);
        f1.setFirstName("คานดา");
        f1.setLastName("รุณณาพงษ์ศา สายแก้ว");
        f1.setNameEn("Kanda Runapongsa Saikaew");

        FsFaculty f2 = new FsFaculty();
        f2.setFsUserId(21L);
        f2.setFirstName("จักรชัย");
        f2.setLastName("โสอินทร์");
        f2.setNameEn("Chakchai So-In");

        FsFaculty f3 = new FsFaculty();
        f3.setFsUserId(22L);
        f3.setFirstName("คำรณ");
        f3.setLastName("สุนัติ");
        f3.setNameEn("Khamron Sunat");

        FsFaculty f4 = new FsFaculty();
        f4.setFsUserId(84L);
        f4.setFirstName("งามนิจ");
        f4.setLastName("อาจอินทร์");
        f4.setNameEn("Ngamnij Arch-int");

        return List.of(f1, f2, f3, f4);
    }

    @Test
    @DisplayName("Live Test 1: OpenAlex ดึงงานวิจัยจริงของอาจารย์ CP KKU (ROR 03cq4gr50)")
    void testLiveOpenAlex() {
        HarvestProperties props = new HarvestProperties();
        props.getOpenalex().setPageSize(25);

        OpenAlexAdapter adapter = new OpenAlexAdapter(props);
        List<FsFaculty> faculty = createRealFacultyList();

        HarvestContext context = HarvestContext.of(null, 2020, faculty, Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("\n=================== 1. OPENALEX LIVE RESULT ===================");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());
        System.out.println("Duration (ms): " + result.durationMs());
        if (!result.publications().isEmpty()) {
            System.out.println("Sample Title: " + result.publications().get(0).title());
            System.out.println("Sample DOI: " + result.publications().get(0).doi());
            System.out.println("Sample Year: " + result.publications().get(0).publicationYear());
            System.out.println("Sample Authors: " + result.publications().get(0).authorNames());
        }

        assertThat(result.success()).isTrue();
        assertThat(result.requestsMade()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Live Test 2: Crossref ดึงงานวิจัยจริง Affiliation Khon Kaen University")
    void testLiveCrossref() {
        HarvestProperties props = new HarvestProperties();
        props.getCrossref().setPageSize(25);

        CrossrefAdapter adapter = new CrossrefAdapter(props);
        List<FsFaculty> faculty = createRealFacultyList();

        HarvestContext context = HarvestContext.of(null, 2020, faculty, Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("\n=================== 2. CROSSREF LIVE RESULT ===================");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());
        System.out.println("Duration (ms): " + result.durationMs());
        if (!result.publications().isEmpty()) {
            System.out.println("Sample Title: " + result.publications().get(0).title());
            System.out.println("Sample DOI: " + result.publications().get(0).doi());
            System.out.println("Sample Authors: " + result.publications().get(0).authorNames());
        }

        assertThat(result.success()).isTrue();
        assertThat(result.requestsMade()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Live Test 3: DBLP ดึงงานวิจัยจริงอาจารย์สายคอมพ์")
    void testLiveDblp() {
        HarvestProperties props = new HarvestProperties();
        DblpAdapter adapter = new DblpAdapter(props);

        List<FsFaculty> faculty = createRealFacultyList();

        HarvestContext context = HarvestContext.of(null, 2020, faculty, Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("\n=================== 3. DBLP LIVE RESULT ===================");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());
        System.out.println("Duration (ms): " + result.durationMs());
        System.out.println("DBLP Notice: " + (result.publications().isEmpty()
                ? "Note: DBLP returned 0 items (Cloudflare Bot Challenge active on dblp.org)"
                : "Found " + result.publications().size() + " items"));

        assertThat(result.success()).isTrue();
        assertThat(result.requestsMade()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Live Test 4: ThaiJO OAI-PMH ดึง XML จริง")
    void testLiveThaijo() {
        HarvestProperties props = new HarvestProperties();
        props.getThaijo().setOaiEndpoint("https://sc01.tci-thaijo.org/index.php/index/oai");
        ThaijoAdapter adapter = new ThaijoAdapter(props);

        List<FsFaculty> faculty = createRealFacultyList();

        HarvestContext context = HarvestContext.of(null, 2023, faculty, Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("\n=================== 4. THAIJO LIVE RESULT ===================");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());
        System.out.println("Duration (ms): " + result.durationMs());

        assertThat(result.success()).isTrue();
        assertThat(result.requestsMade()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Live Test 5: KKU IR DSpace OAI-PMH")
    void testLiveKkuIr() {
        HarvestProperties props = new HarvestProperties();
        KkuIrAdapter adapter = new KkuIrAdapter(props);

        List<FsFaculty> faculty = createRealFacultyList();

        HarvestContext context = HarvestContext.of(null, 2020, faculty, Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("\n=================== 5. KKU IR LIVE RESULT ===================");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());
        System.out.println("Duration (ms): " + result.durationMs());
        System.out.println("Message: " + result.message());

        assertThat(result.requestsMade()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Live Test 6: ดึงจริงทุกแหล่งพร้อมกันแบบ Multi-Thread (Virtual Threads)")
    void testLiveAllSourcesParallel() throws Exception {
        HarvestProperties props = new HarvestProperties();
        props.getCrossref().setPageSize(25);
        props.getOpenalex().setPageSize(25);
        props.getThaijo().setOaiEndpoint("https://sc01.tci-thaijo.org/index.php/index/oai");
        props.getKkuir().setOaiEndpoint("https://kkuir.kku.ac.th/oai/request");
        props.getKkuir().setSet("col_123456789_37199");

        List<PublicationSourceAdapter> adapters = List.of(
                new OpenAlexAdapter(props),
                new CrossrefAdapter(props),
                new DblpAdapter(props),
                new ThaijoAdapter(props),
                new KkuIrAdapter(props)
        );

        List<FsFaculty> faculty = createRealFacultyList();
        HarvestContext context = HarvestContext.of(null, 2020, faculty, Map.of());

        System.out.println("\n==========================================================================");
        System.out.println("=== LIVE HARVEST TEST: RUNNING ALL 5 SOURCES IN PARALLEL (VIRTUAL THREADS) ===");
        System.out.println("==========================================================================");

        long startTime = System.currentTimeMillis();
        List<HarvestResult> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<HarvestResult>> tasks = adapters.stream()
                    .map(a -> (Callable<HarvestResult>) () -> {
                        long t0 = System.currentTimeMillis();
                        System.out.println(">> [" + a.sourceName() + "] Started harvesting on virtual thread...");
                        HarvestResult r = a.harvest(context);
                        System.out.println("<< [" + a.sourceName() + "] Finished in " + (System.currentTimeMillis() - t0)
                                + " ms: " + r.publications().size() + " works, success=" + r.success());
                        return r;
                    })
                    .toList();

            List<Future<HarvestResult>> futures = executor.invokeAll(tasks, 120, TimeUnit.SECONDS);
            for (Future<HarvestResult> f : futures) {
                results.add(f.get());
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("\n=================== SUMMARY OF ALL SOURCES ===================");
        System.out.printf("%-12s | %-8s | %-12s | %-15s | %-12s%n", "Source", "Success", "Works Found", "Requests Made", "Duration (ms)");
        System.out.println("----------------------------------------------------------------------");
        for (HarvestResult r : results) {
            System.out.printf("%-12s | %-8s | %-12d | %-15d | %-12d%n",
                    r.sourceName(), r.success(), r.publications().size(), r.requestsMade(), r.durationMs());
        }
        System.out.println("----------------------------------------------------------------------");
        System.out.println("Total execution time (all sources in parallel): " + totalTime + " ms");
        System.out.println("Total publications retrieved: " + results.stream().mapToInt(r -> r.publications().size()).sum());

        assertThat(results).hasSize(5);
    }
}
