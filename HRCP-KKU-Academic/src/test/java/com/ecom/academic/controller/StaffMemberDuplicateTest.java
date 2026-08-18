package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

/**
 * Adding the same person twice is easy to do and expensive to undo: every
 * duplicate turns into an ambiguous choice in every document dropdown, and the
 * wrong one may already have been picked by the time anyone notices. Two people
 * genuinely can share a name though, so the guard asks rather than refuses.
 */
class StaffMemberDuplicateTest {

    private final StaffMemberService staffService = mock(StaffMemberService.class);
    private final AdminLogService adminLogService = mock(AdminLogService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private final StaffMemberController controller =
            new StaffMemberController(staffService, adminLogService, userRepository, request);

    private final Model model = new ExtendedModelMap();
    private final Principal admin = () -> "admin@kku.ac.th";

    @BeforeEach
    void setUp() {
        when(userRepository.findByEmail(any())).thenReturn(new UserDtls());
        when(staffService.findByName(any(), any())).thenReturn(List.of());
    }

    private String add(Boolean confirmDuplicate) {
        return controller.addStaff("สมชาย", "ใจดี", "อาจารย์ ดร.",
                "Somchai", "Jaidee", "Lecturer",
                "พนักงานมหาวิทยาลัย",
                "วิทยาการคอมพิวเตอร์", "COMMITTEE", confirmDuplicate, model, admin);
    }

    private StaffMember existing() {
        StaffMember s = new StaffMember();
        s.setId(5L);
        s.setFirstName("สมชาย");
        s.setLastName("ใจดี");
        s.setStaffRole("HEAD");
        s.setIsActive(true);
        return s;
    }

    @Test
    @DisplayName("ชื่อไม่ซ้ำ ต้องบันทึกได้ตามปกติ")
    void aNewNameIsSavedWithoutFuss() {
        String view = add(null);

        assertThat(view).isEqualTo("redirect:/admin/academic/staff?success=added");
        verify(staffService).save(any(StaffMember.class));
    }

    @Test
    @DisplayName("ชื่อซ้ำกับคนที่มีอยู่ ต้องหยุดถามก่อน ยังไม่บันทึก")
    void aDuplicateNameStopsToAskFirst() {
        when(staffService.findByName("สมชาย", "ใจดี")).thenReturn(List.of(existing()));

        String view = add(null);

        assertThat(view).isEqualTo("academic/admin/staff_form");
        verify(staffService, never()).save(any());

        // The form comes back filled in — being warned must not cost the typing.
        StaffMember echoed = (StaffMember) model.getAttribute("staff");
        assertThat(echoed).isNotNull();
        assertThat(echoed.getFirstName()).isEqualTo("สมชาย");
        assertThat(echoed.getStaffRole()).isEqualTo("COMMITTEE");
        // ...and says who it clashed with, so the decision can be made on evidence.
        assertThat((List<?>) model.getAttribute("duplicates")).hasSize(1);
        assertThat((String) model.getAttribute("duplicateWarning")).contains("สมชาย ใจดี");
    }

    @Test
    @DisplayName("ยืนยันแล้วว่าเป็นคนละคน ต้องบันทึกให้")
    void confirmingGoesThrough() {
        when(staffService.findByName("สมชาย", "ใจดี")).thenReturn(List.of(existing()));

        String view = add(true);

        assertThat(view).isEqualTo("redirect:/admin/academic/staff?success=added");
        verify(staffService).save(any(StaffMember.class));
    }
}
