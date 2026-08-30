# Wallet API Testing

Start the application and wait for it to become healthy:

```sh
docker compose up --build -d
curl --retry 30 --retry-delay 1 --fail http://localhost:8080/actuator/health
```

Set a unique player ID for this test run. A timestamp avoids conflicts with data retained in the Docker volume.

```sh
export BASE_URL=http://localhost:8080
export PLAYER_ID="player-test-$(date +%s)"
```

## Create a wallet

```sh
curl -i -X POST "$BASE_URL/players/$PLAYER_ID/wallet" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Test Player","currency":"USD"}'
```

Expected: `201 Created` and a balance of `0`. Creation also creates a `CREATE` operation in the transaction history.

## Credit the wallet

```sh
curl -i -X POST "$BASE_URL/players/$PLAYER_ID/wallet/credits" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: credit-test-001' \
  -d '{"amount":100.0000}'
```

Expected: `200 OK`, a `CREDIT` operation, and `balanceAfter` of `100.0000`.

## Verify credit idempotency

Repeat the exact credit request with the same idempotency key:

```sh
curl -i -X POST "$BASE_URL/players/$PLAYER_ID/wallet/credits" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: credit-test-001' \
  -d '{"amount":100.0000}'
```

Expected: `200 OK` with the original operation. No second credit is applied.

Reusing that key with a different request is rejected:

```sh
curl -i -X POST "$BASE_URL/players/$PLAYER_ID/wallet/credits" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: credit-test-001' \
  -d '{"amount":50.0000}'
```

Expected: `409 Conflict`.

## Debit the wallet

```sh
curl -i -X POST "$BASE_URL/players/$PLAYER_ID/wallet/debits" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: debit-test-001' \
  -d '{"amount":30.0000}'
```

Expected: `200 OK`, a `DEBIT` operation, and `balanceAfter` of `70.0000`.

## Reject insufficient funds

```sh
curl -i -X POST "$BASE_URL/players/$PLAYER_ID/wallet/debits" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: debit-test-002' \
  -d '{"amount":100.0000}'
```

Expected: `422 Unprocessable Entity`. The balance remains `70.0000`, and no debit operation is recorded.

## Get the current balance

```sh
curl -i "$BASE_URL/players/$PLAYER_ID/wallet/balance"
```

Expected: `200 OK` with a balance of `70.0000`.

## Get paginated transaction history

```sh
curl -i "$BASE_URL/players/$PLAYER_ID/wallet/history?page=0&size=2"
curl -i "$BASE_URL/players/$PLAYER_ID/wallet/history?page=1&size=2"
```

Expected: `200 OK`. History is ordered newest first. The complete history contains `CREATE`, `CREDIT`, and `DEBIT`; the rejected overdraft is absent.

## Inspect the database

```sh
docker compose exec postgres psql -U wallet_ledger -d wallet_ledger -c \
  "SELECT player_id, balance, currency FROM wallets WHERE player_id = '$PLAYER_ID';"

docker compose exec postgres psql -U wallet_ledger -d wallet_ledger -c \
  "SELECT operation_type, amount, balance_after, idempotency_key, created_at FROM wallet_operations WHERE wallet_id = (SELECT id FROM wallets WHERE player_id = '$PLAYER_ID') ORDER BY created_at DESC, id DESC;"
```

The database operation log is the source of record for wallet creation, credits, and successful debits.