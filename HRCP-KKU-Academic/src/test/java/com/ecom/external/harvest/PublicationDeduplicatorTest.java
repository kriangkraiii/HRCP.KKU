package com.ecom.external.harvest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.ScopusPublicationRepository;

class PublicationDeduplicatorTest {

    private ScopusPublicationRepository publicationRepo;
    private FuzzyTitleMatcher fuzzyMatcher;
    private PublicationDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        publicationRepo = mock(ScopusPublicationRepository.class);
        // The pg_trgm lookup now goes through its own bean so that a database
        // without the extension cannot roll back the batch being written.
        fuzzyMatcher = mock(FuzzyTitleMatcher.class);
        deduplicator = new PublicationDeduplicator(publicationRepo, fuzzyMatcher);
    }

    @Test
    @DisplayName("Level 1 Deduplication: Exact DOI match returns duplicate")
    void level1DoiMatch() {
        Long userId = 100L;
        String doi = "10.1016/j.artint.2024.104000";

        ScopusPublication existing = new ScopusPublication();
        existing.setId(1L);
        existing.setFsUserId(userId);
        existing.setDoi(doi);

        when(publicationRepo.findFirstByFsUserIdAndDoiIgnoreCase(userId, doi)).thenReturn(Optional.of(existing));

        RawPublication raw = RawPublication.builder()
                .targetFsUserId(userId)
                .doi("https://doi.org/" + doi) // test with URL prefix
                .title("A deep study on agentic workflows")
                .publicationYear(2024)
                .authorNames("Somchai Jaidee | John Doe")
                .build();

        PublicationDeduplicator.DedupResult result = deduplicator.findDuplicate(raw, userId, 0.70);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.matchLevel()).isEqualTo(PublicationDeduplicator.MatchLevel.LEVEL_1_DOI);
        assertThat(result.existing()).contains(existing);
    }

    @Test
    @DisplayName("Level 2 Deduplication: Normalized Hash match when DOI is missing")
    void level2HashMatch() {
        Long userId = 100L;
        String title = "Machine Learning in Agriculture: A Comprehensive Review";
        int year = 2023;
        String authors = "Somchai Jaidee | Malee Srisuk";

        String hash = PublicationDeduplicator.computeDedupHash(title, year, authors);
        assertThat(hash).isNotNull();

        ScopusPublication existing = new ScopusPublication();
        existing.setId(2L);
        existing.setFsUserId(userId);
        existing.setDedupHash(hash);

        when(publicationRepo.findFirstByFsUserIdAndDoiIgnoreCase(eq(userId), anyString())).thenReturn(Optional.empty());
        when(publicationRepo.findFirstByFsUserIdAndDedupHash(userId, hash)).thenReturn(Optional.of(existing));

        RawPublication raw = RawPublication.builder()
                .targetFsUserId(userId)
                .title("Machine learning in agriculture: a comprehensive review!")
                .publicationYear(year)
                .authorNames("Jaidee, Somchai ; Srisuk, Malee")
                .build();

        PublicationDeduplicator.DedupResult result = deduplicator.findDuplicate(raw, userId, 0.70);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.matchLevel()).isEqualTo(PublicationDeduplicator.MatchLevel.LEVEL_2_HASH);
        assertThat(result.existing()).contains(existing);
    }

    @Test
    @DisplayName("Level 2.5 Deduplication: PostgreSQL pg_trgm fuzzy match when title slightly varies")
    void level25FuzzyMatch() {
        Long userId = 100L;
        String title = "Deep Reinforcement Learning for Autonomous Drone Navigation";
        int year = 2024;

        ScopusPublication existing = new ScopusPublication();
        existing.setId(3L);
        existing.setFsUserId(userId);
        existing.setTitle("Deep Reinforcement Learning for Autonomous Drone Navigation System");
        existing.setPublicationYear(year);

        when(publicationRepo.findFirstByFsUserIdAndDoiIgnoreCase(eq(userId), anyString())).thenReturn(Optional.empty());
        when(publicationRepo.findFirstByFsUserIdAndDedupHash(eq(userId), anyString())).thenReturn(Optional.empty());
        when(fuzzyMatcher.findSimilarTitleId(eq(userId), anyString(), eq(year), anyDouble())).thenReturn(Optional.of(3L));
        when(publicationRepo.findById(3L)).thenReturn(Optional.of(existing));

        RawPublication raw = RawPublication.builder()
                .targetFsUserId(userId)
                .title(title)
                .publicationYear(year)
                .authorNames("Somchai Jaidee")
                .build();

        PublicationDeduplicator.DedupResult result = deduplicator.findDuplicate(raw, userId, 0.70);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.matchLevel()).isEqualTo(PublicationDeduplicator.MatchLevel.LEVEL_2_5_FUZZY);
        assertThat(result.existing()).contains(existing);
    }

    @Test
    @DisplayName("Dedup Hash computation works with Thai text")
    void thaiTextDedupHash() {
        String title = "การพัฒนาระบบสารสนเทศเพื่อการบริหารงานบุคคล มหาวิทยาลัยขอนแก่น";
        String hash = PublicationDeduplicator.computeDedupHash(title, 2567, "สมชาย ใจดี");

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 in Hex

        // Same title with punctuation and different spacing must produce exact same hash
        String titleVariation = "การพัฒนาระบบสารสนเทศ เพื่อการบริหารงานบุคคล  มหาวิทยาลัยขอนแก่น (ฉบับสมบูรณ์)";
        String hashVariation = PublicationDeduplicator.computeDedupHash(titleVariation, 2567, "สมชาย ใจดี");
        // Title has extra words so hash will differ, but normal whitespace collapse works:
        String titleSpaceVariation = "การพัฒนาระบบสารสนเทศเพื่อการบริหารงานบุคคล   มหาวิทยาลัยขอนแก่น";
        String hashSpaceVariation = PublicationDeduplicator.computeDedupHash(titleSpaceVariation, 2567, "สมชาย ใจดี");
        assertThat(hashSpaceVariation).isEqualTo(hash);
    }

    @Test
    @DisplayName("Extracts first author signature invariant to ordering")
    void extractFirstAuthorSurname() {
        assertThat(PublicationDeduplicator.extractFirstAuthorSurname("Somchai Jaidee | Malee Srisuk")).isEqualTo("jaidee_somchai");
        assertThat(PublicationDeduplicator.extractFirstAuthorSurname("Jaidee, Somchai ; Srisuk, Malee")).isEqualTo("jaidee_somchai");
        assertThat(PublicationDeduplicator.extractFirstAuthorSurname("Jaidee, S.")).isEqualTo("jaidee");
        assertThat(PublicationDeduplicator.extractFirstAuthorSurname(null)).isEmpty();
    }
}
