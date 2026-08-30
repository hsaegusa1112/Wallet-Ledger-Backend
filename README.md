# Wallet Ledger Backend

A Java 17, Spring Boot 3.5 application with PostgreSQL, ready to run entirely in Docker.

## Start with Docker

```sh
cp .env.example .env
docker compose up --build
```

The API is available at `http://localhost:8080`. Verify the application and database connection at:

```sh
curl http://localhost:8080/actuator/health
```

Stop the stack with `docker compose down`. To also remove the PostgreSQL data volume, run `docker compose down -v`.

PostgreSQL is available on `localhost:5432` with the credentials in `.env`. To access it directly:

```sh
docker compose exec postgres psql -U wallet_ledger -d wallet_ledger
```

## API

| Operation | Endpoint | Notes |
| --- | --- | --- |
| Create wallet | `POST /players/{playerId}/wallet` | Creates a wallet and records a `CREATE` operation. |
| Credit wallet | `POST /players/{playerId}/wallet/credits` | Requires an `Idempotency-Key` header. |
| Debit wallet | `POST /players/{playerId}/wallet/debits` | Returns `422` when funds are insufficient. |
| Get balance | `GET /players/{playerId}/wallet/balance` | Returns the current balance. |
| Get history | `GET /players/{playerId}/wallet/history?page=0&size=20` | Returns newest-first, paginated operations. |

Credit and debit requests require a positive amount with up to four decimal places and a reason:

```json
{
	"amount": 25.0000,
	"reason": "mission reward"
}
```

Example credit request:

```sh
curl -X POST http://localhost:8080/players/player-1/wallet/credits \
	-H 'Content-Type: application/json' \
	-H 'Idempotency-Key: reward-001' \
	-d '{"amount":25.0000,"reason":"mission reward"}'
```

## Tests

The integration tests target the running Docker Compose application, including its PostgreSQL database and row locks. Start the stack first, then run:

```sh
docker compose up --build -d
mvn test
```

The suite verifies concurrent credits, concurrent debits that never overdraw a wallet, and concurrent replays of one idempotency key. Set `APP_BASE_URL` to target a non-default application address.

## Design decisions

The service uses a balance snapshot and an immutable operation ledger. The `wallets` table stores each player's current balance for efficient reads. The `wallet_operations` table permanently records each wallet creation, credit, and debit with its operation type, amount, balance after the operation, idempotency key, reason, and timestamp.

Amounts use PostgreSQL `NUMERIC(19,4)` and Java `BigDecimal`, avoiding floating-point arithmetic. Flyway owns schema changes, while Hibernate is configured only to validate mappings. The balance is intentionally denormalized from the ledger for read performance; a production deployment should add a reconciliation process that periodically derives balances from the operation history.

## Concurrency and idempotency

Credit and debit are performed in one database transaction. The service acquires a PostgreSQL pessimistic write lock on the wallet row before it reads or updates the balance. Requests for the same wallet are therefore serialized, preventing lost updates and concurrent debits from exceeding the available balance.

Each money-moving request requires an `Idempotency-Key`. A unique database constraint on `(wallet_id, idempotency_key)` protects against duplicate operations. Repeating the same operation type, amount, and reason returns the original recorded operation. Reusing a key with changed request data returns `409 Conflict`. An insufficient-funds debit returns `422 Unprocessable Entity`; because it occurs inside the transaction before persistence, neither the balance nor operation log is changed.

## Testing approach

Tests call the running Docker Compose application over HTTP and therefore exercise the real Spring service, PostgreSQL transactions, locks, and Flyway schema. They use unique player IDs to avoid collisions with existing development data.

The suite covers concurrent credits, concurrent debits from a limited balance, and concurrent retries sharing one idempotency key. It also verifies normal credit/debit behavior, reason persistence, insufficient-funds rejection, idempotency-key conflicts, amount-scale validation, and pagination validation.

## Database migrations

Flyway applies database migrations automatically before Hibernate validates entity mappings. Migration scripts are in `src/main/resources/db/migration` and must be named `V<version>__<description>.sql`, for example `V2__create_ledger_entries.sql`.

The initial migration, `V1__create_wallets.sql`, creates the `wallets` table. Flyway records each applied migration in `flyway_schema_history`; never modify a migration after it has been applied to a shared database. Add a new versioned migration instead. Every wallet lifecycle action, including creation, is recorded in `wallet_operations`.

## Assumptions and limitations

This service is assumed to run as a backend worker behind an authenticated API gateway or upstream service. Authentication and authorization are therefore outside this service's scope; the caller is responsible for ensuring it is authorized to operate on the supplied player ID.

- Each player has one wallet in one currency; transfers and currency conversion are not implemented.
- Refunds, holds, bulk rewards, and reconciliation are outside the current scope.
- Tests require Docker Compose to be running and write uniquely named test wallets to the local development database. A production CI pipeline should use an isolated database per test run.
- The service does not yet provide OpenAPI documentation, metrics, structured logs, or a balance-reconciliation endpoint.