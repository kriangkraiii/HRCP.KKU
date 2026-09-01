package com.ecom.external.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.ScopusPublication;

class PublicationHarmonizerTest {

    private PublicationHarmonizer harmonizer;

    @BeforeEach
    void setUp() {
        harmonizer = new PublicationHarmonizer();
    }

    @Test
    @DisplayName("Harmonize normalizes DOI, whitespace, ISSN and maps all core fields")
    void harmonizeMapsAndNormalizesFields() {
        RawPublication raw = RawPublication.builder()
                .targetFsUserId(42L)
                .externalId("W123456789")
                .doi("https://doi.org/10.1007/S10489-023-04981-W")
                .title("   A Fast    Transformer for Image Segmentation  \n ")
                .publicationName("Applied Intelligence  ")
                .publicationYear(2024)
                .citedBy(15)
                .authorNames("Somchai Jaidee | John Doe")
                .issn("0924-669X")
                .eissn("1573-7497")
                .volume("54")
                .issue("3")
                .pageRange("100-112")
                .url("https://doi.org/10.1007/s10489-023-04981-w")
                .dataSource("OPENALEX")
                .rawMetadataJson("{\"sample\":true}")
                .build();

        ScopusPublication entity = harmonizer.harmonize(raw, 42L, "mockhash123");

        assertThat(entity.getFsUserId()).isEqualTo(42L);
        assertThat(entity.getDataSource()).isEqualTo("OPENALEX");
        assertThat(entity.getExternalId()).isEqualTo("W123456789");
        assertThat(entity.getDoi()).isEqualTo("10.1007/s10489-023-04981-w");
        assertThat(entity.getTitle()).isEqualTo("A Fast Transformer for Image Segmentation");
        assertThat(entity.getPublicationName()).isEqualTo("Applied Intelligence");
        assertThat(entity.getPublicationYear()).isEqualTo(2024);
        assertThat(entity.getCitedBy()).isEqualTo(15);
        assertThat(entity.getIssn()).isEqualTo("0924-669X");
        assertThat(entity.getEissn()).isEqualTo("1573-7497");
        assertThat(entity.getDedupHash()).isEqualTo("mockhash123");
        assertThat(entity.getRawSourceMetadata()).isEqualTo("{\"sample\":true}");
    }

    @Test
    @DisplayName("mergeIntoExisting only updates missing fields and higher citation counts")
    void mergeIntoExistingEnrichesWithoutOverwriting() {
        ScopusPublication existing = new ScopusPublication();
        existing.setId(10L);
        existing.setFsUserId(42L);
        existing.setTitle("Existing Scopus Paper Title");
        existing.setPublicationYear(2023);
        existing.setCitedBy(5);
        // DOI and abstract are missing in existing
        existing.setDoi(null);
        existing.setAbstractText(null);

        RawPublication raw = RawPublication.builder()
                .targetFsUserId(42L)
                .doi("10.1109/TPAMI.2023.123456")
                .abstractText("This is an enriched abstract from OpenAlex.")
                .citedBy(12) // higher citation count
                .openalexId("https://openalex.org/W999")
                .dataSource("OPENALEX")
                .build();

        harmonizer.mergeIntoExisting(existing, raw, "newhash");

        assertThat(existing.getDoi()).isEqualTo("10.1109/tpami.2023.123456");
        assertThat(existing.getAbstractText()).isEqualTo("This is an enriched abstract from OpenAlex.");
        assertThat(existing.getCitedBy()).isEqualTo(12);
        assertThat(existing.getOpenalexId()).isEqualTo("https://openalex.org/W999");
        assertThat(existing.getTitle()).isEqualTo("Existing Scopus Paper Title"); // original kept
    }

    @Test
    @DisplayName("Truncates string fields safely without throwing IndexOutOfBoundsException")
    void truncatesSafely() {
        String veryLongText = "A".repeat(3000);
        assertThat(PublicationHarmonizer.truncate(veryLongText, 2000)).hasSize(2000);
        assertThat(PublicationHarmonizer.truncate("short", 2000)).isEqualTo("short");
        assertThat(PublicationHarmonizer.truncate(null, 100)).isNull();
    }
}
