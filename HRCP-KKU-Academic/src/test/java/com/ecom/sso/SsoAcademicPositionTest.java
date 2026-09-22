package com.ecom.sso;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * ตำแหน่งทางวิชาการจาก KKU SSO ชนะแหล่งอื่น — ยกเว้นตอนที่คำร้องยังเดินอยู่
 *
 * <p>ก่อนหน้านี้ {@code KkuSsoClient.fetchProfile} ไม่มีใครเรียกเลย ตำแหน่งจึงมาจาก
 * {@code fs_faculty} ซึ่งเป็นสำเนาอีกทอดที่ sync วันละครั้ง เทสต์ชุดนี้ล็อกพฤติกรรมใหม่ไว้
 * ทั้งฝั่งที่ทับได้และฝั่งที่ต้องไม่ทับ
 *
 * <p>ยิงเข้า {@link SsoUserProvisioner} ตรง ๆ แทนการจำลอง callback ทั้งเส้น เพราะสิ่งที่
 * ต้องการยืนยันคือกติกาการตัดสินค่า ไม่ใช่เส้นทาง HTTP ของการล็อกอิน
 */
@DisplayName("ตำแหน่งทางวิชาการจาก KKU SSO")
class SsoAcademicPositionTest extends AbstractFlowTest {

    @Autowired
    private SsoUserProvisioner provisioner;

    @Autowired
    private UserRepository userRepository;

    private UserDtls applicant;

    @BeforeEach
    void seedApplicant() {
        applicant = data.applicant();
        applicant.setTitle("อ.ดร.");
        applicant.setAcademicPosition("อาจารย์");
        applicant.setAcademicPositionEn("Lecturer");
        applicant.setAcademicPositionSyncedAt(null);
        applicant = userRepository.save(applicant);
    }

    private KkuSsoClient.SsoToken tokenFor(UserDtls user) {
        return new KkuSsoClient.SsoToken("access-token-not-logged", user.getEmail(),
                "immutable-1", user.getFirstName(), user.getLastName(), "emp-1");
    }

    /** profile ที่ SSO ส่งกลับมา โดยระบุแค่สองช่องที่เกี่ยวกับตำแหน่ง */
    private KkuSsoClient.SsoProfile profile(String positionName, String levelName) {
        return new KkuSsoClient.SsoProfile(applicant.getEmail(), "u-1", "employee",
                "นาย", applicant.getFirstName(), applicant.getLastName(),
                "Mr.", "Somchai", "Jaidee",
                "วิทยาลัยการคอมพิวเตอร์", positionName, "พนักงานมหาวิทยาลัย",
                "L1", levelName, "วิชาการ", "male", "0812345678", "ปฏิบัติราชการ");
    }

    private UserDtls reload() {
        return userRepository.findById(applicant.getId()).orElseThrow();
    }

    @Nested
    @DisplayName("ไม่มีคำร้องค้าง — ทับได้")
    class Overwrites {

        @Test
        @DisplayName("positionName ที่เป็นตำแหน่งวิชาการ ทับค่าเดิมทั้งไทยและอังกฤษ")
        void positionNameWins() {
            provisioner.provision(tokenFor(applicant), null,
                    Optional.of(profile("ผู้ช่วยศาสตราจารย์", null)));

            UserDtls after = reload();
            assertThat(after.getAcademicPosition()).isEqualTo("ผู้ช่วยศาสตราจารย์");
            assertThat(after.getAcademicPositionEn())
                    .as("SSO ไม่ส่งตำแหน่งภาษาอังกฤษมา ค่านี้ต้อง derive จาก AcademicRank")
                    .isEqualTo("Assistant Professor");
            assertThat(after.getAcademicPositionSyncedAt()).isNotNull();
        }

        @Test
        @DisplayName("สายที่ตำแหน่งวิชาการไปอยู่ levelName ก็อ่านเจอ")
        void levelNameIsReadToo() {
            provisioner.provision(tokenFor(applicant), null,
                    Optional.of(profile("นักวิจัย", "รองศาสตราจารย์")));

            assertThat(reload().getAcademicPosition()).isEqualTo("รองศาสตราจารย์");
            assertThat(reload().getAcademicPositionEn()).isEqualTo("Associate Professor");
        }

