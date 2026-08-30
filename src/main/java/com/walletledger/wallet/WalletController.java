package com.walletledger.wallet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/players/{playerId}/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WalletBalanceResponse createWallet(@PathVariable String playerId, @Valid @RequestBody CreateWalletRequest request) {
        return WalletBalanceResponse.from(walletService.createWallet(playerId, request.name(), request.currency()));
    }

    @PostMapping("/credits")
        WalletOperationResponse credit(@PathVariable String playerId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        return WalletOperationResponse.from(
            walletService.applyOperation(playerId, OperationType.CREDIT, request.amount(), idempotencyKey,
                request.reason()));
    }

    @PostMapping("/debits")
        WalletOperationResponse debit(@PathVariable String playerId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        return WalletOperationResponse.from(
            walletService.applyOperation(playerId, OperationType.DEBIT, request.amount(), idempotencyKey,
                request.reason()));
    }

    @GetMapping("/balance")
    WalletBalanceResponse getBalance(@PathVariable String playerId) {
        return WalletBalanceResponse.from(walletService.getWallet(playerId));
    }

    @GetMapping("/balance-check")
    BalanceCheckResponse checkBalance(@PathVariable String playerId) {
        return BalanceCheckResponse.from(walletService.checkBalance(playerId));
    }

    @GetMapping("/history")
    Page<WalletOperationResponse> getHistory(@PathVariable String playerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return walletService.getHistory(playerId, page, size).map(WalletOperationResponse::from);
    }

        record CreateWalletRequest(@NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
    }

        record MoneyRequest(@NotNull @DecimalMin(value = "0.0001") @jakarta.validation.constraints.Digits(integer = 15, fraction = 4) BigDecimal amount,
            @NotBlank @jakarta.validation.constraints.Size(max = 255) String reason) {
    }

    record WalletBalanceResponse(String playerId, String currency, BigDecimal balance) {
        static WalletBalanceResponse from(Wallet wallet) {
            return new WalletBalanceResponse(wallet.getPlayerId(), wallet.getCurrency(), wallet.getBalance());
        }
    }

    record BalanceCheckResponse(BigDecimal currentBalance, BigDecimal ledgerBalance, boolean matches) {
        static BalanceCheckResponse from(WalletService.BalanceCheck balanceCheck) {
            return new BalanceCheckResponse(balanceCheck.currentBalance(), balanceCheck.ledgerBalance(),
                    balanceCheck.matches());
        }
    }

        record WalletOperationResponse(Long id, OperationType operationType, BigDecimal amount, BigDecimal balanceAfter,
            String reason, OffsetDateTime createdAt) {
        static WalletOperationResponse from(WalletOperation operation) {
            return new WalletOperationResponse(operation.getId(), operation.getOperationType(), operation.getAmount(),
                operation.getBalanceAfter(), operation.getReason(), operation.getCreatedAt());
        }
    }
}