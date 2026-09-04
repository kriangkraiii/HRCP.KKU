package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.UserDigitalCertificate;

public interface UserDigitalCertificateRepository extends JpaRepository<UserDigitalCertificate, Long> {

    List<UserDigitalCertificate> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Integer userId);

    Optional<UserDigitalCertificate> findByIdAndUserId(Long id, Integer userId);

    Optional<UserDigitalCertificate> findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Integer userId);

    @Modifying
    @Query("UPDATE UserDigitalCertificate c SET c.isActive = false WHERE c.user.id = :userId")
    void deactivateAllFor(@Param("userId") Integer userId);
}
