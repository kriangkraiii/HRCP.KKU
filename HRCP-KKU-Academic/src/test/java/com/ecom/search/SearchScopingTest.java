package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.Notification;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.search.dto.SearchHit;
import com.ecom.search.service.SearchService;
import com.ecom.support.AbstractFlowTest;

/**
 * The security test for the whole feature.
 *
 * <p>The index is one flat table holding rows from a dozen sources, and the old
 * per-repository search is gone — so the single visibility predicate in
 * {@code SearchSqlBuilder.scopeClause()} is now the only thing standing between
 * one applicant and another's paperwork. If it is wrong, it is wrong for
 * everything at once.
 *
 * <p>These run on the portable dialect, which shares that clause verbatim with
 * the PostgreSQL one — that sharing is precisely why the builder emits both
 * flavours instead of there being two repositories.
 */
class SearchScopingTest extends AbstractFlowTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private com.ecom.search.repository.SearchDocumentRepository index;

    private UserDtls somchai;
    private UserDtls malee;
    private UserDtls admin;
    private AcademicRequest maleesRequest;

    @BeforeEach
    void seedTwoApplicantsAndAnAdmin() {
        data.reset();
        somchai = data.applicant();
        malee = data.otherApplicant();
        admin = data.admin();

        maleesRequest = data.evaluation(malee, RequestStatus.RECEIVED);
    }

    private List<String> titlesFor(String term, UserDtls as) {
        return searchService.quickSearch(term, as, 50).hits().stream()
                .map(SearchHit::title)
                .toList();
    }

    private String maleesRequestCode() {
        return maleesRequest.getRequestCode();
    }

    // ------------------------------------------------------------------
    // OWNER_OR_ADMIN — requests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an applicant finds their own request")
    void ownerFindsTheirOwn() {
        AcademicRequest mine = data.evaluation(somchai, RequestStatus.RECEIVED);

        assertThat(titlesFor(mine.getRequestCode(), somchai))
                .as("เจ้าของต้องค้นคำร้องตัวเองเจอ")
                .isNotEmpty();
    }

    @Test
    @DisplayName("an applicant cannot find another applicant's request")
    void oneApplicantCannotSeeAnother() {
        assertThat(titlesFor(maleesRequestCode(), somchai))
                .as("สมชายต้องค้นคำร้องของมาลีไม่เจอ แม้จะรู้เลขคำร้อง")
                .isEmpty();
    }

    @Test
    @DisplayName("an admin finds any applicant's request")
    void adminSeesEveryRequest() {
        assertThat(titlesFor(maleesRequestCode(), admin))
                .as("แอดมินต้องเห็นคำร้องของทุกคน")
                .isNotEmpty();
    }

    @Test
    @DisplayName("searching by another applicant's name returns nothing")
    void nameSearchIsScopedToo() {
        assertThat(titlesFor("มาลี", somchai))
                .as("ค้นด้วยชื่อคนอื่นก็ต้องไม่รั่ว ไม่ใช่แค่เลขคำร้อง")
                .isEmpty();

        assertThat(titlesFor("มาลี", admin))
                .as("แต่แอดมินค้นด้วยชื่อต้องเจอ")
                .isNotEmpty();
    }

    // ------------------------------------------------------------------
    // OWNER — notifications, which not even an admin may read
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a notification is visible only to the person it was sent to")
    void notificationsAreOwnerOnly() {
        Notification n = new Notification();
        n.setRecipient(malee);
        n.setTitle("แจ้งเตือนเฉพาะบุคคลรหัสลับมาลี");
        n.setMessage("ข้อความส่วนตัวของมาลี");
        n.setType(NotificationType.ACADEMIC_STATUS_UPDATE);
        n.setCreatedAt(LocalDateTime.now());
        notifications.save(n);

        assertThat(titlesFor("รหัสลับมาลี", malee))
                .as("ผู้รับต้องเห็นแจ้งเตือนของตัวเอง")
                .isNotEmpty();

        assertThat(titlesFor("รหัสลับมาลี", somchai))
                .as("ผู้ใช้อื่นต้องไม่เห็น")
                .isEmpty();

        assertThat(titlesFor("รหัสลับมาลี", admin))
                .as("แอดมินก็ต้องไม่เห็นแจ้งเตือนส่วนตัวของคนอื่น — แอดมินมีกล่องของตัวเอง")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // ADMIN — staff and user records
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the user directory is admin-only")
    void userRecordsAreAdminOnly() {
        // Every user is indexed as a SYSTEM_USER row with ADMIN visibility.
        List<String> asAdmin = titlesFor(malee.getEmail(), admin);
        List<String> asApplicant = titlesFor(malee.getEmail(), somchai);

        assertThat(asAdmin)
                .as("แอดมินต้องค้นรายชื่อผู้ใช้ได้")
                .isNotEmpty();
        assertThat(asApplicant)
                .as("ผู้ยื่นทั่วไปต้องไม่เห็นทะเบียนผู้ใช้")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // PUBLIC — menus
    // ------------------------------------------------------------------

    @Test
    @DisplayName("applicant menus are visible to everyone, admin menus are not")
    void navigationRespectsItsAudience() {
        assertThat(titlesFor("ยื่นขอกำหนดตำแหน่ง", somchai))
                .as("เมนูของผู้ยื่นต้องค้นเจอ")
                .isNotEmpty();

        assertThat(titlesFor("จัดการผู้ดูแลระบบ", somchai))
                .as("เมนูแอดมินต้องไม่โผล่ให้ผู้ยื่นทั่วไป")
                .isEmpty();

        assertThat(titlesFor("จัดการผู้ดูแลระบบ", admin))
                .as("แต่แอดมินต้องเห็น")
                .isNotEmpty();
    }

    // ------------------------------------------------------------------
    // The clause itself
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a soft-deleted row is invisible to everyone, owner included")
    void softDeletedRowsNeverSurface() {
        AcademicRequest mine = data.evaluation(somchai, RequestStatus.RECEIVED);
        String code = mine.getRequestCode();
        assertThat(titlesFor(code, somchai)).isNotEmpty();

        var row = index.findByEntityTypeAndEntityIdAndDocPart(
                com.ecom.search.model.SearchEntityType.ACADEMIC_REQUEST, mine.getId(), "MAIN")
                .orElseThrow();
        row.setDeleted(true);
        index.save(row);

        assertThat(titlesFor(code, somchai))
                .as("is_deleted อยู่ในเงื่อนไขเดียวกับการมองเห็น จึงต้องซ่อนจากเจ้าของด้วย")
                .isEmpty();
        assertThat(titlesFor(code, admin)).isEmpty();
    }
}
