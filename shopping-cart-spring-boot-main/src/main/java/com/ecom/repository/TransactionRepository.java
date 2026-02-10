package com.ecom.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ecom.model.Transaction;
import com.ecom.model.UserDtls;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserOrderByCreatedAtDesc(UserDtls user);

    List<Transaction> findByUserAndStatusOrderByCreatedAtDesc(UserDtls user, Transaction.Status status);

    List<Transaction> findByStatusOrderByCreatedAtDesc(Transaction.Status status);

    boolean existsByRefTransactionId(String refTransactionId);

    Page<Transaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Transaction> findByTypeAndStatusOrderByCreatedAtDesc(Transaction.Type type, Transaction.Status status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'PURCHASE' AND t.status = 'SUCCESS'")
    Double getTotalPurchaseRevenue();

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.type = 'PURCHASE' AND t.status = 'SUCCESS'")
    Long getTotalPurchaseCount();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'TOPUP' AND t.status = 'SUCCESS'")
    Double getTotalTopupAmount();
}
