package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.AcademicCommitteeMember;
import com.ecom.academic.model.CommitteeType;

public interface AcademicCommitteeMemberRepository extends JpaRepository<AcademicCommitteeMember, Long> {

    List<AcademicCommitteeMember> findByIsActiveTrueOrderByFirstNameAscLastNameAsc();

    Optional<AcademicCommitteeMember> findByEmailIgnoreCaseAndIsActiveTrue(String email);

    List<AcademicCommitteeMember> findByCommitteeTypeAndIsActiveTrueOrderByFirstNameAscLastNameAsc(CommitteeType committeeType);

    @Query("SELECT c FROM AcademicCommitteeMember c WHERE c.isActive = true AND "
            + "(LOWER(c.firstName) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(coalesce(c.firstNameEn, '')) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(coalesce(c.lastNameEn, '')) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(c.affiliation) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(coalesce(c.affiliationEn, '')) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(coalesce(c.expertiseField, '')) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "OR LOWER(coalesce(c.expertiseFieldEn, '')) LIKE LOWER(CONCAT('%', :kw, '%'))) "
            + "ORDER BY c.firstName ASC, c.lastName ASC")
    List<AcademicCommitteeMember> searchActive(@Param("kw") String keyword);

    boolean existsByEmailIgnoreCase(String email);
}
