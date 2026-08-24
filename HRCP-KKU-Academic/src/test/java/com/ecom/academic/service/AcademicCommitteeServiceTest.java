package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicCommitteeMember;
import com.ecom.academic.model.CommitteeType;
import com.ecom.academic.repository.AcademicCommitteeMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ระบบจัดการข้อมูลกรรมการและผู้ทรงคุณวุฒิ (AcademicCommitteeService)")
class AcademicCommitteeServiceTest {

    @Autowired
    private AcademicCommitteeService committeeService;

    @Autowired
    private AcademicCommitteeMemberRepository committeeRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        committeeRepository.deleteAll();
    }

    @Test
    @DisplayName("สามารถบันทึกข้อมูลกรรมการและสร้าง/จับคู่ User Account อัตโนมัติ")
    void canSaveAndAutoProvisionUser() {
        AcademicCommitteeMember member = new AcademicCommitteeMember();
        member.setTitle("ศ.ดร.");
        member.setFirstName("สมชาย");
        member.setLastName("ใจดี");
        member.setAcademicPosition("ศาสตราจารย์");
        member.setAffiliation("คณะวิทยาศาสตร์ มหาวิทยาลัยขอนแก่น");
        member.setExpertiseField("ปัญญาประดิษฐ์และวิทยาการข้อมูล");

        member.setTitleEn("Prof. Dr.");
        member.setFirstNameEn("Somchai");
        member.setLastNameEn("Jaidee");
        member.setAcademicPositionEn("Professor");
        member.setAffiliationEn("Faculty of Science, Khon Kaen University");
        member.setExpertiseFieldEn("Artificial Intelligence");

        member.setEmail("somchai.j@kku.ac.th");
        member.setPhoneNumber("081-2345678");
        member.setCommitteeType(CommitteeType.TEACHING_EVALUATION);

        AcademicCommitteeMember saved = committeeService.save(member);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFullName()).isEqualTo("ศ.ดร. สมชาย ใจดี");
        assertThat(saved.getFullNameEn()).isEqualTo("Prof. Dr. Somchai Jaidee");
        assertThat(saved.getAffiliationEn()).isEqualTo("Faculty of Science, Khon Kaen University");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getUser()).isNotNull();
        assertThat(saved.getUser().getEmail()).isEqualTo("somchai.j@kku.ac.th");
        assertThat(saved.getUser().getRole()).isEqualTo("ROLE_USER");
        assertThat(saved.getUser().getIsEnable()).isTrue();
    }

    @Test
    @DisplayName("สามารถค้นหากรรมการตามคำสำคัญและประเภทกรรมการ")
    void canSearchAndFilterByType() {
        AcademicCommitteeMember m1 = new AcademicCommitteeMember();
        m1.setTitle("รศ.ดร.");
        m1.setFirstName("วิชัย");
        m1.setLastName("เก่งการ");
        m1.setAffiliation("จุฬาลงกรณ์มหาวิทยาลัย");
        m1.setAffiliationEn("Chulalongkorn University");
        m1.setEmail("wichai.k@chula.ac.th");
        m1.setCommitteeType(CommitteeType.EXTERNAL_READER);
        m1.setExpertiseField("Software Engineering");
        m1.setExpertiseFieldEn("Software Engineering");
        committeeService.save(m1);

        AcademicCommitteeMember m2 = new AcademicCommitteeMember();
        m2.setTitle("ผศ.ดร.");
        m2.setFirstName("ประเสริฐ");
        m2.setLastName("มั่นคง");
        m2.setAffiliation("มหาวิทยาลัยเชียงใหม่");
        m2.setAffiliationEn("Chiang Mai University");
        m2.setEmail("prasert.m@cmu.ac.th");
        m2.setCommitteeType(CommitteeType.TEACHING_EVALUATION);
        m2.setExpertiseField("Computer Systems");
        committeeService.save(m2);

        // Search by Thai keyword
        List<AcademicCommitteeMember> searchResultTh = committeeService.search("จุฬา");
        assertThat(searchResultTh).hasSize(1);
        assertThat(searchResultTh.get(0).getFirstName()).isEqualTo("วิชัย");

        // Search by English keyword
        List<AcademicCommitteeMember> searchResultEn = committeeService.search("Chiang Mai");
        assertThat(searchResultEn).hasSize(1);
        assertThat(searchResultEn.get(0).getFirstName()).isEqualTo("ประเสริฐ");

        // Filter by type
        List<AcademicCommitteeMember> evalList = committeeService.findByType(CommitteeType.TEACHING_EVALUATION);
        assertThat(evalList).hasSize(1);
        assertThat(evalList.get(0).getFirstName()).isEqualTo("ประเสริฐ");
    }

    @Test
    @DisplayName("สามารถลบข้อมูลกรรมการแบบ Soft Delete (isActive = false)")
    void canSoftDeleteMember() {
        AcademicCommitteeMember m = new AcademicCommitteeMember();
        m.setTitle("อ.ดร.");
        m.setFirstName("นิภา");
        m.setLastName("สุขใจ");
        m.setAffiliation("มหาวิทยาลัยขอนแก่น");
        m.setEmail("nipa.s@kku.ac.th");
        m.setCommitteeType(CommitteeType.POSITION_SCREENING);
        AcademicCommitteeMember saved = committeeService.save(m);

        boolean deleted = committeeService.delete(saved.getId());
        assertThat(deleted).isTrue();

        Optional<AcademicCommitteeMember> found = committeeService.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getIsActive()).isFalse();

        List<AcademicCommitteeMember> activeList = committeeService.findAllActive();
        assertThat(activeList).noneMatch(c -> c.getId().equals(saved.getId()));
    }
}
