package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.service.ExternalSignerService;
import com.ecom.academic.service.SignerBriefing;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/** ผู้ยื่นเห็นอีเมลของคนอื่นแบบปิดบางส่วน — ค้นจากส่วนที่ปิดไว้ก็ต้องไม่ได้ ไม่อย่างนั้นเดาทีละตัวอักษรได้ */
class PeopleApiControllerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PeopleApiController api = new PeopleApiController(users, mock(StaffMemberService.class),
            mock(ExternalSignerService.class), mock(SignerBriefing.class),
            mock(AcademicRequestRepository.class), mock(PositionRequestRepository.class));

    private static UserDtls user(int id, String email, String role, String first, String last) {
        UserDtls u = new UserDtls();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        u.setFirstName(first);
        u.setLastName(last);
        u.setIsEnable(true);
        return u;
    }

    private List<PeopleApiController.Person> searchAs(UserDtls caller, String q) {
        UserDtls target = user(2, "somchai.secret@kku.ac.th", "ROLE_USER", "สมชาย", "ลับ");
        when(users.findByEmail(caller.getEmail())).thenReturn(caller);
        when(users.findAll()).thenReturn(List.of(caller, target));
        return api.search(q, null, null, null, null, caller::getEmail).getBody();
    }

    @Test
    void anApplicantCannotSearchTheHiddenPartOfAnEmail() {
        assertThat(searchAs(user(1, "caller@kku.ac.th", "ROLE_USER", "ผู้", "ยื่น"), "somchai.secret@")).isEmpty();
    }

    @Test
    void anApplicantStillFindsPeopleByName() {
        assertThat(searchAs(user(1, "caller@kku.ac.th", "ROLE_USER", "ผู้", "ยื่น"), "สมชาย"))
                .extracting(PeopleApiController.Person::email).containsExactly("so***@kku.ac.th");
    }

    @Test
    void staffSearchByFullEmail() {
        assertThat(searchAs(user(1, "admin@kku.ac.th", "ROLE_ADMIN", "แอด", "มิน"), "somchai.secret@"))
                .extracting(PeopleApiController.Person::email).containsExactly("somchai.secret@kku.ac.th");
    }
}
