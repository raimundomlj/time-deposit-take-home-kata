# XA Bank time deposits

Kotlin and Spring Boot implementation of the [Ikigai Digital take-home](../README.md). The service lists deposits with their withdrawal history and applies one month's interest to all eligible deposits in a single transaction. The [OpenAPI contract](openapi.yaml) defines both operations.

From the repository root, run `cd kotlin` once. Run all commands below from that directory.

## Verify the solution

Run the two suites with Docker Compose. Both commands work with the application stack stopped.

```sh
docker compose run --rm --build unit-tests
docker compose run --rm --build integration-tests
```

The unit suite runs in the Maven container. The integration suite uses the host Docker socket to start disposable PostgreSQL instances through Testcontainers. It applies the Flyway migration to fresh databases and starts a real HTTP server for the endpoint tests.

Alternatively, with JDK 21 and Docker running, use the Maven Wrapper for a clean build and both suites:

```sh
./mvnw clean verify
```

The wrapper downloads Maven 3.9.11 on first use. On macOS and Linux, install `unzip` for the checksum-verified ZIP download. On Windows, use `mvnw.cmd clean verify`. From the Kotlin module, use `./mvnw test` for unit tests or `./mvnw verify -DskipUnitTests=true` for integration tests only. Surefire and Failsafe write their results to `target/surefire-reports` and `target/failsafe-reports`, respectively.

| Suite | Cases | What it verifies |
| --- | ---: | --- |
| [Calculator](src/test/kotlin/org/ikigaidigital/TimeDepositCalculatorTest.kt) | 16 | Eligibility at days 30/31, 45/46 and 365/366, case-sensitive and unknown plan names, half-cent rounding, a fractional-cent principal, successive updates and an empty list. |
| [PostgreSQL reader](src/integrationTest/kotlin/org/ikigaidigital/adapter/outbound/persistence/JdbcTimeDepositReaderIT.kt) | 3 | Empty database, exact decimals and deposits with zero, one or multiple withdrawals, plus rejection of a withdrawal with a missing deposit. |
| [Application use cases](src/integrationTest/kotlin/org/ikigaidigital/application/usecase/TimeDepositUseCasesIT.kt) | 3 | Empty batch, successive commits preserving withdrawal history, and rollback of the first credit when the second write fails. |
| [HTTP API](src/integrationTest/kotlin/org/ikigaidigital/adapter/inbound/rest/TimeDepositHttpIT.kt) | 9 | GET → POST → GET with the sample SQL, rejection of JSON and chunked multipart bodies before crediting interest, rollback, empty batch, concurrent credits, successive half-cent rounding, content negotiation and failed queries. |
| [Balance migration](src/integrationTest/kotlin/org/ikigaidigital/adapter/inbound/rest/BalanceRangeMigrationIT.kt) | 1 | Upgrade from V1 with an oversized balance, rejection of new out-of-range writes, a problem response for the existing row and recovery after correction. |

In IntelliJ IDEA, open [pom.xml](pom.xml) as a Maven project and select JDK 21. To regenerate the contract classes, run the following command in the terminal from the Kotlin directory. It clears the previous build output and generates fresh classes from the current OpenAPI contract.

```sh
./mvnw clean generate-sources
```

In the Maven tool window, select **Reload All Maven Projects** so IntelliJ picks up the generated classes. Next to the green Run button, select **Unit tests** or **Integration tests** and press Run to see individual JUnit results. Start Docker before running the integration tests.

## Run locally

Both the Maven Wrapper and Docker Compose serve Swagger UI at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

### With the Maven Wrapper

With JDK 21 and Docker running, start PostgreSQL, then launch the API with its built-in Swagger UI:

```sh
docker compose up -d --wait db
./mvnw spring-boot:run
```

Keep this terminal open while using Swagger UI. Spring prints each request's method and path, followed by its HTTP status (for example, `GET "/time-deposits"` and `Completed 200 OK`). If you launch the Maven goal from IntelliJ's Maven window, the same output appears in the **Run** console.

Press Ctrl+C to stop the Maven application. If switching from the full Compose stack, run `docker compose stop api` first to free port 8080.

### With Docker Compose

Build the API and start it alongside PostgreSQL:

```sh
docker compose up -d --build
docker compose logs -f api
```

Press Ctrl+C to leave the log viewer.

### Try the API

Wait for the application startup message. The API listens on localhost port 8080. Flyway initializes the tables on startup. For local development, PostgreSQL accepts passwordless connections on the Compose network and publishes port 55432 on the host loopback interface. Both startup options store demo data in the `local-deposits` volume. If you used the earlier password-based setup, its data stays in the separate `deposits` volume.

### Exercise both operations

