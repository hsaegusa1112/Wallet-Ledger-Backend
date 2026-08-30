ALTER TABLE wallet_operations
    ADD COLUMN reason VARCHAR(255) NOT NULL DEFAULT 'wallet created';

ALTER TABLE wallet_operations
    ALTER COLUMN reason DROP DEFAULT;
