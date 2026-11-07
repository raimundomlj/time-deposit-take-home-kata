-- NULL starts the legacy calculation from the exact booked balance on first use.
ALTER TABLE "timeDeposits" ADD COLUMN "calculatorBalance" DOUBLE PRECISION;

-- Enforce the calculator's range on new writes. Older invalid rows can be
-- corrected individually without preventing the service from starting.
ALTER TABLE "timeDeposits" ADD CONSTRAINT deposit_balance_in_calculator_range
    CHECK (balance BETWEEN -1.7976931348623157e308 AND 1.7976931348623157e308
       AND ("calculatorBalance" IS NULL OR
            "calculatorBalance" BETWEEN '-1.7976931348623157e308'::float8 AND '1.7976931348623157e308'::float8))
    NOT VALID;
