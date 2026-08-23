package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:rolechangetestdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
@Transactional
class RoleChangeSecurityTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("เมื่อเปลี่ยน Role จาก USER เป็น ADMIN เลข Applicant ID ต้องถูกลบออก (เป็น null)")
    void changingUserToAdminClearsApplicantId() {
        UserDtls user = new UserDtls();
        user.setEmail("testuser_to_admin@kku.ac.th");
        user.setFirstName("ทดสอบ");
        user.setLastName("ผู้ใช้");
        user.setRole("ROLE_USER");
        user.setIsEnable(true);
        user = userService.saveUser(user);

        assertThat(user.getRole()).isEqualTo("ROLE_USER");
        assertThat(user.getApplicantId()).isNotNull();
        assertThat(user.getApplicantId()).startsWith("APP-");

        // Now edit and change role to ROLE_ADMIN
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(user.getId());
        updateRequest.setEmail(user.getEmail());
        updateRequest.setFirstName("ทดสอบ");
        updateRequest.setLastName("ผู้ใช้");
        updateRequest.setRole("ROLE_ADMIN");

        UserDtls updated = userService.updateUserDetails(updateRequest, null);

        assertThat(updated.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(updated.getApplicantId()).isNull(); // Must be cleared!

        // Verify in DB directly
        UserDtls dbUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(dbUser.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(dbUser.getApplicantId()).isNull();
    }

    @Test
    @DisplayName("เมื่อเปลี่ยน Role จาก ADMIN กลับเป็น USER ต้องได้รับ Applicant ID ใหม่อัตโนมัติ")
    void changingAdminToUserAssignsApplicantId() {
        UserDtls admin = new UserDtls();
        admin.setEmail("testadmin_to_user@kku.ac.th");
        admin.setFirstName("แอดมิน");
        admin.setLastName("ทดสอบ");
        admin.setRole("ROLE_ADMIN");
        admin.setIsEnable(true);
        admin = userService.saveAdmin(admin);

        assertThat(admin.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(admin.getApplicantId()).isNull();

        // Now edit and change role to ROLE_USER
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(admin.getId());
        updateRequest.setEmail(admin.getEmail());
        updateRequest.setFirstName("แอดมิน");
        updateRequest.setLastName("ทดสอบ");
        updateRequest.setRole("ROLE_USER");

        UserDtls updated = userService.updateUserDetails(updateRequest, null);

        assertThat(updated.getRole()).isEqualTo("ROLE_USER");
        assertThat(updated.getApplicantId()).isNotNull();
        assertThat(updated.getApplicantId()).startsWith("APP-");

        // Verify in DB directly
        UserDtls dbUser = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(dbUser.getRole()).isEqualTo("ROLE_USER");
        assertThat(dbUser.getApplicantId()).isNotNull();
    }

    @Test
    @DisplayName("เมื่อเปลี่ยนเป็นแอดมินแล้ว เมื่อดึงข้อมูลต้นทาง (Directory Sync) จะไม่สร้างบัญชีซ้ำและไม่เปลี่ยน Role กลับเป็น User")
    void whenUserIsChangedToAdmin_DirectorySyncNeverRecreatesItAsUser() {
        // 1. Create user who is an admin
        UserDtls admin = new UserDtls();
        admin.setEmail("admin_sync_check@kku.ac.th");
        admin.setFirstName("จิราภรณ์");
        admin.setLastName("หอมอ่อน");
        admin.setRole("ROLE_ADMIN");
        admin.setIsEnable(true);
        admin = userService.saveAdmin(admin);

        long userCountBefore = userRepository.count();

        // 2. Mock or run UserDirectorySync
        // Simulate faculty record matching this email
        com.ecom.external.model.FsFaculty faculty = new com.ecom.external.model.FsFaculty();
        faculty.setEmail("admin_sync_check@kku.ac.th");
        faculty.setFirstName("จิราภรณ์");
        faculty.setLastName("หอมอ่อน");
        faculty.setIsActive("A");

        com.ecom.external.repository.FsFacultyRepository facultyRepo = org.mockito.Mockito.mock(com.ecom.external.repository.FsFacultyRepository.class);
        org.mockito.Mockito.when(facultyRepo.findAll()).thenReturn(java.util.List.of(faculty));

        UserDirectorySync sync = new UserDirectorySync(userRepository, facultyRepo, userService);
        UserDirectorySync.Result result = sync.createMissingAccounts();

        // 3. Assertions
        assertThat(result.created()).isEqualTo(0); // 0 accounts created!
        assertThat(userRepository.count()).isEqualTo(userCountBefore); // Total user count unchanged

        UserDtls verified = userRepository.findByEmail("admin_sync_check@kku.ac.th");
        assertThat(verified).isNotNull();
        assertThat(verified.getRole()).isEqualTo("ROLE_ADMIN"); // Role remains ROLE_ADMIN!
        assertThat(verified.getApplicantId()).isNull(); // Applicant ID remains null!
    }
}
