package com.walletledger.wallet;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletOperationRepository walletOperationRepository;

    public WalletService(WalletRepository walletRepository, WalletOperationRepository walletOperationRepository) {
        this.walletRepository = walletRepository;
        this.walletOperationRepository = walletOperationRepository;
    }

    @Transactional
    public Wallet createWallet(String playerId, String name, String currency) {
        if (walletRepository.findByPlayerId(playerId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A wallet already exists for this player");
        }

        try {
            Wallet wallet = walletRepository.saveAndFlush(new Wallet(playerId, name, currency));
            walletOperationRepository.save(new WalletOperation(wallet, OperationType.CREATE, BigDecimal.ZERO,
                    BigDecimal.ZERO, "wallet-create", "wallet created"));
            return wallet;
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A wallet already exists for this player", exception);
        }
    }

    @Transactional
    public WalletOperation applyOperation(String playerId, OperationType operationType, BigDecimal amount,
            String idempotencyKey, String reason) {
        Wallet wallet = walletRepository.findByPlayerIdForUpdate(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        return walletOperationRepository.findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey)
            .map(existing -> replayOrReject(existing, operationType, amount, reason))
            .orElseGet(() -> createOperation(wallet, operationType, amount, idempotencyKey, reason));
    }

    @Transactional
    public List<BulkRewardResult> distributeRewards(List<BulkReward> rewards, String idempotencyKey, String reason) {
        List<String> playerIds = rewards.stream().map(BulkReward::playerId).sorted().toList();
        List<Wallet> wallets = walletRepository.findAllByPlayerIdInForUpdate(playerIds);
        if (wallets.size() != rewards.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more wallets were not found");
        }

        Map<String, Wallet> walletsByPlayerId = new HashMap<>();
        for (Wallet wallet : wallets) {
            walletsByPlayerId.put(wallet.getPlayerId(), wallet);
        }

        return rewards.stream().map(reward -> {
            Wallet wallet = walletsByPlayerId.get(reward.playerId());
            WalletOperation operation = walletOperationRepository
                    .findByWalletIdAndIdempotencyKey(wallet.getId(), bulkOperationKey(idempotencyKey, reward.playerId()))
                    .map(existing -> replayOrReject(existing, OperationType.CREDIT, reward.amount(), reason))
                    .orElseGet(() -> createOperation(wallet, OperationType.CREDIT, reward.amount(),
                            bulkOperationKey(idempotencyKey, reward.playerId()), reason));
            return new BulkRewardResult(reward.playerId(), operation);
        }).toList();
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(String playerId) {
        return walletRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    @Transactional(readOnly = true)
    public Page<WalletOperation> getHistory(String playerId, int page, int size) {
        Wallet wallet = getWallet(playerId);
        return walletOperationRepository.findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public BalanceCheck checkBalance(String playerId) {
        Wallet wallet = getWallet(playerId);
        BigDecimal ledgerBalance = walletOperationRepository.calculateBalanceFromOperations(wallet.getId());
        return new BalanceCheck(wallet.getBalance(), ledgerBalance, wallet.getBalance().compareTo(ledgerBalance) == 0);
    }

    private WalletOperation replayOrReject(WalletOperation existing, OperationType operationType, BigDecimal amount,
            String reason) {
        if (existing.getOperationType() != operationType || existing.getAmount().compareTo(amount) != 0
                || !existing.getReason().equals(reason)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key has already been used for a different operation");
        }
        return existing;
    }

    private WalletOperation createOperation(Wallet wallet, OperationType operationType, BigDecimal amount,
            String idempotencyKey, String reason) {
        BigDecimal balance = wallet.getBalance();
        BigDecimal balanceAfter = operationType == OperationType.CREDIT ? balance.add(amount) : balance.subtract(amount);

        if (balanceAfter.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient wallet balance");
        }

        wallet.setBalance(balanceAfter);
        return walletOperationRepository.save(
            new WalletOperation(wallet, operationType, amount, balanceAfter, idempotencyKey, reason));
    }

    private String bulkOperationKey(String idempotencyKey, String playerId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((idempotencyKey + "\u0000" + playerId).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("bulk-");
            for (byte hashByte : hash) {
                value.append(String.format("%02x", hashByte));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record BulkReward(String playerId, BigDecimal amount) {
    }

    public record BulkRewardResult(String playerId, WalletOperation operation) {
    }

    public record BalanceCheck(BigDecimal currentBalance, BigDecimal ledgerBalance, boolean matches) {
    }
}
