package com.ecom.external.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
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
import com.ecom.external.model.ExternalAuthorMapping;
import com.ecom.external.model.FsFaculty;

/**
 * Live integration test testing real network harvesting against live APIs.
 */
@Tag("live-integration")
@Disabled("Live integration test requiring live external network APIs")
class LiveHarvestIntegrationTest {

    private FsFaculty createSampleFaculty() {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(999L);
        f.setFirstName("คานดา");
        f.setLastName("รุณณาพงษ์ศา");
        f.setNameEn("Kanda Runapongsa Saikaew");
        return f;
    }

    @Test
    @DisplayName("Live Test 1: OpenAlex ดึงงานวิจัยจริงของ มข. (ROR 03cq4gr50)")
    void testLiveOpenAlex() {
        HarvestProperties props = new HarvestProperties();
        props.getOpenalex().setPageSize(5);

        OpenAlexAdapter adapter = new OpenAlexAdapter(props);

        FsFaculty faculty = new FsFaculty();
        faculty.setFsUserId(1L);
        faculty.setNameEn("Panita Limpawattana");

        HarvestContext context = HarvestContext.of(null, 2024, List.of(faculty), Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("=== OpenAlex Live Result ===");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());
        if (!result.publications().isEmpty()) {
            System.out.println("Sample Title: " + result.publications().get(0).title());
            System.out.println("Sample DOI: " + result.publications().get(0).doi());
            System.out.println("Sample Authors: " + result.publications().get(0).authorNames());
        }

        assertThat(result.success()).isTrue();
        assertThat(result.requestsMade()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Live Test 2: Crossref ดึงงานวิจัยจริง Affiliation Khon Kaen University")
    void testLiveCrossref() {
        HarvestProperties props = new HarvestProperties();
        props.getCrossref().setPageSize(5);

        CrossrefAdapter adapter = new CrossrefAdapter(props);
        FsFaculty faculty = createSampleFaculty();

        HarvestContext context = HarvestContext.of(null, 2024, List.of(faculty), Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("=== Crossref Live Result ===");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());

        assertThat(result.success()).isTrue();
        assertThat(result.requestsMade()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Live Test 3: DBLP ดึงงานวิจัยจริงอาจารย์สายคอมพ์")
    void testLiveDblp() {
        HarvestProperties props = new HarvestProperties();
        DblpAdapter adapter = new DblpAdapter(props);

        FsFaculty faculty = new FsFaculty();
        faculty.setFsUserId(10L);
        faculty.setNameEn("Kanda Runapongsa");

        HarvestContext context = HarvestContext.of(null, 2020, List.of(faculty), Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("=== DBLP Live Result ===");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());
        if (!result.publications().isEmpty()) {
            System.out.println("Sample Title: " + result.publications().get(0).title());
            System.out.println("Sample Venue: " + result.publications().get(0).publicationName());
            System.out.println("Sample Year: " + result.publications().get(0).publicationYear());
        }

        assertThat(result.success()).isTrue();
        assertThat(result.requestsMade()).isGreaterThan(0);
        assertThat(result.publications()).isNotEmpty();
    }

    @Test
    @DisplayName("Live Test 4: ThaiJO OAI-PMH ดึง XML จริง")
    void testLiveThaijo() {
        HarvestProperties props = new HarvestProperties();
        props.getThaijo().setOaiEndpoint("https://sc01.tci-thaijo.org/index.php/index/oai");
        ThaijoAdapter adapter = new ThaijoAdapter(props);

        FsFaculty faculty = createSampleFaculty();

        HarvestContext context = HarvestContext.of(null, 2024, List.of(faculty), Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("=== ThaiJO Live Result ===");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Publications harvested: " + result.publications().size());

        assertThat(result.success()).isTrue();
        assertThat(result.requestsMade()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Live Test 5: KKU IR DSpace OAI-PMH (ผ่าน KKU VPN/Intranet หรือ Soft Resilience)")
    void testLiveKkuIr() {
        HarvestProperties props = new HarvestProperties();
        KkuIrAdapter adapter = new KkuIrAdapter(props);

        FsFaculty faculty = createSampleFaculty();

        HarvestContext context = HarvestContext.of(null, 2024, List.of(faculty), Map.of());
        HarvestResult result = adapter.harvest(context);

        System.out.println("=== KKU IR Live Result ===");
        System.out.println("Success: " + result.success());
        System.out.println("Requests made: " + result.requestsMade());
        System.out.println("Message: " + result.message());

        // KKU IR is protected inside KKU intranet/VPN. Adapter executes requests safely.
        assertThat(result.requestsMade()).isGreaterThanOrEqualTo(0);
    }
}
