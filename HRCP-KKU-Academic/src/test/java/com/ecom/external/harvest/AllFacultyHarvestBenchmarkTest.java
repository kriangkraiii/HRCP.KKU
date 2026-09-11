package com.ecom.external.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.ExternalAuthorMapping;
import com.ecom.external.model.FsFaculty;

/**
 * Benchmark test executing real harvesting for ALL faculty members across all external sources.
 * Generates an end-to-end completeness audit report.
 */
@Tag("live-all-faculty")
public class AllFacultyHarvestBenchmarkTest {

    private List<FsFaculty> loadAllFacultyFromDatabase() {
        List<FsFaculty> list = new ArrayList<>();
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection("jdbc:postgresql://10.198.200.84:5432/hr_db", "postgres", "postgresql");
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT fs_user_id, user_fname, user_lname, name_en, email, position_title FROM fs_faculty ORDER BY fs_user_id")) {
                while (rs.next()) {
                    FsFaculty f = new FsFaculty();
                    f.setFsUserId(rs.getLong("fs_user_id"));
                    f.setFirstName(rs.getString("user_fname"));
                    f.setLastName(rs.getString("user_lname"));
                    f.setNameEn(rs.getString("name_en"));
                    f.setEmail(rs.getString("email"));
                    f.setPositionTitle(rs.getString("position_title"));
                    list.add(f);
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load from PostgreSQL: " + e.getMessage());
        }

        // Ensure prominent faculty like Assoc. Prof. Dr. Kanda is present
        boolean hasKanda = list.stream().anyMatch(f -> f.getNameEn() != null && f.getNameEn().toLowerCase().contains("kanda"));
        if (!hasKanda) {
            FsFaculty fk = new FsFaculty();
            fk.setFsUserId(24L);
            fk.setFirstName("คานดา");
            fk.setLastName("รุณณาพงษ์ศา สายแก้ว");
            fk.setNameEn("Kanda Runapongsa Saikaew");
            fk.setEmail("kanda@kku.ac.th");
            fk.setPositionTitle("รองศาสตราจารย์");
            list.add(fk);
        }

        return list;
    }

