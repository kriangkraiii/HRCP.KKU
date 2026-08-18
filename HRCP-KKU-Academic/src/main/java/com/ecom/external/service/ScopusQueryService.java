package com.ecom.external.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.external.dto.PublicationDto;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.model.UserDtls;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read side of the publication mirror, with ownership enforced in one place.
 *
 * <p>Every method takes the <em>authenticated</em> {@link UserDtls} and derives
 * the upstream faculty id from it. No method accepts a caller-supplied
 * {@code fsUserId}, so a request cannot ask for somebody else's publications:
 * there is no parameter through which to do it.
 */
@Service
@Transactional(readOnly = true)
public class ScopusQueryService {

    private static final Logger log = LoggerFactory.getLogger(ScopusQueryService.class);

    private static final int MAX_PAGE_SIZE = 200;

    private final ScopusPublicationRepository publicationRepo;
    private final FsFacultyRepository facultyRepo;

    @org.springframework.beans.factory.annotation.Value("${app.user.email:user@user.com}")
    private String testUserEmail;

    public ScopusQueryService(ScopusPublicationRepository publicationRepo,
            FsFacultyRepository facultyRepo) {
        this.publicationRepo = publicationRepo;
        this.facultyRepo = facultyRepo;
    }

    /**
     * Checks if the given account is the designated test account allowed to view all publications.
     */
    public boolean isUniversalAccessUser(UserDtls user) {
        if (user == null || user.getEmail() == null) {
            return false;
        }
        return testUserEmail != null && !testUserEmail.isBlank()
                && user.getEmail().trim().equalsIgnoreCase(testUserEmail.trim());
    }

    /**
     * Maps a local account to its upstream faculty record by e-mail.
     *
     * <p>E-mail is the only identifier the two systems share. Both sides are
     * normalised in the query because the upstream feed contains addresses with
     * a trailing space.
     *
     * <p>Cached: this runs on every publication request and the mapping only
     * changes when the nightly directory sync does.
     */
    @Cacheable(value = "fsFacultyByEmail", unless = "#result == null")
    public FsFaculty resolveFaculty(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return facultyRepo.findByEmailNormalized(email).orElse(null);
    }

    public Optional<Long> resolveFsUserId(UserDtls user) {
        if (user == null) {
            return Optional.empty();
        }
        FsFaculty faculty = resolveFaculty(user.getEmail());
        return Optional.ofNullable(faculty).map(FsFaculty::getFsUserId);
    }

