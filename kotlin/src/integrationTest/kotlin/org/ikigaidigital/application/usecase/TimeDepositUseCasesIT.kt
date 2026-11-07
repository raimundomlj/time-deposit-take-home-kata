package org.ikigaidigital.application.usecase

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.ikigaidigital.application.port.outbound.Withdrawal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate

@SpringBootTest(properties = ["spring.datasource.url=jdbc:tc:postgresql:17-alpine:///balance_use_cases"])
@Sql(statements = ["""TRUNCATE TABLE withdrawals, "timeDeposits" """])
class TimeDepositUseCasesIT @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val listDeposits: ListTimeDeposits,
    private val updateBalances: UpdateTimeDepositBalances
) {
    @Test
    fun `updating an empty batch succeeds`() {
        updateBalances.execute()

        assertThat(listDeposits.execute()).isEmpty()
    }

    @Test
    fun `each update credits interest on the committed balance and preserves withdrawal history`() {
        jdbc.update("""
            INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES
                (1, 'premium', 1200.0000000000000001, 46),
                (2, 'basic', 18.00, 31),
                (3, 'student', 1200.00, 366)
        """.trimIndent())
        jdbc.update("""INSERT INTO withdrawals (id, "timeDepositId", amount, date) VALUES (5, 1, 7.50, DATE '2025-01-01')""")

        updateBalances.execute()
        val first = listDeposits.execute()
        assertThat(first.map { it.balance }).containsExactly(
            "1205.0000000000000001".toBigDecimal(), "18.01".toBigDecimal(), "1200.00".toBigDecimal()
        )
        assertThat(first.first().withdrawals).containsExactly(
            Withdrawal(5, "7.50".toBigDecimal(), LocalDate.parse("2025-01-01"))
        )

        updateBalances.execute()
        assertThat(listDeposits.execute()).containsExactly(
            first[0].copy(balance = "1210.0200000000000001".toBigDecimal()),
            first[1].copy(balance = "18.03".toBigDecimal()),
            first[2]
        )
    }

    @Test
    fun `failure on the second credit rolls back the first credit`() {
        jdbc.update("""
            INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES
                (1, 'basic', 1200.00, 31), (2, 'premium', 1200.00, 46)
        """.trimIndent())
        val before = listDeposits.execute()
        jdbc.execute("""ALTER TABLE "timeDeposits" ADD CONSTRAINT reject_second_credit CHECK (id <> 2 OR balance <= 1200)""")
        try {
            assertThatThrownBy { updateBalances.execute() }
                .isInstanceOf(DataIntegrityViolationException::class.java)
                .hasMessageContaining("reject_second_credit")

            assertThat(listDeposits.execute()).isEqualTo(before)
        } finally {
            jdbc.execute("""ALTER TABLE "timeDeposits" DROP CONSTRAINT reject_second_credit""")
        }
    }
}
