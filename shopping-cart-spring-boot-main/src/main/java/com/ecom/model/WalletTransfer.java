package com.ecom.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity สำหรับเก็บประวัติการโอนเงินระหว่าง User
 */
@Entity
@Table(name = "wallet_transfers")
public class WalletTransfer {

    public enum TransferStatus {
        SUCCESS, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private UserDtls sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private UserDtls receiver;

    @Column(nullable = false)
    private Double amount;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "sender_balance_after")
    private Double senderBalanceAfter;

    @Column(name = "receiver_balance_after")
    private Double receiverBalanceAfter;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TransferStatus.SUCCESS;
        }
    }

    // Default constructor
    public WalletTransfer() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserDtls getSender() {
        return sender;
    }

    public void setSender(UserDtls sender) {
        this.sender = sender;
    }

    public UserDtls getReceiver() {
        return receiver;
    }

    public void setReceiver(UserDtls receiver) {
        this.receiver = receiver;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Double getSenderBalanceAfter() {
        return senderBalanceAfter;
    }

    public void setSenderBalanceAfter(Double senderBalanceAfter) {
        this.senderBalanceAfter = senderBalanceAfter;
    }

    public Double getReceiverBalanceAfter() {
        return receiverBalanceAfter;
    }

    public void setReceiverBalanceAfter(Double receiverBalanceAfter) {
        this.receiverBalanceAfter = receiverBalanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