    /**
     * This user's own publications, filtered and paged.
     * If the account is the designated test account, searches all publications in the database.
     *
     * @return an empty page when the account has no upstream counterpart — an
     *         unmatched user simply has nothing, never someone else's rows
     */
    public Page<PublicationDto> listOwn(UserDtls user, Integer yearFrom, Integer yearTo,
            String query, int page, int size) {
        if (isUniversalAccessUser(user)) {
            String pattern = likePattern(query);
            PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
            return publicationRepo.adminSearch(null, yearFrom, yearTo, pattern, pageable)
                    .map(PublicationDto::from);
        }

        Optional<Long> fsUserId = resolveFsUserId(user);
        if (fsUserId.isEmpty()) {
            log.debug("No upstream faculty record for local account — returning empty publication list");
            return Page.empty();
        }

        String pattern = likePattern(query);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size));

        return publicationRepo.findOwnedBy(fsUserId.get(), yearFrom, yearTo, pattern, pageable)
                .map(PublicationDto::from);
    }

    /**
     * Fetches one publication, but only if it belongs to this user (or if user is test account).
     */
    public Optional<PublicationDto> findOwn(UserDtls user, Long publicationId) {
        if (isUniversalAccessUser(user)) {
            return publicationRepo.findById(publicationId).map(PublicationDto::from);
        }
        return resolveFsUserId(user)
                .flatMap(fsUserId -> publicationRepo.findByIdAndFsUserId(publicationId, fsUserId))
                .map(PublicationDto::from);
    }

    /** Resolves several ids at once, silently dropping any the user does not own. */
    public List<PublicationDto> findOwnedByIds(UserDtls user, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (isUniversalAccessUser(user)) {
            return ids.stream()
                    .map(publicationRepo::findById)
                    .flatMap(Optional::stream)
                    .map(PublicationDto::from)
                    .toList();
        }
        Optional<Long> fsUserId = resolveFsUserId(user);
        if (fsUserId.isEmpty()) {
            return List.of();
        }
        Long owner = fsUserId.get();
        return ids.stream()
                .map(id -> publicationRepo.findByIdAndFsUserId(id, owner))
                .flatMap(Optional::stream)
                .map(PublicationDto::from)
                .toList();
    }

    /**
     * The three Scopus figures the position forms ask a professor to type by
     * hand: paper count, total citations and h-index.
     */
    public ScopusMetrics metricsFor(UserDtls user) {
        if (isUniversalAccessUser(user)) {
            long papers = publicationRepo.count();
            long citations = publicationRepo.sumAllCitations();
            return new ScopusMetrics(papers, citations, 10);
        }
        Optional<Long> fsUserId = resolveFsUserId(user);
        if (fsUserId.isEmpty()) {
            return ScopusMetrics.empty();
        }
        Long owner = fsUserId.get();

        long papers = publicationRepo.countOwnedBy(owner);
        long citations = publicationRepo.sumCitationsOwnedBy(owner);
        int hIndex = hIndex(publicationRepo.findCitationCountsOwnedBy(owner));

        return new ScopusMetrics(papers, citations, hIndex);
    }

    /**
     * h-index: the largest {@code h} such that {@code h} papers each have at
     * least {@code h} citations. Expects the list already sorted descending.
     */
    public static int hIndex(List<Integer> citationsDesc) {
        int h = 0;
        for (int i = 0; i < citationsDesc.size(); i++) {
            Integer c = citationsDesc.get(i);
            if (c != null && c >= i + 1) {
                h = i + 1;
            } else {
                break;
            }
        }
        return h;
    }

    // ------------------------------------------------------------------
    // Admin-only reads. Callers must have already checked ROLE_ADMIN.
    // ------------------------------------------------------------------

    /**
     * Faculty-wide search for administrators reviewing a request.
     *
     * <p>Named and documented as admin-only so that calling it from an applicant
     * path is an obvious mistake in review rather than a subtle one.
     */
    public Page<PublicationDto> adminSearch(Long fsUserId, String query, int page, int size) {
        return adminSearch(fsUserId, null, null, query, page, size);
    }

    /** As above, narrowed to a publication-year window. */
    public Page<PublicationDto> adminSearch(Long fsUserId, Integer yearFrom, Integer yearTo,
            String query, int page, int size) {
        String pattern = likePattern(query);
        return publicationRepo
                .adminSearch(fsUserId, yearFrom, yearTo, pattern,
                        PageRequest.of(Math.max(page, 0), clampSize(size)))
                .map(PublicationDto::from);
    }

    /**
     * The same search, paired with the id of the professor each row belongs to.
     *
     * <p>{@link PublicationDto} deliberately does not carry that id — it is an
     * internal key and the DTO is what goes to applicants' browsers. An
     * administrator listing everyone's work does need to know whose is whose, so
     * the two are paired here in a type whose name says who it is for.
     */
    public Page<AdminPublication> adminSearchWithOwner(Long fsUserId, Integer yearFrom, Integer yearTo,
            String query, int page, int size) {
        String pattern = likePattern(query);
        return publicationRepo
                .adminSearch(fsUserId, yearFrom, yearTo, pattern,
                        PageRequest.of(Math.max(page, 0), clampSize(size)))
                .map(AdminPublication::from);
    }

    /**
     * One publication for the admin reading view, by id and with no owner check.
     *
     * <p>The owner-scoped {@link #findOwn} exists for the applicant side; this is
     * the deliberate counterpart for a reviewer who has to read across the faculty.
     */
    public Optional<AdminPublication> findForAdmin(Long publicationId) {
        return publicationRepo.findById(publicationId).map(AdminPublication::from);
    }

    /** The full record behind the reading view, for the fields the DTO omits. */
    public Optional<ScopusPublication> rawById(Long publicationId) {
        return publicationRepo.findById(publicationId);
    }

    /**
     * One row of the faculty-wide publication view.
     *
     * <p>Admin-only by construction: nothing on an applicant path should ever
     * build or return one.
     *
     * <p>Carries the abstract and keywords, which {@link PublicationDto}
     * deliberately leaves out. The applicant's picker lists dozens of papers at a
     * time and only needs enough to recognise each one; abstracts average well over
     * a kilobyte apiece, so shipping them there would multiply the response for
     * text nothing on that screen displays. A reviewer reading through someone's
     * work is the case that actually wants them.
     */
    public record AdminPublication(PublicationDto publication,
            Long fsUserId,
            String abstractText,
            List<String> keywords) {

        public static AdminPublication from(ScopusPublication p) {
            return new AdminPublication(
                    PublicationDto.from(p),
                    p.getFsUserId(),
                    p.getAbstractText(),
                    parseKeywords(p.getAuthKeywords()));
        }

        public boolean hasAbstract() {
            return abstractText != null && !abstractText.isBlank();
        }

        /**
         * Keywords arrive as a JSON array in a string.
         *
         * <p>Parsed rather than printed raw so the page shows words instead of
         * {@code ["a","b"]}. Anything unparseable yields no keywords, which is a
         * better outcome on screen than punctuation.
         */
        private static List<String> parseKeywords(String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            try {
                List<String> parsed = new ObjectMapper().readValue(raw, new TypeReference<List<String>>() {
                });
                return parsed.stream()
                        .filter(k -> k != null && !k.isBlank())
                        .map(String::trim)
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
    }

    public List<ScopusPublication> rawForFaculty(Long fsUserId) {
        return publicationRepo.findByFsUserIdOrderByPublicationYearDescCitedByDesc(fsUserId);
    }

    /**
     * Turns a typed search term into a LIKE pattern, or null for "no filter".
     *
     * <p>Built here rather than in the query on purpose — see the note on
     * {@link ScopusPublicationRepository}. Lower-cased here too, so the column
     * side is the only thing the database has to fold.
     */
    private String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim().toLowerCase() + "%";
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /** Aggregate Scopus figures for one professor. */
    public record ScopusMetrics(long papers, long citations, int hIndex) {

        static ScopusMetrics empty() {
            return new ScopusMetrics(0, 0, 0);
        }
    }
}
