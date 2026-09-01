package com.ecom.external.harvest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.ScopusPublicationRepository;

/**
 * Multi-tier deduplication engine for harvested academic publications.
 *
 * <p>Levels:
 * <ul>
 *   <li><b>Level 1 (DOI)</b>: Exact case-insensitive match on normalized DOI.</li>
 *   <li><b>Level 2 (Hash)</b>: SHA-256 hash of normalized title + year + first author's surname.</li>
 *   <li><b>Level 2.5 (Fuzzy)</b>: PostgreSQL {@code pg_trgm} title similarity &ge; threshold within same year and author.</li>
 * </ul>
 */
@Component
public class PublicationDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(PublicationDeduplicator.class);

    private final ScopusPublicationRepository publicationRepo;

    public PublicationDeduplicator(ScopusPublicationRepository publicationRepo) {
        this.publicationRepo = publicationRepo;
    }

    public enum MatchLevel {
        LEVEL_1_DOI,
        LEVEL_2_HASH,
        LEVEL_2_5_FUZZY,
        NONE
    }

    public record DedupResult(Optional<ScopusPublication> existing, MatchLevel matchLevel, String computedHash) {
        public boolean isDuplicate() {
            return existing.isPresent();
        }
    }

    /**
     * Finds whether a harvested raw publication is a duplicate of an existing record for this faculty member.
     */
    public DedupResult findDuplicate(RawPublication raw, Long fsUserId, double fuzzyThreshold) {
        String dedupHash = computeDedupHash(raw.title(), raw.publicationYear(), raw.authorNames());

        // Level 1: DOI Exact match
        String normalizedDoi = PublicationHarmonizer.normalizeDoi(raw.doi());
        if (normalizedDoi != null && !normalizedDoi.isBlank()) {
            Optional<ScopusPublication> byDoi = publicationRepo.findByFsUserIdAndDoiIgnoreCase(fsUserId, normalizedDoi);
            if (byDoi.isPresent()) {
                log.debug("Dedup L1 (DOI) hit for user {}: DOI {}", fsUserId, normalizedDoi);
                return new DedupResult(byDoi, MatchLevel.LEVEL_1_DOI, dedupHash);
            }
        }

        // Level 2: Normalized Hash match
        if (dedupHash != null) {
            Optional<ScopusPublication> byHash = publicationRepo.findByFsUserIdAndDedupHash(fsUserId, dedupHash);
            if (byHash.isPresent()) {
                log.debug("Dedup L2 (Hash) hit for user {}: Hash {}", fsUserId, dedupHash);
                return new DedupResult(byHash, MatchLevel.LEVEL_2_HASH, dedupHash);
            }
        }

        // Level 2.5: Fuzzy pg_trgm title match (requires title and year)
        if (raw.title() != null && raw.title().length() >= 10 && raw.publicationYear() != null) {
            try {
                Optional<Long> fuzzyId = publicationRepo.findFuzzyMatchId(fsUserId, raw.title().trim(), raw.publicationYear(), fuzzyThreshold);
                if (fuzzyId.isPresent()) {
                    Optional<ScopusPublication> fuzzyPub = publicationRepo.findById(fuzzyId.get());
                    if (fuzzyPub.isPresent()) {
                        log.debug("Dedup L2.5 (Fuzzy) hit for user {}: ID {} matched '{}'", fsUserId, fuzzyId.get(), raw.title());
                        return new DedupResult(fuzzyPub, MatchLevel.LEVEL_2_5_FUZZY, dedupHash);
                    }
                }
            } catch (Exception e) {
                log.warn("Fuzzy dedup query failed (pg_trgm extension active?): {}", e.getMessage());
            }
        }

        return new DedupResult(Optional.empty(), MatchLevel.NONE, dedupHash);
    }

    /**
     * Computes the deterministic SHA-256 hash of normalized title + year + first author surname.
     */
    public static String computeDedupHash(String title, Integer year, String authorNames) {
        if (title == null || title.isBlank()) {
            return null;
        }

        String normTitle = title.toLowerCase()
                .replaceAll("[^a-z0-9\\u0E00-\\u0E7F]", "") // allow English letters, numbers, and Thai characters
                .trim();

        if (normTitle.isBlank()) {
            return null;
        }

        int y = year != null ? year : 0;
        String surname = extractFirstAuthorSurname(authorNames);

        String payload = normTitle + "|" + y + "|" + surname;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String extractFirstAuthorSurname(String authorNames) {
        if (authorNames == null || authorNames.isBlank()) {
            return "";
        }
        // Split on author separators (| or ; or "and")
        String firstAuthor = authorNames.split("[|;]|\\band\\b")[0].trim();
        if (firstAuthor.isBlank()) {
            return "";
        }
        // Extract words, lower-cased
        String[] rawTokens = firstAuthor.replaceAll("[^a-zA-Z\\u0E00-\\u0E7F\\s]", " ").toLowerCase().trim().split("\\s+");
        java.util.List<String> validTokens = new java.util.ArrayList<>();
        for (String t : rawTokens) {
            if (t.length() > 1) { // ignore single-character initials like "S."
                validTokens.add(t);
            }
        }
        if (validTokens.isEmpty()) {
            return rawTokens.length > 0 ? rawTokens[0] : "";
        }
        java.util.Collections.sort(validTokens);
        return String.join("_", validTokens);
    }
}
