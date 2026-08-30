package com.walletledger.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "wallet_operations")
public class WalletOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private OperationType operationType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected WalletOperation() {
    }

    public WalletOperation(Wallet wallet, OperationType operationType, BigDecimal amount, BigDecimal balanceAfter,
            String idempotencyKey, String reason) {
        this.wallet = wallet;
        this.operationType = operationType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.idempotencyKey = idempotencyKey;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
