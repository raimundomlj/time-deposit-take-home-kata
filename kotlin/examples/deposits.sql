BEGIN;

WITH deposit AS (
    INSERT INTO "timeDeposits" ("planType", balance, days)
    VALUES ('basic', 18.00, 31)
    RETURNING id
)
INSERT INTO withdrawals ("timeDepositId", amount, date)
SELECT id, 7.50, DATE '2025-01-01' FROM deposit;

INSERT INTO "timeDeposits" ("planType", balance, days)
VALUES ('student', 1200.00, 366);

COMMIT;
