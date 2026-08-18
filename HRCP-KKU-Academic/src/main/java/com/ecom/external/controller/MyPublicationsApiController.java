package com.ecom.external.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.external.dto.PublicationDto;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.service.EnglishNameSplitter;
import com.ecom.external.service.ScopusQueryService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Read-only API backing the publication picker and the profile autofill.
 *
 * <p>Everything here is scoped to the caller. The owning faculty id is derived
 * from {@link Principal} on the server — it is never accepted as a parameter, so
 * no request shape exists that could return another professor's publications.
 */
@RestController
@RequestMapping("/api/my")
public class MyPublicationsApiController {

    private static final Logger log = LoggerFactory.getLogger(MyPublicationsApiController.class);

    private final ScopusQueryService scopusQuery;
    private final UserRepository userRepository;

    public MyPublicationsApiController(ScopusQueryService scopusQuery, UserRepository userRepository) {
        this.scopusQuery = scopusQuery;
        this.userRepository = userRepository;
    }

    /**
     * This professor's own publications.
     *
     * @param q        free-text filter over title, journal and DOI
     * @param yearFrom inclusive lower bound on publication year
     */
    @GetMapping("/publications")
    public ResponseEntity<Map<String, Object>> myPublications(
            @RequestParam(required = false) String q,
            @RequestParam(name = "year_from", required = false) Integer yearFrom,
            @RequestParam(name = "year_to", required = false) Integer yearTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Principal principal) {

        UserDtls user = currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Page<PublicationDto> result = scopusQuery.listOwn(user, yearFrom, yearTo, q, page, size);

        Map<String, Object> body = new HashMap<>();
        body.put("data", result.getContent());
        body.put("total", result.getTotalElements());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("linked", scopusQuery.isUniversalAccessUser(user) || scopusQuery.resolveFsUserId(user).isPresent());
        return ResponseEntity.ok(body);
    }

    /**
     * Paper count, citation total and h-index — the figures the position forms
     * currently ask the professor to look up and type in by hand.
     */
    @GetMapping("/publications/metrics")
    public ResponseEntity<Map<String, Object>> myMetrics(Principal principal) {
        UserDtls user = currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        ScopusQueryService.ScopusMetrics m = scopusQuery.metricsFor(user);
        return ResponseEntity.ok(Map.of(
                "papers", m.papers(),
                "citations", m.citations(),
                "hIndex", m.hIndex()));
    }

    /**
     * Formatted citation strings for the publications the professor ticked.
     *
     * <p>Ids the caller does not own are dropped rather than rejected: the picker
     * only ever offers owned rows, so a foreign id means a stale tab or a probe,
     * and neither deserves a detailed error.
     */
    @GetMapping("/publications/citations")
    public ResponseEntity<Map<String, Object>> citations(
            @RequestParam String ids,
            Principal principal) {

        UserDtls user = currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        List<Long> requested = parseIds(ids);
        List<PublicationDto> owned = scopusQuery.findOwnedByIds(user, requested);

        List<Map<String, Object>> out = new ArrayList<>(owned.size());
        for (PublicationDto p : owned) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", p.id());
            row.put("citation", p.toCitation());
            row.put("title", p.title());
            row.put("year", p.year());
            row.put("quartile", p.quartile());
            out.add(row);
        }

        if (owned.size() != requested.size()) {
            log.warn("Citation request asked for {} publication(s) but only {} are owned by the caller",
                    requested.size(), owned.size());
        }
        return ResponseEntity.ok(Map.of("data", out));
    }

    /**
     * Directory data for pre-filling request forms.
     *
     * <p>Returns the caller's own record only — this is the source for the
     * "fill my details in automatically" behaviour, not a faculty lookup.
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> myProfile(Principal principal) {
        UserDtls user = currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        FsFaculty f = scopusQuery.resolveFaculty(user.getEmail());
        if (f == null) {
            if (scopusQuery.isUniversalAccessUser(user)) {
                Map<String, Object> body = new HashMap<>();
                body.put("linked", true);
                body.put("prefix", user.getTitle() != null ? user.getTitle() : "ดร.");
                body.put("firstName", user.getFirstName() != null ? user.getFirstName() : "ทดสอบ");
                body.put("lastName", user.getLastName() != null ? user.getLastName() : "ผู้ใช้งาน");
                body.put("displayName", user.getName());
                body.put("nameEn", user.getNameEn() != null ? user.getNameEn() : "Test User");
                body.put("firstNameEn", user.getFirstNameEn() != null ? user.getFirstNameEn() : "Test");
                body.put("lastNameEn", user.getLastNameEn() != null ? user.getLastNameEn() : "User");
                body.put("positionEn", user.getAcademicPositionEn() != null
                        ? user.getAcademicPositionEn()
                        : "Lecturer");
                body.put("positionTitle", user.getAcademicPosition() != null ? user.getAcademicPosition() : "อาจารย์");
                body.put("email", user.getEmail());
                body.put("tel", user.getMobileNumber() != null ? user.getMobileNumber() : "0812345678");
                body.put("scopusId", "57200000000");
                return ResponseEntity.ok(body);
            }
            return ResponseEntity.ok(Map.of("linked", false));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("linked", true);
        body.put("prefix", f.getPrefix());
        body.put("firstName", f.getFirstName());
        body.put("lastName", f.getLastName());
        body.put("displayName", f.getDisplayName());
        body.put("nameEn", f.getNameEn());
        // The directory has no split English name; these are derived so forms can
        // fill a given-name and family-name box separately.
        EnglishNameSplitter.Parts english = EnglishNameSplitter.split(f.getNameEn());
        body.put("firstNameEn", english.firstName());
        body.put("lastNameEn", english.lastName());
        body.put("suffixEn", f.getSuffixEn());
        body.put("positionTitle", f.getPositionTitle());
        body.put("positionEn", f.getPositionEn());
        body.put("prefixPositionEn", f.getPrefixPositionEn());
        body.put("managePosition", f.getManagePosition());
        body.put("email", f.getEmail());
        body.put("tel", f.getTel());
        body.put("telFormat", f.getTelFormat());
        body.put("labName", f.getLabName());
        body.put("room", f.getRoom());
        body.put("scopusId", f.getScopusId());
        return ResponseEntity.ok(body);
    }

    private UserDtls currentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByEmail(principal.getName());
    }

    /** Parses {@code "1,2,3"}, ignoring anything that is not a number. */
    private List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // A malformed id is simply not a publication anyone owns.
            }
        }
        return ids;
    }
}
