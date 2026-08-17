package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;

/**
 * The staff list feeds official documents, so an automatic import has to be
 * conservative in specific ways: it may correct a name, but it must not overrule
 * an administrator, invent a duplicate, or remove someone a past document names.
 */
class StaffDirectorySyncTest {

    private final StaffMemberRepository staffRepo = mock(StaffMemberRepository.class);
    private final FsFacultyRepository facultyRepo = mock(FsFacultyRepository.class);
    private final StaffDirectorySync sync = new StaffDirectorySync(staffRepo, facultyRepo);

    @BeforeEach
    void echoSaves() {
        when(staffRepo.save(any(StaffMember.class))).thenAnswer(i -> i.getArgument(0));
        when(staffRepo.findByFsUserId(any())).thenReturn(Optional.empty());
        when(staffRepo.findByFsUserIdIsNullAndFirstNameIgnoreCaseAndLastNameIgnoreCase(any(), any()))
                .thenReturn(List.of());
    }

    private FsFaculty faculty(long id, String first, String last, String positionTitle, String active) {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(id);
        f.setFirstName(first);
        f.setLastName(last);
        f.setPositionTitle(positionTitle);
        f.setIsActive(active);
        return f;
    }

    private StaffMember imported(long fsUserId, String first, String last, String role) {
        StaffMember s = new StaffMember();
        s.setId(1L);
        s.setFsUserId(fsUserId);
        s.setFirstName(first);
        s.setLastName(last);
        s.setStaffRole(role);
        s.setIsActive(true);
        return s;
    }

