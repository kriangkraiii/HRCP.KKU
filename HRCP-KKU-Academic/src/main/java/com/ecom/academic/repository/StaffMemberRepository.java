package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.StaffMember;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

    List<StaffMember> findByIsActiveTrueOrderByFirstNameAscLastNameAsc();

    /** The row mirroring a given faculty record, if it has been imported. */
    Optional<StaffMember> findByFsUserId(Long fsUserId);

    /**
     * Hand-entered rows matching a name, used once to adopt someone who was
     * typed in before the import existed rather than creating a duplicate of them.
     */
    List<StaffMember> findByFsUserIdIsNullAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
            String firstName, String lastName);

    long countByFsUserIdIsNotNull();

    /** Everyone recorded under a name, for the duplicate warning on the add form. */
    List<StaffMember> findByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);

    List<StaffMember> findByStaffRoleAndIsActiveTrueOrderByFirstNameAscLastNameAsc(String staffRole);

    List<StaffMember> findByStaffRole(String staffRole);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM StaffMember s WHERE LOWER(s.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.department) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.academicTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<StaffMember> searchStaff(@org.springframework.data.repository.query.Param("keyword") String keyword);
}
