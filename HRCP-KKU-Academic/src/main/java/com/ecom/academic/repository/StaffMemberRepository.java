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

    /**
     * Active holders of a role, with their login account eagerly loaded.
     *
     * <p>The signer picker has to show whether each person can actually be sent a
     * signature request, so it needs the account on every row — a lazy proxy read
     * after the transaction closes would fail, and one query per row would not.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT s FROM StaffMember s
            LEFT JOIN FETCH s.user
            WHERE s.staffRole = :staffRole AND s.isActive = true
            ORDER BY s.firstName ASC, s.lastName ASC
            """)
    List<StaffMember> findActiveByRoleWithUser(
            @org.springframework.data.repository.query.Param("staffRole") String staffRole);

    /**
     * The active staff list with accounts attached, for the management table.
     *
     * <p>That table now shows a per-row "linked / not linked" badge, which without
     * the fetch would mean one extra query per person on every page load.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT s FROM StaffMember s
            LEFT JOIN FETCH s.user
            WHERE s.isActive = true
            ORDER BY s.firstName ASC, s.lastName ASC
            """)
    List<StaffMember> findAllActiveWithUser();

    /** The staff record backing a login account, used to resolve someone's signing role. */
    Optional<StaffMember> findByUserId(Integer userId);

    /** Guards the one-account-one-staff-row rule before the DB constraint has to. */
    Optional<StaffMember> findByUserIdAndIdNot(Integer userId, Long id);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM StaffMember s WHERE LOWER(s.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.department) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.academicTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<StaffMember> searchStaff(@org.springframework.data.repository.query.Param("keyword") String keyword);
}
