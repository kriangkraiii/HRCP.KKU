package com.ecom.sso;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicRank;
import com.ecom.academic.service.AcademicPositionSyncPolicy;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.service.EnglishNameSplitter;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.util.AcademicTitleResolver;

/**
 * Maps an SSO identity onto a local {@link UserDtls} row.
 *
 * <p>The rest of the application authorises on {@code UserDtls} — roles,
 * request ownership, storage quotas — so an SSO sign-in still needs one. First
 * login creates it from the faculty directory; later logins refresh the name
 * and leave everything else alone.
 *
 * <p>Provisioning happens only after {@link SsoAccessPolicy} has allowed the
 * address, so this class never has to decide who may enter.
 */
@Service
public class SsoUserProvisioner {

    private static final Logger log = LoggerFactory.getLogger(SsoUserProvisioner.class);

    private static final String ROLE_USER = "ROLE_USER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AcademicPositionSyncPolicy positionSyncPolicy;

    public SsoUserProvisioner(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AcademicPositionSyncPolicy positionSyncPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.positionSyncPolicy = positionSyncPolicy;
    }

    /**
     * Maps the SSO identity onto a local account, creating one if needed.
     *
     * <p><b>May throw {@link org.springframework.dao.DataIntegrityViolationException}</b>
     * when a concurrent sign-in for the same address inserted the row first — the
     * unique constraint on the e-mail is what stops both from landing. Calling
     * this again resolves it: the second pass finds the row and refreshes it.
     * The retry belongs to the caller because the violation surfaces when this
     * transaction commits, which is after the method has already returned.
     */
    @Transactional
    public UserDtls provision(KkuSsoClient.SsoToken token, FsFaculty faculty) {
        return provision(token, faculty, Optional.empty());
    }

    /**
     * @param profile the richer {@code /user.profile} payload when SSO gave us one.
     *                It is the university's own personnel register, so the academic
     *                position it carries outranks the directory copy — see
     *                {@link #applyAcademicPosition}. A login must still work without
     *                it: the profile call is a second round trip that can fail on its
     *                own, and nothing about signing in depends on the answer.
     */
    @Transactional
    public UserDtls provision(KkuSsoClient.SsoToken token, FsFaculty faculty,
            Optional<KkuSsoClient.SsoProfile> profile) {
        String email = token.email().trim().toLowerCase();

        UserDtls user = userRepository.findByEmail(email);
        if (user == null) {
            user = createFrom(token, faculty, email);
            log.info("Provisioned a new local account for an SSO sign-in");
        } else {
            refresh(user, token, faculty);
        }

        applyAcademicPosition(user, faculty, profile.orElse(null));

        user.setLastLoginDate(LocalDateTime.now());
        // An SSO account never signs in with a password, so the first-login
        // password wizard must not be triggered for it.
        user.setIsFirstLogin(false);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private UserDtls createFrom(KkuSsoClient.SsoToken token, FsFaculty faculty, String email) {
        UserDtls user = new UserDtls();
        user.setEmail(email);
        user.setRole(ROLE_USER);
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);

        // Local password login is not available to SSO accounts. A random,
        // un-recorded value keeps the column non-null without creating a
        // credential anyone could use.
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        applyIdentity(user, token, faculty);
        return user;
    }

    private void refresh(UserDtls user, KkuSsoClient.SsoToken token, FsFaculty faculty) {
        // Only the display name is refreshed. Role, enabled state and quotas are
        // local decisions that an upstream directory must not silently override.
        applyIdentity(user, token, faculty);
    }

    private void applyIdentity(UserDtls user, KkuSsoClient.SsoToken token, FsFaculty faculty) {
        if (faculty != null) {
            if (faculty.getFirstName() != null) {
                user.setFirstName(faculty.getFirstName());
            }
            if (faculty.getLastName() != null) {
                user.setLastName(faculty.getLastName());
            }
            // คำนำหน้าจาก directory ใช้ได้เฉพาะตอนที่ SSO ยังไม่เคยยืนยันตำแหน่งให้เท่านั้น
            // พอ SSO พูดแล้ว applyAcademicPosition จะ derive คำนำหน้าจากตำแหน่งนั้นแทน
            // ถ้ายังทับตรงนี้ต่อ prefix เก่าจาก fs_faculty จะชนะทุกครั้งที่ล็อกอิน แล้วคำนำหน้า
            // กับตำแหน่งก็จะไม่ตรงกันอีก — ซึ่งคือบักที่การล็อกช่องนี้ตั้งใจจะปิด
            if (faculty.getPrefix() != null && !user.isAcademicPositionFromSso()) {
                user.setTitle(faculty.getPrefix());
            }
            // ตำแหน่งทางวิชาการไม่ได้อยู่ตรงนี้แล้ว — applyAcademicPosition ตัดสินให้
            if (faculty.getTel() != null && user.getMobileNumber() == null) {
                user.setMobileNumber(faculty.getTel());
            }

            // The directory only carries a combined name_en, so the split is
            // derived the same way UserDirectorySync does it.
            EnglishNameSplitter.Parts english = EnglishNameSplitter.split(faculty.getNameEn());
            if (english.firstName() != null) {
                user.setFirstNameEn(english.firstName());
            }
            if (english.lastName() != null) {
                user.setLastNameEn(english.lastName());
            }
            return;
        }

        // No directory record (an allowlisted admin): fall back to what SSO gave us.
        if (token.firstName() != null) {
            user.setFirstName(token.firstName());
        }
        if (token.lastName() != null) {
            user.setLastName(token.lastName());
        }
    }

