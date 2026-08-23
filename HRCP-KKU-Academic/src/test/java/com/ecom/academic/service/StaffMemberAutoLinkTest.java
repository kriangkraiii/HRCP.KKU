package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@DisplayName("ทดสอบระบบ Auto-Link และ Auto-Role ใน StaffMemberService")
class StaffMemberAutoLinkTest {

    private final StaffMemberRepository staffRepo = mock(StaffMemberRepository.class);
    private final StaffDirectorySync directorySync = mock(StaffDirectorySync.class);
    private final FsFacultyRepository facultyRepo = mock(FsFacultyRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);

    private final StaffMemberService service =
            new StaffMemberService(staffRepo, directorySync, facultyRepo, userRepo);

    @Test
    @DisplayName("สแกนบุคลากรที่ยังไม่ผูกบัญชี และผูกบัญชีให้อัตโนมัติ")
    void autoLinksUnlinkedStaffMembers() {
        StaffMember staff1 = new StaffMember();
        staff1.setId(1L);
        staff1.setFirstName("สิรภัทร");
        staff1.setLastName("เชี่ยวชาญวัฒนา");
        staff1.setFsUserId(1001L);

        FsFaculty faculty1 = new FsFaculty();
        faculty1.setFsUserId(1001L);
        faculty1.setEmail("sunkra@kku.ac.th");
        faculty1.setManagePosition("คณบดี");

        when(staffRepo.findByIsActiveTrueOrderByFirstNameAscLastNameAsc()).thenReturn(List.of(staff1));
        when(facultyRepo.findById(1001L)).thenReturn(Optional.of(faculty1));

        UserDtls user1 = new UserDtls();
        user1.setId(55);
        user1.setEmail("sunkra@kku.ac.th");

        when(directorySync.applyAccountLink(staff1, faculty1)).thenAnswer(inv -> {
            staff1.setUser(user1);
            return true;
        });
        when(directorySync.applyRole(staff1, faculty1)).thenAnswer(inv -> {
            staff1.setStaffRole("DEAN");
            return true;
        });

        StaffMemberService.AutoLinkReport report = service.autoLinkAllAccounts();

        assertThat(report.totalScanned()).isEqualTo(1);
        assertThat(report.newlyLinked()).isEqualTo(1);
        assertThat(report.rolesUpdated()).isEqualTo(1);
        assertThat(report.hasChanges()).isTrue();
        assertThat(staff1.getUser()).isEqualTo(user1);
        assertThat(staff1.getStaffRole()).isEqualTo("DEAN");
        verify(staffRepo).save(staff1);
    }

    @Test
    @DisplayName("หากบุคลากรผูกบัญชีแล้ว และไม่มีตำแหน่งใหม่อัปเดต ต้องไม่เขียนซ้ำ")
    void alreadyLinkedStaffIsNotRewritten() {
        StaffMember staff1 = new StaffMember();
        staff1.setId(1L);
        staff1.setFirstName("สมชาย");
        staff1.setLastName("ใจดี");
        staff1.setUser(new UserDtls());
        staff1.setStaffRole("DEAN");

        when(staffRepo.findByIsActiveTrueOrderByFirstNameAscLastNameAsc()).thenReturn(List.of(staff1));

        StaffMemberService.AutoLinkReport report = service.autoLinkAllAccounts();

        assertThat(report.totalScanned()).isEqualTo(1);
        assertThat(report.newlyLinked()).isZero();
        assertThat(report.rolesUpdated()).isZero();
        assertThat(report.hasChanges()).isFalse();
        verify(staffRepo, never()).save(any());
    }
}
