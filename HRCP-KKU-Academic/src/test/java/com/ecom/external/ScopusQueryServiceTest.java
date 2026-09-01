package com.ecom.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.ecom.academic.repository.PositionRequestPublicationRepository;
import com.ecom.external.dto.PublicationDto;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.ScopusQueryService;
import com.ecom.model.UserDtls;

/**
 * The privacy requirement for this feature is that a professor sees their own
 * publications and nobody else's. These tests pin that down, plus the h-index
 * arithmetic the position forms depend on.
 */
class ScopusQueryServiceTest {

    private final ScopusPublicationRepository publicationRepo = mock(ScopusPublicationRepository.class);
    private final FsFacultyRepository facultyRepo = mock(FsFacultyRepository.class);
    private final PositionRequestPublicationRepository linkRepo =
            mock(PositionRequestPublicationRepository.class);
    private final ScopusQueryService service =
            new ScopusQueryService(publicationRepo, facultyRepo, linkRepo);

    private UserDtls userWithEmail(String email) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        return u;
    }

    private FsFaculty faculty(long id, String email) {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(id);
        f.setEmail(email);
        return f;
    }

    @Test
    @DisplayName("ดึงผลงานต้องผูกกับ fs_user_id ของผู้ใช้ที่ล็อกอินเท่านั้น")
    void listOwnQueriesOnlyTheCallersFacultyId() {
        UserDtls user = userWithEmail("somchai@kku.ac.th");
        when(facultyRepo.findByEmailNormalized("somchai@kku.ac.th"))
                .thenReturn(Optional.of(faculty(1001L, "somchai@kku.ac.th")));
        when(publicationRepo.findOwnedBy(eq(1001L), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listOwn(user, null, null, null, 0, 50);

        // The owner id must come from the resolved faculty record, never a parameter.
        verify(publicationRepo).findOwnedBy(eq(1001L), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("ผู้ใช้ที่จับคู่กับระบบต้นทางไม่ได้ ต้องได้ผลลัพธ์ว่าง ไม่ใช่ของคนอื่น")
    void unlinkedUserGetsEmptyResultRatherThanSomeoneElsesRows() {
        UserDtls user = userWithEmail("nobody@example.com");
        when(facultyRepo.findByEmailNormalized("nobody@example.com")).thenReturn(Optional.empty());

        Page<PublicationDto> result = service.listOwn(user, null, null, null, 0, 50);

        assertThat(result.getTotalElements()).isZero();
        // Critically: no repository call at all, so there is no chance of a
        // null owner id turning into "match every row".
        verify(publicationRepo, never()).findOwnedBy(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("ดึงผลงานรายชิ้นต้องกรองด้วยเจ้าของในคิวรี ไม่ใช่เช็คทีหลัง")
    void findOwnFiltersByOwnerInsideTheQuery() {
        UserDtls user = userWithEmail("somchai@kku.ac.th");
        when(facultyRepo.findByEmailNormalized("somchai@kku.ac.th"))
                .thenReturn(Optional.of(faculty(1001L, "somchai@kku.ac.th")));
        when(publicationRepo.findByIdAndFsUserId(77L, 1001L)).thenReturn(Optional.empty());

        Optional<PublicationDto> result = service.findOwn(user, 77L);

        assertThat(result).isEmpty();
        verify(publicationRepo).findByIdAndFsUserId(77L, 1001L);
        verify(publicationRepo, never()).findById(any());
    }

    @Test
    @DisplayName("ขอผลงานหลายชิ้นพร้อมกัน ต้องตัดรายการที่ไม่ใช่ของตัวเองทิ้ง")
    void findOwnedByIdsDropsForeignIds() {
        UserDtls user = userWithEmail("somchai@kku.ac.th");
        when(facultyRepo.findByEmailNormalized("somchai@kku.ac.th"))
                .thenReturn(Optional.of(faculty(1001L, "somchai@kku.ac.th")));

        ScopusPublication mine = new ScopusPublication();
        mine.setId(10L);
        mine.setFsUserId(1001L);
        mine.setEid("2-s2.0-1");
        mine.setTitle("Mine");

        when(publicationRepo.findByIdAndFsUserId(10L, 1001L)).thenReturn(Optional.of(mine));
        // 99 belongs to another professor, so the owner-scoped finder misses it.
        when(publicationRepo.findByIdAndFsUserId(99L, 1001L)).thenReturn(Optional.empty());

        List<PublicationDto> result = service.findOwnedByIds(user, List.of(10L, 99L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Mine");
    }

    @Test
    @DisplayName("อีเมลที่มีช่องว่าง/ตัวพิมพ์ใหญ่ต้องยังจับคู่กับข้อมูลต้นทางได้")
    void emailMatchingSurvivesWhitespaceAndCase() {
        // The upstream feed really does contain "user@kku.ac.th " with a trailing
        // space; normalisation happens in the query, so the service just passes through.
        when(facultyRepo.findByEmailNormalized("  Somchai@KKU.ac.th "))
                .thenReturn(Optional.of(faculty(1001L, "somchai@kku.ac.th")));

        FsFaculty found = service.resolveFaculty("  Somchai@KKU.ac.th ");

        assertThat(found).isNotNull();
        assertThat(found.getFsUserId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("h-index คำนวณถูกต้องตามนิยาม")
    void hIndexFollowsTheDefinition() {
        // 6 papers cited 10,8,5,4,3,0 → h = 4 (four papers with ≥4 citations)
        assertThat(ScopusQueryService.hIndex(List.of(10, 8, 5, 4, 3, 0))).isEqualTo(4);
        assertThat(ScopusQueryService.hIndex(List.of())).isZero();
        assertThat(ScopusQueryService.hIndex(List.of(0, 0, 0))).isZero();
        // Every paper beats its rank, so h is capped by the paper count.
        assertThat(ScopusQueryService.hIndex(List.of(9, 9, 9))).isEqualTo(3);
        assertThat(ScopusQueryService.hIndex(List.of(1))).isEqualTo(1);
    }

    @Test
    @DisplayName("DTO ต้องไม่พก fs_user_id หรือ raw_json ออกไปฝั่ง client")
    void dtoDoesNotCarryOwnerIdOrRawPayload() {
        ScopusPublication p = new ScopusPublication();
        p.setId(1L);
        p.setFsUserId(1001L);
        p.setEid("2-s2.0-1");
        p.setTitle("T");
        p.setRawJson("{\"secret\":true}");

        String serialized = PublicationDto.from(p).toString();

        assertThat(serialized).doesNotContain("1001");
        assertThat(serialized).doesNotContain("secret");
    }

    @Test
    @DisplayName("สร้างรายการอ้างอิงตามรูปแบบที่เอกสารต้องการ")
    void buildsCitationString() {
        ScopusPublication p = new ScopusPublication();
        p.setId(1L);
        p.setEid("2-s2.0-1");
        p.setAuthorNames("Arch-Int N. | Doe J.");
        p.setPublicationYear(2024);
        p.setTitle("A Knowledge-Driven Approach");
        p.setPublicationName("Applied Sciences");
        p.setVolume("14");
        p.setIssue("13");
        p.setArticleNumber("5866");
        p.setDoi("10.3390/app14135866");

        String citation = PublicationDto.from(p).toCitation();

        assertThat(citation).isEqualTo(
                "Arch-Int N., Doe J. (2024). A Knowledge-Driven Approach. Applied Sciences, 14(13), 5866. "
                        + "https://doi.org/10.3390/app14135866.");
    }

    @Test
    @DisplayName("เลือกเลขหน้า: ใช้ page_range ก่อน ถ้าไม่มีจึงใช้ article_number")
    void prefersPageRangeOverArticleNumber() {
        ScopusPublication withPages = new ScopusPublication();
        withPages.setId(1L);
        withPages.setEid("e1");
        withPages.setPageRange("120-135");
        withPages.setArticleNumber("5866");

        assertThat(PublicationDto.from(withPages).pages()).isEqualTo("120-135");
    }

    @Test
    @DisplayName("adminSearch ต้องเป็นเมธอดแยก ไม่ปนกับเส้นทางของอาจารย์")
    void adminSearchIsSeparateFromOwnerScopedReads() {
        when(publicationRepo.adminSearch(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.adminSearch(null, "x", 0, 10);

        verify(publicationRepo).adminSearch(eq(null), eq(null), eq(null), eq("%x%"), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("บัญชีที่ตรงกับ app.research.universal-access-email ต้องค้นหาผลงานทั้งหมดได้")
    void testUserCanSearchAllPublications() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "universalAccessEmail", "demo@example.invalid");
        UserDtls testUser = userWithEmail("demo@example.invalid");
        when(publicationRepo.adminSearch(eq(null), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<PublicationDto> results = service.listOwn(testUser, null, null, null, 0, 50);

        assertThat(results).isNotNull();
        verify(publicationRepo).adminSearch(eq(null), any(), any(), any(), any(), any(Pageable.class));
        verify(publicationRepo, never()).findOwnedBy(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("อาจารย์ทั่วไปที่ไม่ใช่บัญชีที่ตั้งค่าไว้ ต้องค้นหาได้เฉพาะผลงานของตนเองเท่านั้น")
    void regularTeacherIsStrictlyFilteredByOwnFsUserId() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "universalAccessEmail", "demo@example.invalid");
        UserDtls teacher = userWithEmail("teacher@kku.ac.th");
        when(facultyRepo.findByEmailNormalized("teacher@kku.ac.th"))
                .thenReturn(Optional.of(faculty(999L, "teacher@kku.ac.th")));
        when(publicationRepo.findOwnedBy(eq(999L), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listOwn(teacher, null, null, null, 0, 50);

        verify(publicationRepo).findOwnedBy(eq(999L), any(), any(), any(), any(), any(Pageable.class));
        verify(publicationRepo, never()).adminSearch(any(), any(), any(), any(), any(), any());
    }
}