        @Test
        @DisplayName("ค่าที่ไม่ใช่ตำแหน่งวิชาการต้องไม่หลุดลงช่องนี้")
        void nonAcademicPositionIsIgnored() {
            provisioner.provision(tokenFor(applicant), null,
                    Optional.of(profile("นักวิชาการคอมพิวเตอร์", "ชำนาญการ")));

            UserDtls after = reload();
            assertThat(after.getAcademicPosition()).isEqualTo("อาจารย์");
            assertThat(after.getAcademicPositionSyncedAt())
                    .as("ไม่ได้ยืนยันอะไร จึงไม่ควรปักธงกัน sync")
                    .isNull();
        }

        @Test
        @DisplayName("ดึง profile ไม่ได้ ต้องล็อกอินผ่านและไม่ทำให้ค่าเดิมหาย")
        void missingProfileStillProvisions() {
            UserDtls result = provisioner.provision(tokenFor(applicant), null, Optional.empty());

            assertThat(result).isNotNull();
            assertThat(reload().getAcademicPosition()).isEqualTo("อาจารย์");
        }

        @Test
        @DisplayName("คำนำหน้าเลื่อนตามตำแหน่ง และคงวุฒิ ดร. ที่มีอยู่เดิมไว้")
        void titleFollowsTheRankAndKeepsTheDoctorate() {
            provisioner.provision(tokenFor(applicant), null,
                    Optional.of(profile("ผู้ช่วยศาสตราจารย์", null)));

            assertThat(reload().getTitle())
                    .as("อ.ดร. + ได้ ผศ. ต้องได้ ผศ.ดร. ไม่ใช่ ผศ. เฉย ๆ")
                    .isEqualTo("ผศ.ดร.");
        }

        @Test
        @DisplayName("คำนำหน้าที่สูงเกินตำแหน่งจริงต้องถูกลดลงมาให้ตรง")
        void titleTooHighIsCorrectedDownwards() {
            applicant.setTitle("รศ.ดร.");
            userRepository.save(applicant);

            provisioner.provision(tokenFor(applicant), null,
                    Optional.of(profile("ผู้ช่วยศาสตราจารย์", null)));

            assertThat(reload().getTitle())
                    .as("ถ้าส่ง candidate ผิดลำดับ resolveShortTitle จะ match 'รศ.' ก่อนแล้วคืน รศ.ดร.")
                    .isEqualTo("ผศ.ดร.");
        }

        @Test
        @DisplayName("ไม่มีวุฒิ ดร. เดิม ก็ไม่เติมให้")
        void titleWithoutADoctorateStaysWithoutOne() {
            applicant.setTitle("นาย");
            userRepository.save(applicant);

            provisioner.provision(tokenFor(applicant), null,
                    Optional.of(profile("รองศาสตราจารย์", null)));

            assertThat(reload().getTitle()).isEqualTo("รศ.");
        }
    }

    @Nested
    @DisplayName("มีคำร้องค้างอยู่ — ห้ามทับ")
    class Freezes {

        @Test
        @DisplayName("คำร้องขอกำหนดตำแหน่งที่ยังไม่จบ หยุดการทับไว้")
        void activePositionRequestFreezesTheField() {
            data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_VERIFICATION, null);

            provisioner.provision(tokenFor(applicant), null,
                    Optional.of(profile("ผู้ช่วยศาสตราจารย์", null)));

            UserDtls after = reload();
            assertThat(after.getAcademicPosition())
                    .as("AcademicRankPolicy ใช้ค่านี้ตัดสินคำร้องที่กำลังเวียนอยู่")
                    .isEqualTo("อาจารย์");
            assertThat(after.getAcademicPositionEn()).isEqualTo("Lecturer");
            assertThat(after.getTitle())
                    .as("คำนำหน้าต้องถูกแช่ไปพร้อมตำแหน่ง ไม่งั้นเอกสารในซองที่เวียนอยู่จะไม่ตรงกันเอง")
                    .isEqualTo("อ.ดร.");
            assertThat(after.getAcademicPositionSyncedAt()).isNull();
        }

        @Test
        @DisplayName("คำร้องประเมินการสอนที่ยังไม่จบ ก็หยุดเหมือนกัน")
        void activeEvaluationFreezesTheField() {
            data.evaluation(applicant, RequestStatus.RECEIVED);

            provisioner.provision(tokenFor(applicant), null,
                    Optional.of(profile("ผู้ช่วยศาสตราจารย์", null)));

            assertThat(reload().getAcademicPosition()).isEqualTo("อาจารย์");
        }
    }
}
