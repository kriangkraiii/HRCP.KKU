package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * งานของเจ้าหน้าที่ต้องไม่ไปถึงคณบดีและผู้บริหาร แม้บนระบบจริงทุกคนเป็น ROLE_ADMIN
 */
class OfficerRecipientsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final StaffMemberRepository staff = mock(StaffMemberRepository.class);
    private final OfficerRecipients recipients = new OfficerRecipients(users, staff);

    private static UserDtls admin(int id) {
        UserDtls u = new UserDtls();
        u.setId(id);
        u.setRole("ROLE_ADMIN");
        return u;
    }

    private static StaffMember staffMember(UserDtls user, String role) {
        StaffMember s = new StaffMember();
        s.setUser(user);
        s.setStaffRole(role);
        return s;
    }

    @Test
    @DisplayName("คณบดีและรองคณบดีที่เป็นแอดมิน ไม่ได้รับงานของเจ้าหน้าที่")
    void executivesAreLeftOut() {
        UserDtls hr = admin(1);
        UserDtls dean = admin(2);
        UserDtls assocDean = admin(3);
        UserDtls itAdmin = admin(4);
        when(users.findByRole("ROLE_ADMIN")).thenReturn(List.of(hr, dean, assocDean, itAdmin));
        when(staff.findAllActiveWithUser()).thenReturn(List.of(
                staffMember(hr, "HR"), staffMember(dean, "DEAN"), staffMember(assocDean, "HEAD")));

        assertThat(recipients.officers())
                .as("แอดมินที่ไม่ได้ผูกกับบุคลากร (เช่น ผู้ดูแลระบบ IT) ยังต้องได้รับ")
                .containsExactly(hr, itAdmin);
    }

    @Test
    @DisplayName("ถ้าแอดมินทุกคนเป็นผู้บริหาร ส่งถึงทุกคนเหมือนเดิม งานต้องไม่หายเงียบ")
    void fallsBackToEveryAdminWhenNoOfficerIsLeft() {
        UserDtls dean = admin(2);
        when(users.findByRole("ROLE_ADMIN")).thenReturn(List.of(dean));
        when(staff.findAllActiveWithUser()).thenReturn(List.of(staffMember(dean, "DEAN")));

        assertThat(recipients.officers()).containsExactly(dean);
    }
}
