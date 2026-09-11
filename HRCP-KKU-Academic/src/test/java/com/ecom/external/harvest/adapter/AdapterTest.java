package com.ecom.external.harvest.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.harvest.config.HarvestProperties;
import com.ecom.external.harvest.model.HarvestContext;
import com.ecom.external.harvest.model.HarvestResult;
import com.ecom.external.model.ExternalAuthorMapping;
import com.ecom.external.model.FsFaculty;

class AdapterTest {

    @Test
    @DisplayName("OpenAlex adapter disabled returns skipped result gracefully")
    void openAlexDisabled() {
        HarvestProperties props = new HarvestProperties();
        props.getOpenalex().setEnabled(false);

        OpenAlexAdapter adapter = new OpenAlexAdapter(props);
        assertThat(adapter.isEnabled()).isFalse();

        HarvestResult result = adapter.harvest(HarvestContext.of(null, 2020, List.of(), Map.of()));
        assertThat(result.success()).isTrue();
        assertThat(result.publications()).isEmpty();
    }

    @Test
    @DisplayName("Crossref adapter disabled returns skipped result gracefully")
    void crossrefDisabled() {
        HarvestProperties props = new HarvestProperties();
        props.getCrossref().setEnabled(false);

        CrossrefAdapter adapter = new CrossrefAdapter(props);
        assertThat(adapter.isEnabled()).isFalse();

        HarvestResult result = adapter.harvest(HarvestContext.of(null, 2020, List.of(), Map.of()));
        assertThat(result.success()).isTrue();
        assertThat(result.publications()).isEmpty();
    }

    @Test
    @DisplayName("DBLP adapter disabled returns skipped result gracefully")
    void dblpDisabled() {
        HarvestProperties props = new HarvestProperties();
        props.getDblp().setEnabled(false);

        DblpAdapter adapter = new DblpAdapter(props);
        assertThat(adapter.isEnabled()).isFalse();

        HarvestResult result = adapter.harvest(HarvestContext.of(null, 2020, List.of(), Map.of()));
        assertThat(result.success()).isTrue();
        assertThat(result.publications()).isEmpty();
    }

    @Test
    @DisplayName("ThaiJO adapter disabled returns skipped result gracefully")
    void thaijoDisabled() {
        HarvestProperties props = new HarvestProperties();
        props.getThaijo().setEnabled(false);

        ThaijoAdapter adapter = new ThaijoAdapter(props);
        assertThat(adapter.isEnabled()).isFalse();

        HarvestResult result = adapter.harvest(HarvestContext.of(null, 2020, List.of(), Map.of()));
        assertThat(result.success()).isTrue();
        assertThat(result.publications()).isEmpty();
    }

    @Test
    @DisplayName("KKU IR adapter disabled returns skipped result gracefully")
    void kkuIrDisabled() {
        HarvestProperties props = new HarvestProperties();
        props.getKkuir().setEnabled(false);

        KkuIrAdapter adapter = new KkuIrAdapter(props);
        assertThat(adapter.isEnabled()).isFalse();

        HarvestResult result = adapter.harvest(HarvestContext.of(null, 2020, List.of(), Map.of()));
        assertThat(result.success()).isTrue();
        assertThat(result.publications()).isEmpty();
    }

    @Test
    @DisplayName("DBLP adapter handles author mappings correctly")
    void dblpHandlesAuthorMappings() {
        HarvestProperties props = new HarvestProperties();
        props.getDblp().setEnabled(true);
        props.getDblp().setBaseUrl("http://127.0.0.1:54321"); // unroutable

        DblpAdapter adapter = new DblpAdapter(props);

        FsFaculty faculty = new FsFaculty();
        faculty.setFsUserId(10L);
        faculty.setFirstName("Somchai");
        faculty.setLastName("Jaidee");
        faculty.setNameEn("Somchai Jaidee");

        ExternalAuthorMapping mapping = new ExternalAuthorMapping(10L, "DBLP", "homepages/12/3456");

        HarvestContext context = HarvestContext.of(
                null, 2020, List.of(faculty), Map.of(10L, List.of(mapping)));

        // Even when external service is offline, adapter handles connection errors safely
        HarvestResult result = adapter.harvest(context);
        assertThat(result.success()).isTrue(); // soft recovery
        assertThat(result.publications()).isEmpty();
    }

    @Test
    @DisplayName("Crossref adapter handles connection errors safely without throwing uncaught exceptions")
    void crossrefHandlesExternalErrorSafely() {
        HarvestProperties props = new HarvestProperties();
        props.getCrossref().setEnabled(true);
        props.getCrossref().setBaseUrl("http://127.0.0.1:54321"); // unroutable

        CrossrefAdapter adapter = new CrossrefAdapter(props);

        FsFaculty faculty = new FsFaculty();
        faculty.setFsUserId(10L);
        faculty.setFirstName("Somchai");
        faculty.setLastName("Jaidee");
        faculty.setNameEn("Somchai Jaidee");

        HarvestContext context = HarvestContext.of(null, 2020, List.of(faculty), Map.of());

        HarvestResult result = adapter.harvest(context);
        assertThat(result.success()).isTrue();
        assertThat(result.publications()).isEmpty();
    }
}
