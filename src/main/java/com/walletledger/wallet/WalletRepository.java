package com.walletledger.wallet;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByPlayerId(String playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from Wallet wallet where wallet.playerId = :playerId")
    Optional<Wallet> findByPlayerIdForUpdate(@Param("playerId") String playerId);
}
