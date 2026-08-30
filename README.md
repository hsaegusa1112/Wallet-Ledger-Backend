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

## Tests

The integration tests target the running Docker Compose application, including its PostgreSQL database and row locks. Start the stack first, then run:

```sh
docker compose up --build -d
mvn test
```

The suite verifies concurrent credits, concurrent debits that never overdraw a wallet, and concurrent replays of one idempotency key. Set `APP_BASE_URL` to target a non-default application address.

## Database migrations

Flyway applies database migrations automatically before Hibernate validates entity mappings. Migration scripts are in `src/main/resources/db/migration` and must be named `V<version>__<description>.sql`, for example `V2__create_ledger_entries.sql`.

The initial migration, `V1__create_wallets.sql`, creates the `wallets` table. Flyway records each applied migration in `flyway_schema_history`; never modify a migration after it has been applied to a shared database. Add a new versioned migration instead. Every wallet lifecycle action, including creation, is recorded in `wallet_operations`.