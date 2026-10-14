package com.ecom.academic.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.ExternalSignInLink;

public interface ExternalSignInLinkRepository extends JpaRepository<ExternalSignInLink, Long> {

    Optional<ExternalSignInLink> findByTokenHash(String tokenHash);

    /**
     * ใช้ลิงก์ — คืน 1 เมื่อใช้สำเร็จ 0 เมื่อมีคนใช้ไปก่อนแล้ว (กดสองครั้งพร้อมกันได้ session เดียว)
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ExternalSignInLink l SET l.usedAt = :now, l.usedIp = :ip
            WHERE l.id = :id AND l.usedAt IS NULL AND l.expiresAt > :now
            """)
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("ip") String ip);

    @Modifying
    @Query("DELETE FROM ExternalSignInLink l WHERE l.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
