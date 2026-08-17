package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Creating accounts from a directory is useful right up to the moment it starts
 * having opinions about accounts that already exist. These tests draw that line:
 * new people get an account, everyone else keeps the role, the state and the
 * password they already had.
 */
class UserDirectorySyncTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FsFacultyRepository facultyRepo = mock(FsFacultyRepository.class);
    private final UserService userService = mock(UserService.class);

    private final UserDirectorySync sync =
            new UserDirectorySync(userRepository, facultyRepo, userService);

    @BeforeEach
    void setUp() {
        // saveUser is the system's own "create an ordinary account" step; the
        // fields it fills in are its business, not this class's.
        when(userService.saveUser(any(UserDtls.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(UserDtls.class))).thenAnswer(i -> i.getArgument(0));
    }

    private FsFaculty faculty(String email, String active) {
        FsFaculty f = new FsFaculty();
        f.setFsUserId(1001L);
        f.setEmail(email);
        f.setFirstName("สมชาย");
        f.setLastName("ใจดี");
        f.setPrefix("ผศ.ดร.");
        f.setPositionTitle("ผู้ช่วยศาสตราจารย์");
        f.setTel("0812345678");
        f.setIsActive(active);
        return f;
    }

    private UserDtls created() {
        ArgumentCaptor<UserDtls> captor = ArgumentCaptor.forClass(UserDtls.class);
        verify(userService).saveUser(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("อาจารย์ที่ยังไม่มีบัญชี ต้องได้บัญชีผู้ใช้อัตโนมัติ")
    void aLecturerWithoutAnAccountGetsOne() {
        when(facultyRepo.findAll()).thenReturn(List.of(faculty("somchai@kku.ac.th", "A")));
        when(userRepository.findByEmail("somchai@kku.ac.th")).thenReturn(null);

        UserDirectorySync.Result result = sync.createMissingAccounts();

        assertThat(result.created()).isEqualTo(1);
        UserDtls created = created();
        assertThat(created.getEmail()).isEqualTo("somchai@kku.ac.th");
        assertThat(created.getFirstName()).isEqualTo("สมชาย");
        assertThat(created.getLastName()).isEqualTo("ใจดี");
        assertThat(created.getAcademicPosition()).isEqualTo("ผู้ช่วยศาสตราจารย์");
        assertThat(created.getMobileNumber()).isEqualTo("0812345678");
    }

    @Test
    @DisplayName("อีเมลถูกทำเป็นตัวพิมพ์เล็กก่อนเสมอ")
    void addressesAreNormalisedBeforeUse() {
        when(facultyRepo.findAll()).thenReturn(List.of(faculty("  SomChai@KKU.ac.th ", "A")));
        when(userRepository.findByEmail("somchai@kku.ac.th")).thenReturn(null);

        sync.createMissingAccounts();

        assertThat(created().getEmail()).isEqualTo("somchai@kku.ac.th");
    }

    @Test
    @DisplayName("แอดมินที่บังเอิญอยู่ในรายชื่ออาจารย์ด้วย ต้องไม่ถูกลดสิทธิ์")
    void anAdministratorInTheDirectoryIsNotDemoted() {
        when(facultyRepo.findAll()).thenReturn(List.of(faculty("boss@kku.ac.th", "A")));

        UserDtls admin = new UserDtls();
        admin.setEmail("boss@kku.ac.th");
        admin.setRole("ROLE_ADMIN");
        admin.setIsEnable(true);
        admin.setFirstName("มีอยู่แล้ว");
        admin.setAcademicPosition("รองศาสตราจารย์");
        when(userRepository.findByEmail("boss@kku.ac.th")).thenReturn(admin);

        sync.createMissingAccounts();

        assertThat(admin.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(admin.getFirstName()).isEqualTo("มีอยู่แล้ว");
        assertThat(admin.getAcademicPosition()).isEqualTo("รองศาสตราจารย์");
    }

    @Test
    @DisplayName("บัญชีที่มีอยู่แล้ว ต้องเติมเฉพาะช่องที่ว่าง")
    void onlyBlankFieldsAreFilledOnAnExistingAccount() {
        when(facultyRepo.findAll()).thenReturn(List.of(faculty("somchai@kku.ac.th", "A")));

        UserDtls existing = new UserDtls();
        existing.setEmail("somchai@kku.ac.th");
        existing.setRole("ROLE_USER");
        existing.setMobileNumber("0899999999");
        when(userRepository.findByEmail("somchai@kku.ac.th")).thenReturn(existing);

        UserDirectorySync.Result result = sync.createMissingAccounts();

        assertThat(result.filled()).isEqualTo(1);
        assertThat(existing.getFirstName()).isEqualTo("สมชาย");
        assertThat(existing.getMobileNumber())
                .as("เบอร์ที่กรอกเองต้องไม่ถูกทับด้วยเบอร์จากต้นทาง")
                .isEqualTo("0899999999");
    }

    @Test
    @DisplayName("อาจารย์ที่ลาออกแล้วและยังไม่เคยมีบัญชี ต้องไม่ถูกสร้างบัญชีให้")
    void nobodyIsGivenAnAccountAfterTheyHaveLeft() {
        when(facultyRepo.findAll()).thenReturn(List.of(faculty("retired@kku.ac.th", "0")));
        when(userRepository.findByEmail("retired@kku.ac.th")).thenReturn(null);

        UserDirectorySync.Result result = sync.createMissingAccounts();

        assertThat(result.created()).isZero();
        verify(userService, never()).saveUser(any());
    }

    @Test
    @DisplayName("รายชื่อที่ไม่มีอีเมล ต้องข้ามไป")
    void aRecordWithNoAddressIsSkipped() {
        when(facultyRepo.findAll()).thenReturn(List.of(faculty(null, "A")));

        UserDirectorySync.Result result = sync.createMissingAccounts();

        assertThat(result.skipped()).isEqualTo(1);
        verify(userService, never()).saveUser(any());
    }
}
