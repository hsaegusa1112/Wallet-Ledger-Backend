package com.walletledger.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, unique = true)
    private String playerId;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(Types.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Wallet() {
    }

    public Wallet(String playerId, String name, String currency) {
        this.playerId = playerId;
        this.name = name;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
