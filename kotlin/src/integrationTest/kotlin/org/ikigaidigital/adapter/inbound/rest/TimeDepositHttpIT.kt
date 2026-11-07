package org.ikigaidigital.adapter.inbound.rest

import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlMergeMode
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:tc:postgresql:17-alpine:///http_api"]
)
@Sql(statements = ["""TRUNCATE TABLE withdrawals, "timeDeposits" RESTART IDENTITY"""])
class TimeDepositHttpIT @Autowired constructor(@LocalServerPort private val port: Int, private val jdbc: JdbcTemplate) {
    private val http = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @Test
    fun `a POST with a deposit body is rejected before crediting any balance`() {
        jdbc.update("""INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES (1, 'basic', 18.00, 31)""")
        http.post().uri("/time-deposits/update-balances")
            .contentType(MediaType.APPLICATION_JSON).body("""{"planType":"basic","balance":18,"days":31}""")
            .exchange().expectStatus().isBadRequest()

        http.get().uri("/time-deposits").exchange().expectStatus().isOk().expectBody().json("""
            [{"id":1,"planType":"basic","balance":18.00,"days":31,"withdrawals":[]}]
        """.trimIndent(), JsonCompareMode.STRICT)
    }

    @Test
    fun `chunked multipart is rejected before interest is credited`() {
        jdbc.update("""INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES (1, 'basic', 18.00, 31)""")
        val multipart = "--sample\r\nContent-Disposition: form-data; name=\"amount\"\r\n\r\n18\r\n--sample--\r\n"
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/time-deposits/update-balances"))
            .version(HttpClient.Version.HTTP_1_1)
            .header("Content-Type", "multipart/form-data; boundary=sample")
            // An input stream has unknown length, so the HTTP/1.1 client uses chunked transfer.
            .POST(HttpRequest.BodyPublishers.ofInputStream { multipart.byteInputStream() })
            .build()
        val status = HttpClient.newHttpClient().use { client ->
            client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
        }

        assertAll(
            { assertEquals(400, status) },
            { http.get().uri("/time-deposits").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].balance").isEqualTo(18.00) }
        )
    }

    @Test
    fun `an empty batch returns no content and the collection is an empty JSON array`() {
        http.post().uri("/time-deposits/update-balances").accept(MediaType.APPLICATION_JSON).exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty()

        http.get().uri("/time-deposits").exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody().json("[]", JsonCompareMode.STRICT)
    }

    @Test
    fun `GET serves JSON and rejects clients that only accept problem documents`() {
        http.get().uri("/time-deposits").accept(MediaType.APPLICATION_PROBLEM_JSON).exchange()
            .expectStatus().isEqualTo(406)

        http.get().uri("/time-deposits")
            .accept(MediaType.APPLICATION_PROBLEM_JSON, MediaType.APPLICATION_JSON).exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody().json("[]", JsonCompareMode.STRICT)
    }

    @Test
    fun `GET returns a generic problem when withdrawal history cannot be read`() {
        jdbc.execute("ALTER TABLE withdrawals RENAME TO unavailable_withdrawals")
        try {
            http.get().uri("/time-deposits")
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                    {"title":"Internal Server Error","status":500,
                     "detail":"The database operation could not be completed.","instance":"/time-deposits"}
                """.trimIndent(), JsonCompareMode.STRICT)
        } finally {
            jdbc.execute("ALTER TABLE unavailable_withdrawals RENAME TO withdrawals")
        }
    }

    @Test
    fun `successive POSTs use the legacy half-cent rounding at each credit`() {
        jdbc.update("""INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES (1, 'premium', 3.59, 46)""")
        for (expected in listOf("3.60", "3.61")) {
            http.post().uri("/time-deposits/update-balances").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isNoContent()
            http.get().uri("/time-deposits").exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                    [{"id":1,"planType":"premium","balance":$expected,"days":46,"withdrawals":[]}]
                """.trimIndent(), JsonCompareMode.STRICT)
        }
    }

    @Test
    @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
    @Sql("file:examples/deposits.sql")
    fun `GET POST GET applies interest to the SQL example and preserves withdrawal history`() {
        fun expectDeposits(basicBalance: String) {
            http.get().uri("/time-deposits").exchange().expectStatus().isOk().expectBody().json("""
                [
                  {"id":1,"planType":"basic","balance":$basicBalance,"days":31,
                   "withdrawals":[{"id":1,"amount":7.50,"date":"2025-01-01"}]},
                  {"id":2,"planType":"student","balance":1200.00,"days":366,"withdrawals":[]}
                ]
            """.trimIndent(), JsonCompareMode.STRICT)
        }

        expectDeposits("18.00")
        http.post().uri("/time-deposits/update-balances").exchange()
            .expectStatus().isNoContent().expectBody().isEmpty()
        expectDeposits("18.01")
    }

    @Test
    fun `overlapping POSTs credit interest twice using successive balances`(@Autowired transaction: TransactionTemplate) {
        jdbc.update("""INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES (1, 'premium', 1200.00, 46)""")

        val posts = transaction.execute {
            // Hold the row until both HTTP requests are waiting in PostgreSQL.
            jdbc.queryForObject("""SELECT id FROM "timeDeposits" WHERE id = 1 FOR UPDATE""", Int::class.java)
            val pending = List(2) {
                CompletableFuture.runAsync {
                    http.post().uri("/time-deposits/update-balances").exchange()
                        .expectStatus().isNoContent()
                }
            }
            await("both balance updates blocked on the deposit").atMost(10, SECONDS).until {
                jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock'
                      AND query LIKE '%"timeDeposits"%'
                """.trimIndent(), Int::class.java) == 2
            }
            pending
        }!!
        posts.forEach { it.get(10, SECONDS) }

        http.get().uri("/time-deposits").exchange()
            .expectStatus().isOk()
            .expectBody().json("""
                [{"id":1,"planType":"premium","balance":1210.02,"days":46,"withdrawals":[]}]
            """.trimIndent(), JsonCompareMode.STRICT)
    }

    @Test
    fun `a rejected credit returns a generic problem and the original balance is still visible`() {
        jdbc.update("""INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES (1, 'basic', 18.00, 31)""")
        jdbc.execute("""ALTER TABLE "timeDeposits" ADD CONSTRAINT reject_credit CHECK (balance <= 18)""")
        try {
            http.post().uri("/time-deposits/update-balances").exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json("""
                    {"title":"Internal Server Error","status":500,
                     "detail":"The database operation could not be completed.","instance":"/time-deposits/update-balances"}
                """.trimIndent(), JsonCompareMode.STRICT)

            http.get().uri("/time-deposits").exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                    [{"id":1,"planType":"basic","balance":18.00,"days":31,"withdrawals":[]}]
                """.trimIndent(), JsonCompareMode.STRICT)
        } finally {
            jdbc.execute("""ALTER TABLE "timeDeposits" DROP CONSTRAINT reject_credit""")
        }
    }
}