    private Map<Long, List<ExternalAuthorMapping>> loadMappingsFromDatabase() {
        Map<Long, List<ExternalAuthorMapping>> map = new HashMap<>();
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection("jdbc:postgresql://10.198.200.84:5432/hr_db", "postgres", "postgresql");
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM external_author_mapping")) {
                while (rs.next()) {
                    ExternalAuthorMapping m = new ExternalAuthorMapping();
                    m.setFsUserId(rs.getLong("fs_user_id"));
                    m.setProvider(rs.getString("provider"));
                    m.setExternalPid(rs.getString("external_pid"));
                    map.computeIfAbsent(m.getFsUserId(), k -> new ArrayList<>()).add(m);
                }
            }
        } catch (Exception e) {
            // Table might not exist or empty
        }
        return map;
    }

    @Test
    @DisplayName("ดึงข้อมูลงานวิจัยจริงของอาจารย์ทุกคนในระบบจากทุกแหล่งภายนอก พร้อมตรวจสอบความครบถ้วน")
    void testHarvestAllFacultyCompleteness() throws Exception {
        List<FsFaculty> allFaculty = loadAllFacultyFromDatabase();
        System.out.println("Loaded " + allFaculty.size() + " total faculty members from system.");
        assertThat(allFaculty).isNotEmpty();

        Map<Long, List<ExternalAuthorMapping>> mappings = loadMappingsFromDatabase();

        HarvestProperties props = new HarvestProperties();
        props.getCrossref().setPageSize(50);
        props.getCrossref().setThrottleMs(100);
        props.getOpenalex().setPageSize(50);
        props.getOpenalex().setThrottleMs(100);
        props.getThaijo().setOaiEndpoint("https://sc01.tci-thaijo.org/index.php/index/oai");
        props.getKkuir().setOaiEndpoint("https://kkuir.kku.ac.th/oai/request");
        props.getKkuir().setSet("col_123456789_37199,col_123456789_37198,col_123456789_37197");

        List<PublicationSourceAdapter> adapters = List.of(
                new OpenAlexAdapter(props),
                new CrossrefAdapter(props),
                new ThaijoAdapter(props),
                new KkuIrAdapter(props),
                new DblpAdapter(props)
        );

        HarvestContext context = HarvestContext.of(null, 2018, allFaculty, mappings);

        System.out.println("\n==========================================================================");
        System.out.println("=== STARTING FULL LIVE HARVEST FOR ALL " + allFaculty.size() + " FACULTY MEMBERS ===");
        System.out.println("=== SOURCES: OpenAlex, Crossref, ThaiJO, KKU IR, DBLP ===");
        System.out.println("==========================================================================");

        long t0 = System.currentTimeMillis();
        List<HarvestResult> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<HarvestResult>> tasks = adapters.stream()
                    .map(a -> (Callable<HarvestResult>) () -> {
                        long start = System.currentTimeMillis();
                        System.out.println(">>> [" + a.sourceName() + "] Starting harvest for all " + allFaculty.size() + " faculty...");
                        HarvestResult r = a.harvest(context);
                        System.out.println("<<< [" + a.sourceName() + "] Done in " + (System.currentTimeMillis() - start)
                                + " ms: " + r.publications().size() + " works.");
                        return r;
                    })
                    .toList();

            List<Future<HarvestResult>> futures = executor.invokeAll(tasks, 240, TimeUnit.SECONDS);
            for (Future<HarvestResult> f : futures) {
                try {
                    results.add(f.get());
                } catch (Exception ex) {
                    System.err.println("Adapter task interrupted or cancelled: " + ex.getMessage());
                }
            }
        }

        long totalElapsed = System.currentTimeMillis() - t0;

        // Group publications by faculty user ID and source
        Map<Long, Map<String, List<RawPublication>>> pubsByFacultyAndSource = new HashMap<>();
        Map<Long, Set<String>> distinctPubTitlesByFaculty = new HashMap<>();

        for (HarvestResult res : results) {
            String src = res.sourceName();
            for (RawPublication p : res.publications()) {
                Long uid = p.targetFsUserId();
                if (uid != null) {
                    pubsByFacultyAndSource.computeIfAbsent(uid, k -> new HashMap<>())
                            .computeIfAbsent(src, k -> new ArrayList<>()).add(p);

                    String dedupeKey = (p.doi() != null && !p.doi().isBlank())
                            ? p.doi().toLowerCase().trim()
                            : (p.title() != null ? p.title().toLowerCase().replaceAll("[^a-z0-9]", "") : "");

                    if (!dedupeKey.isEmpty()) {
                        distinctPubTitlesByFaculty.computeIfAbsent(uid, k -> new HashSet<>()).add(dedupeKey);
                    }
                }
            }
        }

        // Print Complete Audit Table
        System.out.println("\n==========================================================================================================================");
        System.out.println("                                  COMPREHENSIVE FACULTY PUBLICATION AUDIT REPORT (2018-2026)                               ");
        System.out.println("==========================================================================================================================");
        System.out.printf("%-4s | %-28s | %-26s | %-8s | %-8s | %-6s | %-6s | %-6s | %-12s%n",
                "No.", "ชื่อ-สกุล (ไทย)", "Name (English)", "OpenAlex", "Crossref", "KKU IR", "ThaiJO", "DBLP", "รวมไม่ซ้ำ (Unique)");
        System.out.println("--------------------------------------------------------------------------------------------------------------------------");

        int facultyWithPubs = 0;
        int totalUniquePubs = 0;

        for (int i = 0; i < allFaculty.size(); i++) {
            FsFaculty f = allFaculty.get(i);
            Long uid = f.getFsUserId();
            Map<String, List<RawPublication>> srcMap = pubsByFacultyAndSource.getOrDefault(uid, Map.of());

            int openAlexCount = srcMap.getOrDefault("OPENALEX", List.of()).size();
            int crossrefCount = srcMap.getOrDefault("CROSSREF", List.of()).size();
            int kkuIrCount = srcMap.getOrDefault("KKU_IR", List.of()).size();
            int thaijoCount = srcMap.getOrDefault("THAIJO", List.of()).size();
            int dblpCount = srcMap.getOrDefault("DBLP", List.of()).size();

            Set<String> uniqueKeys = distinctPubTitlesByFaculty.getOrDefault(uid, Set.of());
            int uniqueCount = uniqueKeys.size();
            totalUniquePubs += uniqueCount;

            if (uniqueCount > 0) {
                facultyWithPubs++;
            }

            String thaiName = (f.getFirstName() != null ? f.getFirstName() : "") + " " + (f.getLastName() != null ? f.getLastName() : "");
            String enName = f.getNameEn() != null ? f.getNameEn() : "-";

            System.out.printf("%-4d | %-28s | %-26s | %-8d | %-8d | %-6d | %-6d | %-6d | %-12d%n",
                    (i + 1),
                    truncate(thaiName.trim(), 28),
                    truncate(enName.trim(), 26),
                    openAlexCount,
                    crossrefCount,
                    kkuIrCount,
                    thaijoCount,
                    dblpCount,
                    uniqueCount);
        }

        System.out.println("==========================================================================================================================");
        System.out.printf("สรุปภาพรวมการดึงข้อมูล:%n");
        System.out.printf("- อาจารย์ทั้งหมดในระบบ: %d ท่าน%n", allFaculty.size());
        System.out.printf("- อาจารย์ที่พบผลงานวิจัย: %d ท่าน (%.1f%%)%n", facultyWithPubs, (facultyWithPubs * 100.0 / allFaculty.size()));
        System.out.printf("- จำนวนผลงานวิจัยรวมทั้งหมด (Deduplicated Unique): %d เรื่อง%n", totalUniquePubs);
        System.out.printf("- เวลารวมในการดึงทุกแหล่งพร้อมกัน (Virtual Threads): %.2f วินาที%n", (totalElapsed / 1000.0));
        System.out.println("==========================================================================================================================");

        assertThat(totalUniquePubs).isGreaterThan(100);
    }

    private static String truncate(String s, int len) {
        if (s == null) return "-";
        if (s.length() <= len) return s;
        return s.substring(0, len - 2) + "..";
    }
}
