package org.ikigaidigital.adapter.inbound.rest

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:tc:postgresql:17-alpine:///balance_migration", "spring.flyway.target=1"]
)
class BalanceRangeMigrationIT @Autowired constructor(
    @LocalServerPort port: Int, private val jdbc: JdbcTemplate, private val flyway: Flyway
) {
    private val http = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @Test
    fun `V2 rejects new oversized balances and reports a problem for existing ones until corrected`() {
        val insert = """INSERT INTO "timeDeposits" ("planType", balance, days) VALUES ('premium', 1e310, 46)"""
        jdbc.update(insert)
        Flyway.configure().configuration(flyway.configuration).target("2").load().migrate()

        assertThatThrownBy { jdbc.update(insert) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining("deposit_balance_in_calculator_range")
        http.post().uri("/time-deposits/update-balances").exchange()
            .expectStatus().isEqualTo(500)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.status").isEqualTo(500)

        jdbc.update("""UPDATE "timeDeposits" SET balance = 3.59""")
        http.post().uri("/time-deposits/update-balances").exchange().expectStatus().isNoContent()
    }
}
