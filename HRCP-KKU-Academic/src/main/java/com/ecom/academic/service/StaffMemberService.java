package com.ecom.academic.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;

@Service
public class StaffMemberService {

    @Autowired
    private StaffMemberRepository staffMemberRepository;

    public List<StaffMember> findAll() {
        return staffMemberRepository.findByIsActiveTrueOrderByFullNameAsc();
    }

    public Optional<StaffMember> findById(Long id) {
        return staffMemberRepository.findById(id);
    }

    public List<StaffMember> findByRole(String role) {
        return staffMemberRepository.findByStaffRoleAndIsActiveTrueOrderByFullNameAsc(role);
    }

    public StaffMember save(StaffMember staffMember) {
        return staffMemberRepository.save(staffMember);
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
