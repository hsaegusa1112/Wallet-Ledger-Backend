package com.walletledger.wallet;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletOperationRepository extends JpaRepository<WalletOperation, Long> {

    Optional<WalletOperation> findByWalletIdAndIdempotencyKey(Long walletId, String idempotencyKey);

    Page<WalletOperation> findByWalletIdOrderByCreatedAtDescIdDesc(Long walletId, Pageable pageable);
}
