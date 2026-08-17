package com.ecom.external.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.external.config.CpWebProperties;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.service.CpWebClient.CpPerson;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.ProfileImageStorage;

/**
 * The college website is a public page maintained by hand; the HR feed is the
 * official record. That ranking is the whole design, and these tests hold it:
 * the website may fill a blank, never overrule something already set — least of
 * all a photograph somebody chose for themselves.
 */
class CpDirectorySyncServiceTest {

    private static final String EMAIL = "somchai@kku.ac.th";

    private final CpWebProperties props = new CpWebProperties();
    private final CpWebClient client = mock(CpWebClient.class);
    private final FsFacultyRepository facultyRepo = mock(FsFacultyRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final StaffMemberRepository staffRepo = mock(StaffMemberRepository.class);
    private final ProfileImageStorage imageStorage = mock(ProfileImageStorage.class);

    private final CpDirectorySyncService sync = new CpDirectorySyncService(
            props, client, facultyRepo, userRepo, staffRepo, imageStorage);

    @BeforeEach
    void setUp() {
        when(facultyRepo.findByEmailNormalized(anyString())).thenReturn(Optional.empty());
        when(staffRepo.findByFsUserId(any())).thenReturn(Optional.empty());
        when(client.fetchImage(anyString())).thenReturn(Optional.of(new byte[] { 1, 2, 3 }));
        when(imageStorage.storeFromBytes(any(), anyString())).thenReturn("cp-somchai.png");
    }

    private CpPerson person() {
        return new CpPerson(EMAIL, "สมชาย", "ใจดี",
                "ผู้ช่วยศาสตราจารย์ดร.", "ผศ.ดร.", "หลักสูตรวิทยาการคอมพิวเตอร์",
                "/storage/images/somchai.png", "somchai.jaidee", true);
    }

    private UserDtls user() {
        UserDtls u = new UserDtls();
        u.setEmail(EMAIL);
        u.setRole("ROLE_USER");
        return u;
    }

    @Test
    @DisplayName("บัญชีที่ยังไม่มีรูป ต้องได้รูปจากเว็บคณะ")
    void anAccountWithNoPhotoGetsOne() {
        UserDtls u = user();
        u.setProfileImage("default.png");
        when(userRepo.findByEmail(EMAIL)).thenReturn(u);
        when(client.fetchAll()).thenReturn(List.of(person()));

        CpDirectorySyncService.Result result = sync.sync();

        assertThat(result.photos()).isEqualTo(1);
        assertThat(u.getProfileImage()).isEqualTo("cp-somchai.png");
    }

    @Test
    @DisplayName("รูปที่ผู้ใช้อัปโหลดเอง ต้องไม่ถูกทับ")
    void aPhotoTheUserChoseIsNeverReplaced() {
        UserDtls u = user();
        u.setProfileImage("my-own-photo.jpg");
        when(userRepo.findByEmail(EMAIL)).thenReturn(u);
        when(client.fetchAll()).thenReturn(List.of(person()));

        sync.sync();

        assertThat(u.getProfileImage()).isEqualTo("my-own-photo.jpg");
        // Nor is anyone else's server asked for a file we would only throw away.
        verify(client, never()).fetchImage(anyString());
    }

    @Test
    @DisplayName("ข้อมูลที่ว่างต้องถูกเติม แต่ข้อมูลที่มีอยู่แล้วต้องไม่ถูกแตะ")
    void blanksAreFilledAndExistingValuesLeftAlone() {
        UserDtls u = user();
        u.setProfileImage("default.png");
        u.setAcademicPosition("รองศาสตราจารย์");   // จากระบบ HR ซึ่งเป็นแหล่งหลัก
        when(userRepo.findByEmail(EMAIL)).thenReturn(u);
        when(client.fetchAll()).thenReturn(List.of(person()));

        sync.sync();

        assertThat(u.getFirstName()).isEqualTo("สมชาย");
        assertThat(u.getTitle()).isEqualTo("ผศ.ดร.");
        assertThat(u.getAcademicPosition())
                .as("เว็บคณะเป็นข้อมูลประชาสัมพันธ์ ไม่ควรทับข้อมูลจากระบบ HR")
                .isEqualTo("รองศาสตราจารย์");
    }

    @Test
    @DisplayName("คนบนเว็บคณะที่ไม่มีในระบบเรา ต้องไม่ถูกสร้างเป็นบัญชีใหม่")
    void someoneOnlyOnTheWebsiteIsNotCreatedHere() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(null);
        when(client.fetchAll()).thenReturn(List.of(person()));

        CpDirectorySyncService.Result result = sync.sync();

        assertThat(result.unmatched()).isEqualTo(1);
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("หน่วยงานที่ว่างในรายชื่อบุคลากร ต้องถูกเติมจากหลักสูตรบนเว็บคณะ")
    void aBlankDepartmentIsFilledFromTheProgramme() {
        FsFaculty faculty = new FsFaculty();
        faculty.setFsUserId(1001L);
        when(facultyRepo.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(faculty));

        StaffMember staff = new StaffMember();
        staff.setFsUserId(1001L);
        when(staffRepo.findByFsUserId(1001L)).thenReturn(Optional.of(staff));
        when(userRepo.findByEmail(EMAIL)).thenReturn(null);
        when(client.fetchAll()).thenReturn(List.of(person()));

        sync.sync();

        assertThat(staff.getDepartment()).isEqualTo("หลักสูตรวิทยาการคอมพิวเตอร์");
    }

    @Test
    @DisplayName("ปิดการเชื่อมต่อไว้ ต้องไม่ยิงไปที่เว็บคณะเลย")
    void nothingIsFetchedWhenTheIntegrationIsSwitchedOff() {
        props.setEnabled(false);

        CpDirectorySyncService.Result result = sync.sync();

        assertThat(result.success()).isFalse();
        verify(client, never()).fetchAll();
    }
}
