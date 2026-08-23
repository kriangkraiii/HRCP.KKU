package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.UserSignature;

public interface UserSignatureRepository extends JpaRepository<UserSignature, Long> {

    /** Someone's signature library, newest first, excluding soft-deleted rows. */
    List<UserSignature> findByUserIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(Integer userId);

    /**
     * Fetches a signature as its owner.
     *
     * <p>Ownership is part of the query rather than checked afterwards, so there
     * is no path where a caller forgets the check — a signature image is personal
     * data and must never be readable by whoever guesses an id.
     */
    Optional<UserSignature> findByIdAndUserIdAndIsDeletedFalse(Long id, Integer userId);

    long countByUserIdAndIsDeletedFalse(Integer userId);

    /**
     * Clears the default flag across someone's signatures.
     *
     * <p>Run immediately before setting a new one: the partial unique index
     * allows a single default per person, so the old one has to go first.
     */
    @Modifying
    @Query("UPDATE UserSignature s SET s.isDefault = false WHERE s.user.id = :userId AND s.isDefault = true")
    void clearDefaultFor(@Param("userId") Integer userId);

    Optional<UserSignature> findByUserIdAndIsDefaultTrueAndIsDeletedFalse(Integer userId);
}
