package com.ecom.academic.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;

@Service
public class StaffMemberService {

    private final StaffMemberRepository staffMemberRepository;

    public StaffMemberService(StaffMemberRepository staffMemberRepository) {
        this.staffMemberRepository = staffMemberRepository;
    }

    public List<StaffMember> findAll() {
        return staffMemberRepository.findByIsActiveTrueOrderByFirstNameAscLastNameAsc();
    }

    public Optional<StaffMember> findById(Long id) {
        return staffMemberRepository.findById(id);
    }

    public List<StaffMember> findByRole(String role) {
        return staffMemberRepository.findByStaffRoleAndIsActiveTrueOrderByFirstNameAscLastNameAsc(role);
    }

    public StaffMember save(StaffMember staffMember) {
        return staffMemberRepository.save(staffMember);
    }

    /**
     * Everyone already recorded under this name, active or not.
     *
     * <p>Used to warn before adding what is probably the same person again.
     * Inactive rows count: re-adding someone who was retired is exactly the case
     * worth catching, since the old row is still named in past documents.
     */
    public List<StaffMember> findByName(String firstName, String lastName) {
        if (firstName == null || lastName == null) {
            return List.of();
        }
        return staffMemberRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(
                firstName.trim(), lastName.trim());
    }

    public void softDelete(Long id) {
        staffMemberRepository.findById(id).ifPresent(staff -> {
            staff.setIsActive(false);
            staffMemberRepository.save(staff);
        });
    }

    public List<StaffMember> findDeans() {
        return findByRole("DEAN");
    }

    public List<StaffMember> findHeads() {
        return findByRole("HEAD");
    }

    public List<StaffMember> findCommittee() {
        return findByRole("COMMITTEE");
    }

    public List<StaffMember> findHR() {
        return findByRole("HR");
    }

    public List<StaffMember> findGeneral() {
        return findByRole("GENERAL");
    }
}
