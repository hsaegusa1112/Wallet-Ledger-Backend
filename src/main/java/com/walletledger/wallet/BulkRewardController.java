package com.walletledger.wallet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rewards")
public class BulkRewardController {

    private final WalletService walletService;

    public BulkRewardController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.OK)
    List<BulkRewardResponse> distribute(@RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody BulkRewardRequest request) {
        ensureUniquePlayers(request.rewards());
        return walletService.distributeRewards(
                request.rewards().stream().map(reward -> new WalletService.BulkReward(reward.playerId(), reward.amount())).toList(),
                idempotencyKey, request.reason()).stream().map(BulkRewardResponse::from).toList();
    }

    private void ensureUniquePlayers(List<RewardRecipient> rewards) {
        Set<String> playerIds = new HashSet<>();
        if (rewards.stream().map(RewardRecipient::playerId).anyMatch(playerId -> !playerIds.add(playerId))) {
            throw new IllegalArgumentException("A player can receive only one reward per bulk request");
        }
    }

    record BulkRewardRequest(@NotBlank @Size(max = 255) String reason,
            @NotEmpty @Size(max = 100) List<@Valid RewardRecipient> rewards) {
    }

    record RewardRecipient(@NotBlank String playerId,
            @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount) {
    }

    record BulkRewardResponse(String playerId, Long operationId, BigDecimal amount, BigDecimal balanceAfter,
            String reason, OffsetDateTime createdAt) {
        static BulkRewardResponse from(WalletService.BulkRewardResult result) {
            WalletOperation operation = result.operation();
            return new BulkRewardResponse(result.playerId(), operation.getId(), operation.getAmount(),
                    operation.getBalanceAfter(), operation.getReason(), operation.getCreatedAt());
        }
    }
}