After Flyway initializes the database, load the [sample deposits](examples/deposits.sql) once from the Kotlin directory. This works with either startup option. Each run of the script inserts two deposits.

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U postgres -d time_deposits < examples/deposits.sql
```

In [Swagger UI](http://localhost:8080/swagger-ui.html), use **Try it out → Execute** for each operation:

1. **GET /time-deposits** returns `200 OK`. The sample Basic deposit has a balance of `18.00` on day 31 and one withdrawal of `7.50`. The Student deposit has a balance of `1200.00` on day 366.
2. **POST /time-deposits/update-balances** takes no body and returns `204 No Content`. Each call applies one month of interest to all eligible deposits in a single transaction.
3. **GET /time-deposits** shows the same deposits and withdrawal history. The Basic balance is now `18.01`. The Student balance is still `1200.00` because it stopped earning interest after day 365.

Stop the stack with `docker compose down`. To discard the demo database and test dependency cache and start from scratch, use `docker compose down --volumes`.

## Calculation and storage

| Stored plan name | Interest-bearing days | Annual rate |
| --- | --- | --- |
| `basic` | 31 onwards | 1% |
| `student` | 31 through 365 inclusive | 3% |
| `premium` | 46 onwards | 5% |

The shared calculator matches plan names case-sensitively. Other names earn zero. Each call computes `balance × annual rate / 12` in the original `Double` order, then rounds the interest to two decimal places using `BigDecimal(interest)` and `HALF_UP`. For Basic on day 31, a balance of `18.00` produces a binary value just below `0.015`, so the credit is `0.01`.

The two balances reconcile decimal storage with the legacy calculation. PostgreSQL stores `balance`, returned by GET, as `NUMERIC` and adds the rounded interest to it exactly. `calculatorBalance` preserves the resulting `Double` for the next calculation, including its effect on half-cent rounding. Both values are updated in the same transaction. For a Premium deposit of `3.59` on day 46, the first booked balance is `3.60` and the calculator carries `3.5999999999999996`. The next credit is `0.01`, bringing the booked balance to `3.61`.

The update use case calls `creditMonthlyInterest` because it needs both the rounded interest to credit in PostgreSQL and the resulting calculator balance. The original `updateBalance` method delegates to the same operation, preserving its signature and behavior. This avoids duplicating the interest rules.

Flyway V2 starts each existing deposit from its booked balance. It rejects new balances outside the supported finite range. If a V1 row already exceeds that range, the update rolls back and returns a `500 application/problem+json` response until the value is corrected. For a direct SQL correction of the booked balance, set `"calculatorBalance" = NULL` in the same update to restart the calculation from the corrected value.

Withdrawals represent historical transactions and are returned as recorded. The listing sorts deposits by ID and withdrawals by date, then ID. A successful GET serves `application/json`; a client accepting only `application/problem+json` receives `406`. The POST returns an empty `204` on success.

## Changing the HTTP contract

Edit [openapi.yaml](openapi.yaml) first. Maven validates it and generates the Kotlin Spring interface and wire models under `target/generated-sources/openapi` during `generate-sources`. The REST controller implements that interface. A changed operation signature requires the implementation to compile against the new contract. The same YAML drives Swagger UI.

## Code layout

The [inbound REST adapter](src/main/kotlin/org/ikigaidigital/adapter/inbound/rest) maps between the generated HTTP types and the [application use cases](src/main/kotlin/org/ikigaidigital/application/usecase). Listing coordinates the deposit query. Updating owns the transaction and invokes the shared [calculator](src/main/kotlin/org/ikigaidigital/TimeDepositCalculator.kt), which contains the rates, eligibility and rounding rules.

The use cases depend on [outbound ports](src/main/kotlin/org/ikigaidigital/application/port/outbound). The [JDBC adapter](src/main/kotlin/org/ikigaidigital/adapter/outbound/persistence) implements these ports. It locks rows in ID order before calculating credits. The [integration tests](src/integrationTest/kotlin/org/ikigaidigital) cover rollback after a failed credit and overlapping POSTs against the same deposit.

## AI-assisted development

I started by defining characterization tests with Codex to capture when each plan earns interest and how the original calculator rounds half-cent amounts. I ran those tests against the original implementation, then used the same tests to check that the refactor preserved its behavior.

When I reviewed the initial implementation, I chose an API-first approach for both endpoints. The OpenAPI contract now defines the operations the controller implements. I also separated database error handling from the controller so both endpoints report database failures consistently.

I kept the POST bodyless because the assignment asks it to update existing deposits. For the Swagger walkthrough, I chose to seed the database with SQL so a reviewer can use GET, POST and GET again to see exactly which balances changed. I also separated the unit and integration suites so the calculator tests can run without Docker. For concurrency, I added a PostgreSQL test that sends two overlapping POSTs and checks the final balance after both credits.

I used OpenAI Codex to help refactor the calculator, implement the endpoints and write tests. I worked one change at a time so I could review each diff against the assignment and check the test results. To follow the same workflow, start Codex in the Kotlin directory and use the project rules in [AGENTS.md](AGENTS.md).
