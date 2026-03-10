package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.StaffMember;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

    List<StaffMember> findByIsActiveTrueOrderByFullNameAsc();

    List<StaffMember> findByStaffRoleAndIsActiveTrueOrderByFullNameAsc(String staffRole);

    List<StaffMember> findByStaffRole(String staffRole);
}
