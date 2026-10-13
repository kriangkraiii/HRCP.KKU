package com.ecom.academic.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicCommitteeMember;
import com.ecom.academic.model.CommitteeType;
import com.ecom.academic.repository.AcademicCommitteeMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@Service
public class AcademicCommitteeService {

    private static final Logger log = LoggerFactory.getLogger(AcademicCommitteeService.class);

    private final AcademicCommitteeMemberRepository committeeRepository;
    private final UserRepository userRepository;

    public AcademicCommitteeService(
            AcademicCommitteeMemberRepository committeeRepository,
            UserRepository userRepository) {
        this.committeeRepository = committeeRepository;
        this.userRepository = userRepository;
    }

    public List<AcademicCommitteeMember> findAllActive() {
        return committeeRepository.findByIsActiveTrueOrderByFirstNameAscLastNameAsc();
    }

    public Optional<AcademicCommitteeMember> findById(Long id) {
        return committeeRepository.findById(id);
    }

    public Optional<AcademicCommitteeMember> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return committeeRepository.findByEmailIgnoreCaseAndIsActiveTrue(email.trim());
    }

    public List<AcademicCommitteeMember> findByType(CommitteeType type) {
        if (type == null) {
            return findAllActive();
        }
        return committeeRepository.findByCommitteeTypeAndIsActiveTrueOrderByFirstNameAscLastNameAsc(type);
    }

    public List<AcademicCommitteeMember> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAllActive();
        }
        return committeeRepository.searchActive(keyword.trim());
    }

    @Transactional
    public AcademicCommitteeMember save(AcademicCommitteeMember member) {
        if (member.getEmail() != null) {
            member.setEmail(member.getEmail().trim().toLowerCase());
        }
        member.setFirstName(member.getFirstName() != null ? member.getFirstName().trim() : null);
        member.setLastName(member.getLastName() != null ? member.getLastName().trim() : null);
        rejectDuplicate(member);

        // Link with user account if exists or create placeholder
        UserDtls existingUser = userRepository.findByEmail(member.getEmail());
        if (existingUser != null) {
            member.setUser(existingUser);
        } else {
            UserDtls provisioned = provisionCommitteeUser(member);
            member.setUser(provisioned);
        }

        return committeeRepository.save(member);
    }

    private void rejectDuplicate(AcademicCommitteeMember member) {
        List<AcademicCommitteeMember> duplicates = committeeRepository.findActiveDuplicates(
                member.getEmail(), member.getFirstName(), member.getLastName(), member.getId());
        if (duplicates.isEmpty()) {
            return;
        }
        AcademicCommitteeMember existing = duplicates.get(0);
        boolean sameEmail = existing.getEmail() != null && existing.getEmail().equalsIgnoreCase(member.getEmail());
        String reason = sameEmail
                ? "อีเมล " + member.getEmail() + " ถูกใช้แล้ว"
                : "ชื่อ-นามสกุล " + member.getFirstName() + " " + member.getLastName() + " มีอยู่แล้ว";
        throw new DuplicateCommitteeMemberException("มีข้อมูลกรรมการนี้ในระบบแล้ว: " + reason
                + " (" + existing.getFullName() + ", " + existing.getAffiliation() + ")");
    }

    @Transactional
    public boolean delete(Long id) {
        Optional<AcademicCommitteeMember> opt = committeeRepository.findById(id);
        if (opt.isPresent()) {
            AcademicCommitteeMember member = opt.get();
            member.setIsActive(false);
            committeeRepository.save(member);
            return true;
        }
        return false;
    }

    /**
     * Provisions a local UserDtls for a committee member so they can be assigned
     * signature steps and authenticate via KKU SSO without needing a local password.
     */
    @Transactional
    public UserDtls provisionCommitteeUser(AcademicCommitteeMember member) {
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            return null;
        }
        String email = member.getEmail().trim().toLowerCase();
        UserDtls user = userRepository.findByEmail(email);
        if (user != null) {
            return user;
        }

        UserDtls newUser = new UserDtls();
        newUser.setEmail(email);
        newUser.setTitle(member.getTitle());
        newUser.setFirstName(member.getFirstName());
        newUser.setLastName(member.getLastName());
        newUser.setName(member.getFullName());
        newUser.setAcademicPosition(member.getAcademicPosition() != null ? member.getAcademicPosition() : member.getTitle());
        newUser.setRole("ROLE_USER");
        newUser.setIsEnable(true);
        newUser.setAccountNonLocked(true);
        newUser.setIsFirstLogin(false);
        newUser.setEmailVerified(true);
        newUser.setPassword("$2a$10$UNUSABLE_SSO_ONLY_PASSWORD_HASH_GUARD"); // SSO only, no direct password login

        log.info("Provisioned user account for committee member: {} ({})", member.getFullName(), email);
        return userRepository.save(newUser);
    }
}
