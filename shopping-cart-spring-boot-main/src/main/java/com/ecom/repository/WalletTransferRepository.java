package com.ecom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ecom.model.UserDtls;
import com.ecom.model.WalletTransfer;

@Repository
public interface WalletTransferRepository extends JpaRepository<WalletTransfer, Long> {

    /** ดึงรายการโอนที่เราเป็นผู้ส่ง (เรียงจากล่าสุด) */
    List<WalletTransfer> findBySenderOrderByCreatedAtDesc(UserDtls sender);

    /** ดึงรายการโอนที่เราเป็นผู้รับ (เรียงจากล่าสุด) */
    List<WalletTransfer> findByReceiverOrderByCreatedAtDesc(UserDtls receiver);

    /** ดึงรายการโอนทั้งหมดที่เกี่ยวข้องกับเรา (ส่ง+รับ) เรียงจากล่าสุด */
    @Query("SELECT t FROM WalletTransfer t WHERE t.sender = :user OR t.receiver = :user ORDER BY t.createdAt DESC")
    List<WalletTransfer> findAllByUser(@Param("user") UserDtls user);

    /** ดึงรายการโอนสำเร็จทั้งหมดที่เกี่ยวกับ user */
    @Query("SELECT t FROM WalletTransfer t WHERE (t.sender = :user OR t.receiver = :user) AND t.status = 'SUCCESS' ORDER BY t.createdAt DESC")
    List<WalletTransfer> findSuccessfulByUser(@Param("user") UserDtls user);
}
