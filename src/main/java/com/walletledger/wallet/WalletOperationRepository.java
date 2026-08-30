package com.walletledger.wallet;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletOperationRepository extends JpaRepository<WalletOperation, Long> {

    Optional<WalletOperation> findByWalletIdAndIdempotencyKey(Long walletId, String idempotencyKey);

    Page<WalletOperation> findByWalletIdOrderByCreatedAtDescIdDesc(Long walletId, Pageable pageable);

    @Query("select coalesce(sum(case when operation.operationType = com.walletledger.wallet.OperationType.CREDIT "
            + "then operation.amount when operation.operationType = com.walletledger.wallet.OperationType.DEBIT "
            + "then -operation.amount else 0 end), 0) from WalletOperation operation where operation.wallet.id = :walletId")
    BigDecimal calculateBalanceFromOperations(@Param("walletId") Long walletId);
}
