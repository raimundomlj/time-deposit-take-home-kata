# Working on the Kotlin solution

- Read the [assignment](../README.md), the relevant code and its tests before editing. Make one focused change at a time. Inspect unfamiliar scripts before running them.
- Preserve the shared `TimeDeposit` class and the signature and behavior of `updateBalance`. Keep the original `Double` calculation and round only the interest with `BigDecimal(interest).setScale(2, RoundingMode.HALF_UP)`.
- Edit [openapi.yaml](openapi.yaml) first when changing the API. Keep exactly two operations: GET lists deposits with their withdrawals, and a bodyless POST applies one round of interest to existing deposits in a single transaction.
- Keep interest rules in the calculator, transaction handling in the update use case, HTTP handling in the REST adapter and SQL in the JDBC adapter.
- Run tests from this directory. Use `./mvnw test` for the calculator's characterization tests and `./mvnw clean verify` for the full suite with Docker running. Report failures and any checks you could not run.
- Keep credentials and private contact details out of the repository. Each commit, branch, fork, push or pull request requires explicit user authorization.
