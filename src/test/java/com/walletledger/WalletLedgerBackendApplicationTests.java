package com.walletledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WalletLedgerBackendApplicationTests {

    private static final BigDecimal TEN = new BigDecimal("10.0000");
    private static final String BASE_URL = System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:8080");
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void waitForApplication() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/actuator/health"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")) {
                    return;
                }
            } catch (java.io.IOException exception) {
                // The application container is still starting.
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Application did not become healthy within 30 seconds: " + BASE_URL);
    }

    @Test
    void concurrentCreditsUseTheWalletLockAndPreserveEveryUpdate() throws Exception {
        String playerId = newPlayerId();
        createWallet(playerId);

        List<HttpResponse<String>> responses = runConcurrently(10, index ->
            applyOperation(playerId, "credits", TEN, "credit-" + index, "concurrent reward"));

        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(balance(playerId)).isEqualByComparingTo("100.0000");
    }

    @Test
    void concurrentDebitsNeverOverdrawTheWallet() throws Exception {
        String playerId = newPlayerId();
        createWallet(playerId);
        assertThat(applyOperation(playerId, "credits", new BigDecimal("100.0000"), "initial-credit", "seed funds").statusCode())
                .isEqualTo(200);

        List<OperationResult> results = runConcurrently(10, index -> {
            try {
                int statusCode = applyOperation(playerId, "debits", new BigDecimal("15.0000"), "debit-" + index, "purchase")
                        .statusCode();
                return statusCode == 200 ? OperationResult.SUCCESS : OperationResult.REJECTED;
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        assertThat(results).filteredOn(result -> result == OperationResult.SUCCESS).hasSize(6);
        assertThat(results).filteredOn(result -> result == OperationResult.REJECTED).hasSize(4);
        assertThat(balance(playerId)).isEqualByComparingTo("10.0000");
    }

    @Test
    void concurrentRequestsWithTheSameIdempotencyKeyCreateOneOperation() throws Exception {
        String playerId = newPlayerId();
        createWallet(playerId);

        List<HttpResponse<String>> responses = runConcurrently(10, index ->
            applyOperation(playerId, "credits", TEN, "same-credit-key", "daily reward"));

        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(responses).extracting(HttpResponse::body).containsOnly(responses.get(0).body());
        assertThat(balance(playerId)).isEqualByComparingTo("10.0000");
        assertThat(history(playerId)).contains("\"totalElements\":2");
    }

        @Test
        void creditDebitAndHistoryPreserveReasonsAndRejectInsufficientFunds() throws Exception {
        String playerId = newPlayerId();
        createWallet(playerId);

        assertThat(applyOperation(playerId, "credits", new BigDecimal("25.0000"), "credit-1", "mission reward")
            .statusCode()).isEqualTo(200);
        assertThat(applyOperation(playerId, "debits", new BigDecimal("10.0000"), "debit-1", "item purchase")
            .statusCode()).isEqualTo(200);
        assertThat(applyOperation(playerId, "debits", new BigDecimal("20.0000"), "debit-2", "expensive purchase")
            .statusCode()).isEqualTo(422);
        assertThat(balance(playerId)).isEqualByComparingTo("15.0000");
        assertThat(balanceCheck(playerId)).contains("\"currentBalance\":15.0000", "\"ledgerBalance\":15.0000",
            "\"matches\":true");

        String history = history(playerId);
        assertThat(history).contains("\"totalElements\":3", "\"reason\":\"mission reward\"",
            "\"reason\":\"item purchase\"");
        }

        @Test
        void changedIdempotentRequestAndInvalidInputsAreRejected() throws Exception {
        String playerId = newPlayerId();
        createWallet(playerId);

        assertThat(applyOperation(playerId, "credits", TEN, "reused-key", "mission reward").statusCode()).isEqualTo(200);
        assertThat(applyOperation(playerId, "credits", new BigDecimal("11.0000"), "reused-key", "mission reward")
            .statusCode()).isEqualTo(409);
        assertThat(applyOperation(playerId, "credits", new BigDecimal("1.00001"), "too-precise", "mission reward")
            .statusCode()).isEqualTo(400);
        assertThat(get(playerId, "/history?page=-1&size=20").statusCode()).isEqualTo(400);
        assertThat(balance(playerId)).isEqualByComparingTo("10.0000");
        }

    @Test
    void bulkRewardsAreAtomicAndIdempotentWithDifferentAmountsPerPlayer() throws Exception {
        String firstPlayerId = newPlayerId();
        String secondPlayerId = newPlayerId();
        createWallet(firstPlayerId);
        createWallet(secondPlayerId);

        String requestBody = "{\"reason\":\"tournament placement\",\"rewards\":[{\"playerId\":\""
                + firstPlayerId + "\",\"amount\":100.0000},{\"playerId\":\"" + secondPlayerId
                + "\",\"amount\":25.5000}]}";
        assertThat(bulkRewards(requestBody, "tournament-001").statusCode()).isEqualTo(200);
        assertThat(bulkRewards(requestBody, "tournament-001").statusCode()).isEqualTo(200);
        assertThat(balance(firstPlayerId)).isEqualByComparingTo("100.0000");
        assertThat(balance(secondPlayerId)).isEqualByComparingTo("25.5000");

        String invalidBatch = "{\"reason\":\"should roll back\",\"rewards\":[{\"playerId\":\""
                + firstPlayerId + "\",\"amount\":10.0000},{\"playerId\":\"unknown-player\",\"amount\":10.0000}]}";
        assertThat(bulkRewards(invalidBatch, "tournament-002").statusCode()).isEqualTo(404);
        assertThat(balance(firstPlayerId)).isEqualByComparingTo("100.0000");
    }

    private <T> List<T> runConcurrently(int requestCount, ConcurrentOperation<T> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return operation.run(requestIndex);
                }));
            }
            ready.await();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private String newPlayerId() {
        return "test-" + UUID.randomUUID();
    }

    private void createWallet(String playerId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(walletUri(playerId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test Player\",\"currency\":\"USD\"}"))
                .build();
        assertThat(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
    }

    private HttpResponse<String> applyOperation(String playerId, String operationPath, BigDecimal amount,
            String idempotencyKey, String reason) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(walletUri(playerId) + "/" + operationPath))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":" + amount.toPlainString()
                + ",\"reason\":\"" + reason + "\"}"))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> bulkRewards(String requestBody, String idempotencyKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/rewards/bulk"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private BigDecimal balance(String playerId) throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(walletUri(playerId) + "/balance"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String value = response.body().replaceAll(".*\"balance\":([0-9.]+).*", "$1");
        return new BigDecimal(value);
    }

    private String history(String playerId) throws Exception {
        HttpResponse<String> response = get(playerId, "/history?page=0&size=20");
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private String balanceCheck(String playerId) throws Exception {
        HttpResponse<String> response = get(playerId, "/balance-check");
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private HttpResponse<String> get(String playerId, String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(walletUri(playerId) + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI walletUri(String playerId) {
        return URI.create(BASE_URL + "/players/" + playerId + "/wallet");
    }

    @FunctionalInterface
    private interface ConcurrentOperation<T> {
        T run(int requestIndex) throws Exception;
    }

    private enum OperationResult {
        SUCCESS,
        REJECTED
    }
}
