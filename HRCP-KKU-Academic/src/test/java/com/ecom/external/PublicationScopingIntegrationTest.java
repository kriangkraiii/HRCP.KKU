package com.ecom.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;

import com.ecom.external.dto.PublicationDto;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.ScopusQueryService;
import com.ecom.model.UserDtls;

/**
 * End-to-end check that the owner scoping holds against a real database, not
 * just a mocked repository — the JPQL filters are where a mistake would
 * actually leak data.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pubscope;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        // Never let a test reach the campus API.
        "fs.api.enabled=false",
        "fs.sync.on-startup=false"
})
class PublicationScopingIntegrationTest {

    private static final String SOMCHAI = "somchai@kku.ac.th";
    private static final String MALEE = "malee@kku.ac.th";

    @Autowired
    private ScopusQueryService scopusQuery;

    @Autowired
    private FsFacultyRepository facultyRepo;

    @Autowired
    private ScopusPublicationRepository publicationRepo;

    @BeforeEach
    void seed() {
        publicationRepo.deleteAll();
        facultyRepo.deleteAll();

        facultyRepo.save(faculty(1001L, SOMCHAI));
        // Stored with the trailing space the upstream feed really sends.
        facultyRepo.save(faculty(1002L, MALEE + " "));

        publicationRepo.save(publication(1001L, "2-s2.0-A", "Somchai paper A", 2023, 10));
        publicationRepo.save(publication(1001L, "2-s2.0-B", "Somchai paper B", 2024, 4));
        publicationRepo.save(publication(1002L, "2-s2.0-C", "Malee paper C", 2024, 99));
    }

    private FsFaculty faculty(long id, String email) {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(id);
        f.setEmail(email);
        f.setScopusId("s" + id);
        f.setSyncedAt(LocalDateTime.now());
        return f;
    }

    private ScopusPublication publication(long owner, String eid, String title, int year, int cited) {
        ScopusPublication p = new ScopusPublication();
        p.setFsUserId(owner);
        p.setEid(eid);
        p.setTitle(title);
        p.setPublicationYear(year);
        p.setCitedBy(cited);
        p.setSyncedAt(LocalDateTime.now());
        return p;
    }

    private UserDtls user(String email) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        return u;
    }

    @Test
    @DisplayName("อาจารย์เห็นเฉพาะผลงานของตัวเอง ไม่เห็นของอาจารย์คนอื่น")
    void eachProfessorSeesOnlyTheirOwnPublications() {
        Page<PublicationDto> somchai = scopusQuery.listOwn(user(SOMCHAI), null, null, null, 0, 50);
        Page<PublicationDto> malee = scopusQuery.listOwn(user(MALEE), null, null, null, 0, 50);

        assertThat(somchai.getContent()).extracting(PublicationDto::title)
                .containsExactlyInAnyOrder("Somchai paper A", "Somchai paper B");
        assertThat(malee.getContent()).extracting(PublicationDto::title)
                .containsExactly("Malee paper C");
    }

    @Test
    @DisplayName("อีเมลต้นทางที่มีช่องว่างต่อท้ายต้องยังจับคู่ได้")
    void trailingSpaceInUpstreamEmailStillMatches() {
        // Malee's stored address is "malee@kku.ac.th " — a plain equality match
        // would return nothing and silently hide her own work from her.
        Page<PublicationDto> malee = scopusQuery.listOwn(user(MALEE), null, null, null, 0, 50);

        assertThat(malee.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("ขอผลงานของคนอื่นด้วย id ตรงๆ ต้องไม่ได้ข้อมูล")
    void cannotFetchAnotherProfessorsPublicationById() {
        Long maleesPublicationId = publicationRepo
                .findByFsUserIdOrderByPublicationYearDescCitedByDesc(1002L)
                .get(0).getId();

        assertThat(scopusQuery.findOwn(user(SOMCHAI), maleesPublicationId)).isEmpty();
        assertThat(scopusQuery.findOwn(user(MALEE), maleesPublicationId)).isPresent();
    }

    @Test
    @DisplayName("ขอหลาย id พร้อมกัน ต้องได้เฉพาะของตัวเอง")
    void bulkFetchReturnsOnlyOwnedRows() {
        List<Long> everyId = publicationRepo.findAll().stream().map(ScopusPublication::getId).toList();

        List<PublicationDto> somchaiSees = scopusQuery.findOwnedByIds(user(SOMCHAI), everyId);

        assertThat(somchaiSees).hasSize(2);
        assertThat(somchaiSees).extracting(PublicationDto::title)
                .doesNotContain("Malee paper C");
    }

    @Test
    @DisplayName("ตัวเลขสรุป Scopus ต้องนับเฉพาะผลงานของตัวเอง")
    void metricsCountOnlyOwnPublications() {
        ScopusQueryService.ScopusMetrics somchai = scopusQuery.metricsFor(user(SOMCHAI));
        ScopusQueryService.ScopusMetrics malee = scopusQuery.metricsFor(user(MALEE));

        assertThat(somchai.papers()).isEqualTo(2);
        assertThat(somchai.citations()).isEqualTo(14);   // 10 + 4, Malee's 99 excluded
        assertThat(somchai.hIndex()).isEqualTo(2);

        assertThat(malee.papers()).isEqualTo(1);
        assertThat(malee.citations()).isEqualTo(99);
    }

    @Test
    @DisplayName("บัญชีที่ไม่มีคู่ในระบบต้นทางต้องได้ผลว่าง")
    void accountWithoutUpstreamRecordSeesNothing() {
        Page<PublicationDto> result = scopusQuery.listOwn(user("stranger@example.com"), null, null, null, 0, 50);

        assertThat(result.getTotalElements()).isZero();
        assertThat(scopusQuery.metricsFor(user("stranger@example.com")).papers()).isZero();
    }

    @Test
    @DisplayName("ค้นหาและกรองปีต้องยังคงจำกัดอยู่แค่ผลงานของตัวเอง")
    void searchAndYearFilterStayWithinOwnership() {
        // A query that matches Malee's title must still return nothing for Somchai.
        Page<PublicationDto> result = scopusQuery.listOwn(user(SOMCHAI), null, null, "Malee", 0, 50);
        assertThat(result.getContent()).isEmpty();

        Page<PublicationDto> byYear = scopusQuery.listOwn(user(SOMCHAI), 2024, 2024, null, 0, 50);
        assertThat(byYear.getContent()).extracting(PublicationDto::title).containsExactly("Somchai paper B");
    }
}