    private StaffMember saved() {
        ArgumentCaptor<StaffMember> captor = ArgumentCaptor.forClass(StaffMember.class);
        verify(staffRepo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("อาจารย์ที่ยังไม่มีในรายชื่อบุคลากร ต้องถูกเพิ่มให้อัตโนมัติ")
    void aLecturerMissingFromTheStaffListIsAdded() {
        when(facultyRepo.findAll()).thenReturn(List.of(
                faculty(1001L, "สมชาย", "ใจดี", "ผู้ช่วยศาสตราจารย์", "A")));

        StaffDirectorySync.Result result = sync.importFromDirectory();

        assertThat(result.created()).isEqualTo(1);
        StaffMember created = saved();
        assertThat(created.getFirstName()).isEqualTo("สมชาย");
        assertThat(created.getAcademicTitle()).isEqualTo("ผู้ช่วยศาสตราจารย์");
        assertThat(created.getFsUserId()).isEqualTo(1001L);
        assertThat(created.getStaffRole())
                .as("ต้นทางไม่รู้ว่าใครเป็นคณบดี การเดาบทบาทที่ใช้เซ็นเอกสารอันตรายกว่าปล่อยให้แอดมินตั้ง")
                .isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("บทบาทที่แอดมินตั้งเอง ต้องไม่ถูก sync ทับ")
    void anAdministratorsRoleSurvivesEverySync() {
        when(facultyRepo.findAll()).thenReturn(List.of(
                faculty(1001L, "สมชาย", "ใจดี", "รองศาสตราจารย์", "A")));
        StaffMember existing = imported(1001L, "สมชาย", "ใจดี", "DEAN");
        existing.setStaffType("ข้าราชการ");
        existing.setAcademicTitle("ผู้ช่วยศาสตราจารย์");
        when(staffRepo.findByFsUserId(1001L)).thenReturn(Optional.of(existing));

        sync.importFromDirectory();

        assertThat(existing.getStaffRole()).isEqualTo("DEAN");
        assertThat(existing.getStaffType()).isEqualTo("ข้าราชการ");
        // ...but a promotion upstream does reach the documents.
        assertThat(existing.getAcademicTitle()).isEqualTo("รองศาสตราจารย์");
    }

    @Test
    @DisplayName("sync ซ้ำโดยข้อมูลไม่เปลี่ยน ต้องไม่เขียนอะไรเลย")
    void anUnchangedRowIsNotRewrittenEveryNight() {
        when(facultyRepo.findAll()).thenReturn(List.of(
                faculty(1001L, "สมชาย", "ใจดี", "ผู้ช่วยศาสตราจารย์", "A")));
        StaffMember existing = imported(1001L, "สมชาย", "ใจดี", "GENERAL");
        existing.setAcademicTitle("ผู้ช่วยศาสตราจารย์");
        when(staffRepo.findByFsUserId(1001L)).thenReturn(Optional.of(existing));

        StaffDirectorySync.Result result = sync.importFromDirectory();

        assertThat(result.touchedAnything()).isFalse();
        verify(staffRepo, never()).save(any());
    }

    @Test
    @DisplayName("คนที่แอดมินพิมพ์ไว้เองก่อนหน้านี้ ต้องถูกเชื่อมเข้ากับต้นทาง ไม่ใช่สร้างซ้ำ")
    void someoneTypedInBeforeTheImportIsAdoptedNotDuplicated() {
        when(facultyRepo.findAll()).thenReturn(List.of(
                faculty(1001L, "สมชาย", "ใจดี", "ผู้ช่วยศาสตราจารย์", "A")));

        StaffMember handEntered = new StaffMember();
        handEntered.setId(7L);
        handEntered.setFirstName("สมชาย");
        handEntered.setLastName("ใจดี");
        handEntered.setStaffRole("HEAD");
        when(staffRepo.findByFsUserIdIsNullAndFirstNameIgnoreCaseAndLastNameIgnoreCase("สมชาย", "ใจดี"))
                .thenReturn(List.of(handEntered));

        StaffDirectorySync.Result result = sync.importFromDirectory();

        assertThat(result.adopted()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(handEntered.getFsUserId()).isEqualTo(1001L);
        assertThat(handEntered.getStaffRole()).isEqualTo("HEAD");
    }

    @Test
    @DisplayName("ชื่อซ้ำกันหลายแถว ต้องไม่เดาว่าเป็นคนไหน")
    void anAmbiguousNameIsNeverGuessed() {
        when(facultyRepo.findAll()).thenReturn(List.of(
                faculty(1001L, "สมชาย", "ใจดี", "อาจารย์", "A")));

        StaffMember one = new StaffMember();
        one.setFirstName("สมชาย");
        one.setLastName("ใจดี");
        StaffMember two = new StaffMember();
        two.setFirstName("สมชาย");
        two.setLastName("ใจดี");
        when(staffRepo.findByFsUserIdIsNullAndFirstNameIgnoreCaseAndLastNameIgnoreCase("สมชาย", "ใจดี"))
                .thenReturn(List.of(one, two));

        StaffDirectorySync.Result result = sync.importFromDirectory();

        assertThat(result.adopted()).isZero();
        assertThat(result.created())
                .as("เพิ่มคนใหม่ดีกว่าเชื่อมผิดคน เพราะเชื่อมผิดแปลว่าเอกสารขึ้นชื่อผิดคน")
                .isEqualTo(1);
        assertThat(one.getFsUserId()).isNull();
        assertThat(two.getFsUserId()).isNull();
    }

    @Test
    @DisplayName("อาจารย์ที่ออกจากต้นทางแล้ว ต้องถูกปิดใช้งาน ไม่ใช่ลบทิ้ง")
    void someoneWhoLeavesIsDeactivatedNotDeleted() {
        when(facultyRepo.findAll()).thenReturn(List.of(
                faculty(1001L, "สมชาย", "ใจดี", "อาจารย์", "0")));
        StaffMember existing = imported(1001L, "สมชาย", "ใจดี", "COMMITTEE");
        when(staffRepo.findByFsUserId(1001L)).thenReturn(Optional.of(existing));

        StaffDirectorySync.Result result = sync.importFromDirectory();

        assertThat(result.deactivated()).isEqualTo(1);
        assertThat(existing.getIsActive()).isFalse();
        verify(staffRepo, never()).delete(any());
        verify(staffRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("คนนอกที่แอดมินเพิ่มเอง ต้องไม่ถูกแตะเลย")
    void handEnteredPeopleOutsideTheDirectoryAreLeftAlone() {
        when(facultyRepo.findAll()).thenReturn(List.of());

        StaffDirectorySync.Result result = sync.importFromDirectory();

        assertThat(result.touchedAnything()).isFalse();
        verify(staffRepo, never()).save(any());
    }
}
