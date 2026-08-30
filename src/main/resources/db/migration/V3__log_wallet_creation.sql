ALTER TABLE wallet_operations
    DROP CONSTRAINT wallet_operations_amount_check,
    DROP CONSTRAINT wallet_operations_type_check;

ALTER TABLE wallet_operations
    ADD CONSTRAINT wallet_operations_amount_check
        CHECK ((operation_type = 'CREATE' AND amount = 0)
            OR (operation_type IN ('CREDIT', 'DEBIT') AND amount > 0)),
    ADD CONSTRAINT wallet_operations_type_check
        CHECK (operation_type IN ('CREATE', 'CREDIT', 'DEBIT'));
