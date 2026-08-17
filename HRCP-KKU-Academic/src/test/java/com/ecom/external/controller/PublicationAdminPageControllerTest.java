package com.ecom.external.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.ecom.external.dto.PublicationDto;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.ScopusQueryService;
import com.ecom.external.service.ScopusQueryService.AdminPublication;

/**
 * A faculty-wide publication list is the one screen that deliberately reads
 * across every professor, so the things worth pinning are that it asks for
 * exactly the slice the filters describe, and that paging keeps that slice.
 */
class PublicationAdminPageControllerTest {

    private final ScopusQueryService scopusQuery = mock(ScopusQueryService.class);
    private final ScopusPublicationRepository publicationRepo = mock(ScopusPublicationRepository.class);
    private final FsFacultyRepository facultyRepo = mock(FsFacultyRepository.class);

    private final PublicationAdminPageController controller =
            new PublicationAdminPageController(scopusQuery, publicationRepo, facultyRepo);

    private final Model model = new ExtendedModelMap();

    private FsFaculty faculty(long id, String first, String last) {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(id);
        f.setFirstName(first);
        f.setLastName(last);
        f.setPositionTitle("ผศ.");
        return f;
    }

    private AdminPublication row(long ownerId, String title) {
        ScopusPublication p = new ScopusPublication();
        p.setId(1L);
        p.setEid("2-s2.0-1");
        p.setTitle(title);
        p.setFsUserId(ownerId);
        return AdminPublication.from(p);
    }

    @BeforeEach
    void setUp() {
        when(facultyRepo.findAll()).thenReturn(List.of(
                faculty(1001L, "สมชาย", "ใจดี"),
                faculty(1002L, "มาลี", "ดีใจ")));
        when(publicationRepo.findDistinctAuthorIds()).thenReturn(List.of(1001L));
        when(publicationRepo.findDistinctYears()).thenReturn(List.of(2025, 2024));
        when(scopusQuery.adminSearchWithOwner(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new PageImpl<>(List.of(row(1001L, "งานวิจัยหนึ่ง"))));
    }

    @Test
    @DisplayName("ไม่ใส่ตัวกรอง ต้องค้นทั้งคณะ")
    void withoutFiltersItSearchesEveryone() {
        controller.page(null, null, null, 0, model);

        verify(scopusQuery).adminSearchWithOwner(isNull(), isNull(), isNull(), isNull(), eq(0), eq(25));
        assertThat(model.getAttribute("publications")).isNotNull();
    }

    @Test
    @DisplayName("เลือกปี ต้องกรองเฉพาะปีนั้น (ทั้งขอบล่างและขอบบน)")
    void pickingAYearNarrowsToThatYearOnly() {
        controller.page("deep learning", 1001L, 2024, 1, model);

        verify(scopusQuery).adminSearchWithOwner(eq(1001L), eq(2024), eq(2024),
                eq("deep learning"), eq(1), eq(25));
    }

    @Test
    @DisplayName("ลิงก์เปลี่ยนหน้าต้องพาตัวกรองไปด้วย")
    void thePagerCarriesTheFiltersAlong() {
        controller.page("machine learning", 1001L, 2024, 0, model);

        String filterQuery = (String) model.getAttribute("filterQuery");
        assertThat(filterQuery)
                .as("ถ้าไม่พาไป หน้า 2 จะกลายเป็นผลลัพธ์คนละชุดโดยที่ผู้ใช้ไม่รู้")
                .contains("machine+learning")
                .contains("&fsUserId=1001")
                .contains("&year=2024");
    }

    @Test
    @DisplayName("ตัวเลือกเจ้าของผลงาน ต้องมีเฉพาะคนที่มีผลงานจริง")
    void theAuthorFilterOffersOnlyPeopleWhoHavePublications() {
        controller.page(null, null, null, 0, model);

        @SuppressWarnings("unchecked")
        Map<Long, String> options = (Map<Long, String>) model.getAttribute("authorOptions");
        assertThat(options).containsOnlyKeys(1001L);
        assertThat(options.get(1001L)).contains("สมชาย");

        // ...while the name lookup still covers everyone, so a row whose owner has
        // no other publication still shows a name.
        @SuppressWarnings("unchecked")
        Map<Long, String> names = (Map<Long, String>) model.getAttribute("authorNames");
        assertThat(names).containsKeys(1001L, 1002L);
    }

    @Test
    @DisplayName("หน้าอ่านรายละเอียด ต้องส่งบทคัดย่อและคำสำคัญมาให้อ่าน")
    void theReadingViewCarriesTheAbstractAndKeywords() {
        ScopusPublication p = new ScopusPublication();
        p.setId(9L);
        p.setEid("2-s2.0-9");
        p.setTitle("Thai Elderly Speech Recognition");
        p.setFsUserId(1001L);
        p.setAbstractText("Thai ASR needs more research due to tonal variation.");
        p.setAuthKeywords("[\"speech recognition\",\"transfer learning\"]");
        when(scopusQuery.findForAdmin(9L)).thenReturn(java.util.Optional.of(AdminPublication.from(p)));
        when(scopusQuery.rawById(9L)).thenReturn(java.util.Optional.of(p));

        String view = controller.detail(9L, model);

        assertThat(view).isEqualTo("admin/publication_detail");
        AdminPublication row = (AdminPublication) model.getAttribute("row");
        assertThat(row).isNotNull();
        assertThat(row.hasAbstract()).isTrue();
        assertThat(row.abstractText()).contains("tonal variation");
        assertThat(row.keywords())
                .as("คำสำคัญมาเป็น JSON array ในสตริง ต้องแตกออกมาเป็นคำ ไม่ใช่โชว์วงเล็บ")
                .containsExactly("speech recognition", "transfer learning");
        assertThat(model.getAttribute("ownerName")).isEqualTo("ผศ. สมชาย ใจดี");
    }

    @Test
    @DisplayName("เปิดผลงานที่ไม่มีอยู่ ต้องกลับไปหน้ารายการ ไม่ใช่หน้าพัง")
    void openingSomethingThatIsNotThereGoesBackToTheList() {
        when(scopusQuery.findForAdmin(404L)).thenReturn(java.util.Optional.empty());

        assertThat(controller.detail(404L, model)).isEqualTo("redirect:/admin/publications");
    }

    @Test
    @DisplayName("คำสำคัญที่อ่านไม่ออก ต้องไม่ทำให้หน้าพัง")
    void unreadableKeywordsDegradeQuietly() {
        ScopusPublication p = new ScopusPublication();
        p.setId(10L);
        p.setEid("2-s2.0-10");
        p.setFsUserId(1001L);
        p.setAuthKeywords("ไม่ใช่ JSON");

        assertThat(AdminPublication.from(p).keywords()).isEmpty();
    }

    @Test
    @DisplayName("หน้าเว็บต้องเป็นของแอดมินเท่านั้น")
    void thePageIsAdminOnly() {
        var annotation = PublicationAdminPageController.class
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("ADMIN");
    }
}
