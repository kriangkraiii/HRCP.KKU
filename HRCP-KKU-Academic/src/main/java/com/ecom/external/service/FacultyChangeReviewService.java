package com.ecom.external.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.FsFacultyChange;
import com.ecom.external.repository.FsFacultyChangeRepository;
import com.ecom.external.repository.FsFacultyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Applies or discards the faculty edits the sync has staged for review.
 *
 * <p>Approving writes the upstream values into {@link FsFaculty}; rejecting
 * leaves our copy exactly as it was. Either way the decision is recorded with
 * who made it, so a later question about why a professor's title differs from
 * the source system has an answer.
 */
@Service
public class FacultyChangeReviewService {

    private static final Logger log = LoggerFactory.getLogger(FacultyChangeReviewService.class);

    private final FsFacultyChangeRepository changeRepo;
    private final FsFacultyRepository facultyRepo;
    private final CacheManager cacheManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public FacultyChangeReviewService(FsFacultyChangeRepository changeRepo,
            FsFacultyRepository facultyRepo,
            CacheManager cacheManager) {
        this.changeRepo = changeRepo;
        this.facultyRepo = facultyRepo;
        this.cacheManager = cacheManager;
    }

    public List<FsFacultyChange> pending() {
        return changeRepo.findByStatusOrderByDetectedAtDesc(FsFacultyChange.STATUS_PENDING);
    }

    public long pendingCount() {
        return changeRepo.countByStatus(FsFacultyChange.STATUS_PENDING);
    }

    public Optional<FsFacultyChange> find(Long id) {
        return changeRepo.findById(id);
    }

    /**
     * Parses the stored diff for display.
     *
     * @return label → {"old": …, "new": …}, empty if the payload is unreadable
     */
    public Map<String, Map<String, String>> readDiff(FsFacultyChange change) {
        if (change.getDiffJson() == null || change.getDiffJson().isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(change.getDiffJson(),
                    new TypeReference<LinkedHashMap<String, Map<String, String>>>() {
                    });
        } catch (Exception e) {
            log.warn("Unreadable diff on change {}: {}", change.getId(), e.toString());
            return Map.of();
        }
    }

    /** Diffs for a whole list, keyed by change id — saves the view from N parses. */
    public Map<Long, Map<String, Map<String, String>>> readDiffs(List<FsFacultyChange> changes) {
        Map<Long, Map<String, Map<String, String>>> out = new LinkedHashMap<>();
        for (FsFacultyChange c : changes) {
            out.put(c.getId(), readDiff(c));
        }
        return out;
    }

    /**
     * Writes the proposed values into the faculty record.
     *
     * @return true when applied; false if the change was already decided or the
     *         faculty record has since disappeared
     */
    @Transactional
    public boolean approve(Long changeId, String adminEmail, String note) {
        FsFacultyChange change = changeRepo.findById(changeId).orElse(null);
        if (change == null || !change.isPending()) {
            return false;
        }

        FsFaculty faculty = facultyRepo.findById(change.getFsUserId()).orElse(null);
        if (faculty == null) {
            log.warn("Cannot apply change {} — faculty {} is no longer stored", changeId, change.getFsUserId());
            return false;
        }

        JsonNode proposed = parse(change.getProposedJson());
        if (proposed == null) {
            log.error("Change {} has an unreadable proposed payload; refusing to apply", changeId);
            return false;
        }

        FsFacultyMapper.apply(faculty, proposed);
        facultyRepo.save(faculty);

        change.setStatus(FsFacultyChange.STATUS_APPROVED);
        change.setReviewedAt(LocalDateTime.now());
        change.setReviewedBy(adminEmail);
        change.setReviewNote(note);
        changeRepo.save(change);

        // The e-mail → faculty mapping is cached for publication scoping; an
        // approved e-mail change must not keep resolving to the old record.
        evictFacultyCache();

        log.info("Faculty change {} for user {} approved by {}", changeId, change.getFsUserId(), adminEmail);
        return true;
    }

    /** Keeps our stored values and closes the request. */
    @Transactional
    public boolean reject(Long changeId, String adminEmail, String note) {
        FsFacultyChange change = changeRepo.findById(changeId).orElse(null);
        if (change == null || !change.isPending()) {
            return false;
        }

        change.setStatus(FsFacultyChange.STATUS_REJECTED);
        change.setReviewedAt(LocalDateTime.now());
        change.setReviewedBy(adminEmail);
        change.setReviewNote(note);
        changeRepo.save(change);

        log.info("Faculty change {} for user {} rejected by {}", changeId, change.getFsUserId(), adminEmail);
        return true;
    }

    /** Approves everything currently pending. */
    @Transactional
    public int approveAll(String adminEmail) {
        int applied = 0;
        for (FsFacultyChange change : pending()) {
            if (approve(change.getId(), adminEmail, "อนุมัติทั้งหมด")) {
                applied++;
            }
        }
        return applied;
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private void evictFacultyCache() {
        var cache = cacheManager.getCache("fsFacultyByEmail");
        if (cache != null) {
            cache.clear();
        }
    }
}
