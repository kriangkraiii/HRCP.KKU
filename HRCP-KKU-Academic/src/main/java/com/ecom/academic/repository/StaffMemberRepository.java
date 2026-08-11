package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.StaffMember;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

    List<StaffMember> findByIsActiveTrueOrderByFirstNameAscLastNameAsc();

    List<StaffMember> findByStaffRoleAndIsActiveTrueOrderByFirstNameAscLastNameAsc(String staffRole);

    List<StaffMember> findByStaffRole(String staffRole);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM StaffMember s WHERE LOWER(s.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.department) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.academicTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<StaffMember> searchStaff(@org.springframework.data.repository.query.Param("keyword") String keyword);
}