    /**
     * ตัดสินตำแหน่งทางวิชาการจากต้นทางที่มี แล้วเขียนลงบัญชีถ้าเขียนได้
     *
     * <p>ลำดับความน่าเชื่อถือ: SSO ก่อน แล้วค่อย {@code fs_faculty} — SSO คือทะเบียนบุคลากร
     * ของมหาวิทยาลัยเอง ส่วน fs_faculty เป็นสำเนาที่ sync ผ่าน Fund Management อีกทอด
     *
     * <p>แต่ SSO ส่งมาแต่ภาษาไทย ({@code titleEng} คือคำนำหน้า Mr./Mrs. ไม่ใช่ตำแหน่ง)
     * ฝั่งอังกฤษจึง derive จาก {@link AcademicRank#englishLabel()} เสมอ ไม่ใช่รับมาตรง ๆ
     * และการผ่าน {@link AcademicRank#of} ยังทำให้ค่าที่ไม่ใช่ตำแหน่งวิชาการ (สายสนับสนุน
     * ได้ {@code positionName} เป็น "นักวิชาการคอมพิวเตอร์" เป็นต้น) ตกไปเองโดยไม่ต้องเดา
     *
     * <p>ทุกครั้งที่เขียน จะประทับ {@code academicPositionSyncedAt} ไว้ด้วย เพื่อให้ job
     * ที่รันตามเวลา (UserDirectorySync รายคืน, CpDirectorySyncService รายสัปดาห์) รู้ว่า
     * ห้ามเอาค่าเก่ามาทับกลับ
     */
    private void applyAcademicPosition(UserDtls user, FsFaculty faculty, KkuSsoClient.SsoProfile profile) {
        String stored = user.getAcademicPosition();
        String storedTitle = user.getTitle();
        AcademicRank ssoRank = profile == null
                ? null
                : AcademicRank.of(profile.positionName(), profile.levelName());

        boolean mayOverwrite = positionSyncPolicy.mayOverwrite(user.getId());
        boolean applied = false;

        if (ssoRank != null && mayOverwrite) {
            user.setAcademicPosition(ssoRank.thaiLabel());
            user.setAcademicPositionEn(ssoRank.englishLabel());
            user.setTitle(shortTitleFor(ssoRank, storedTitle));
            user.setAcademicPositionSyncedAt(LocalDateTime.now());
            applied = true;
        } else if (ssoRank == null && !user.isAcademicPositionFromSso()) {
            // SSO ไม่ได้ระบุตำแหน่งวิชาการมา (หรือดึง profile ไม่ได้) — ใช้ directory
            // เหมือนพฤติกรรมเดิม แต่ไม่ทับค่าที่ SSO เคยยืนยันไว้แล้ว
            if (faculty != null && faculty.getPositionTitle() != null) {
                user.setAcademicPosition(faculty.getPositionTitle());
            }
            if (faculty != null && faculty.getPositionEn() != null) {
                user.setAcademicPositionEn(faculty.getPositionEn());
            }
        }

        if (profile == null) {
            return;
        }

        // บรรทัดเดียวที่ตอบได้ว่า SSO ส่งตำแหน่งวิชาการมาที่ช่องไหน และค่าถูกเปลี่ยนโดยอะไร
        // ใช้ id ไม่ใช่อีเมล ตามที่คลาสนี้ตั้งใจไม่ log ตัวตนลงไป และไม่แตะ accessToken
        String logLine = "SSO academic position — userId={} positionName={} levelName={}"
                + " positionTypeName={} workline={} fsFaculty={} stored={} resolved={}"
                + " titleBefore={} titleAfter={} mayOverwrite={} applied={}";
        Object[] values = { user.getId(), profile.positionName(), profile.levelName(),
                profile.positionTypeName(), profile.workline(),
                faculty == null ? null : faculty.getPositionTitle(),
                stored, ssoRank == null ? null : ssoRank.thaiLabel(),
                storedTitle, user.getTitle(), mayOverwrite, applied };

        if (ssoRank != null && !mayOverwrite && !ssoRank.thaiLabel().equals(stored)) {
            // ทะเบียนไม่ตรงกับที่เราถืออยู่ แต่คนนี้มีคำร้องเดินอยู่จึงยังไม่แตะ —
            // ถ้าค้างนาน นักทรัพยากรบุคคลควรเห็นและตัดสินใจเอง
            log.warn(logLine, values);
        } else {
            log.info(logLine, values);
        }
    }

    /**
     * คำนำหน้าชื่อที่ตรงกับตำแหน่งใหม่ โดยคงวุฒิ ดร. จากคำนำหน้าเดิมไว้
     *
     * <p>ลำดับ argument เป็นสาระ ไม่ใช่รสนิยม: {@link AcademicTitleResolver#resolveShortTitle}
     * เก็บธง "มีวุฒิ ดร." จากทุก candidate แต่ยึดตำแหน่งจาก candidate แรกที่เข้าเค้า ตำแหน่งใหม่
     * จึงต้องมาก่อน — ถ้าสลับเป็น (คำนำหน้าเดิม, ตำแหน่ง) แล้วคำนำหน้าเดิมเป็น "รศ.ดร." มันจะ
     * match "รศ." ทันทีแล้วคืน "รศ.ดร." ตำแหน่งที่ทะเบียนเพิ่งยืนยันมาจะไม่มีผลอะไรเลย
     *
     * <p>ผลที่ได้: อ.ดร. + ผศ. → ผศ.ดร. (เลื่อนขึ้น คงวุฒิ), รศ.ดร. + ผศ. → ผศ.ดร. (แก้ค่าที่
     * สูงเกินจริง), นาย + ผศ. → ผศ. (ไม่มีวุฒิก็ไม่เติมให้)
     */
    private static String shortTitleFor(AcademicRank rank, String currentTitle) {
        return AcademicTitleResolver.resolveShortTitle(rank.thaiLabel(), currentTitle);
    }
}
