package org.ikigaidigital.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.ikigaidigital.application.port.outbound.TimeDepositDetails
import org.ikigaidigital.application.port.outbound.TimeDepositReader
import org.ikigaidigital.application.port.outbound.Withdrawal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest(properties = ["spring.datasource.url=jdbc:tc:postgresql:17-alpine:///time_deposits"])
@Transactional
class JdbcTimeDepositReaderIT @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val reader: TimeDepositReader
) {
    @Test
    fun `an empty database returns an empty list`() {
        assertThat(reader.findAll()).isEmpty()
    }

    @Test
    fun `loads each deposit with its own withdrawal history`() {
        jdbc.update("""
            INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES
                (3, 'premium', 9007199254740993.125, 46),
                (1, 'basic', 18.00, 31),
                (2, 'student', 105.27, 365)
        """.trimIndent())
        jdbc.update("""
            INSERT INTO withdrawals (id, "timeDepositId", amount, date) VALUES
                (10, 3, 1.25, DATE '2025-02-01'),
                (11, 2, 2.00, DATE '2025-01-05'),
                (12, 3, 0.125, DATE '2025-01-01')
        """.trimIndent())

        assertThat(reader.findAll()).containsExactly(
            TimeDepositDetails(1, "basic", "18.00".toBigDecimal(), 31, emptyList()),
            TimeDepositDetails(2, "student", "105.27".toBigDecimal(), 365, listOf(
                Withdrawal(11, "2.00".toBigDecimal(), LocalDate.parse("2025-01-05"))
            )),
            TimeDepositDetails(3, "premium", "9007199254740993.125".toBigDecimal(), 46, listOf(
                Withdrawal(12, "0.125".toBigDecimal(), LocalDate.parse("2025-01-01")),
                Withdrawal(10, "1.25".toBigDecimal(), LocalDate.parse("2025-02-01"))
            ))
        )
    }

    @Test
    fun `a withdrawal must reference an existing deposit`() {
        assertThatThrownBy {
            jdbc.update("""INSERT INTO withdrawals ("timeDepositId", amount, date) VALUES (42, 5.00, CURRENT_DATE)""")
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
