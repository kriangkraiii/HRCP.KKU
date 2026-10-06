package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.ActingSigner;

public interface ActingSignerRepository extends JpaRepository<ActingSigner, Long> {

    @Query("SELECT a FROM ActingSigner a LEFT JOIN FETCH a.actingUser LEFT JOIN FETCH a.updatedBy WHERE a.slotKey = :slotKey")
    Optional<ActingSigner> findBySlotKey(@Param("slotKey") String slotKey);

    @Query("SELECT a FROM ActingSigner a LEFT JOIN FETCH a.actingUser LEFT JOIN FETCH a.updatedBy")
    List<ActingSigner> findAllWithUsers();
}
