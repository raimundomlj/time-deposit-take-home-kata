package org.ikigaidigital.adapter.outbound.persistence

import org.ikigaidigital.application.port.outbound.TimeDepositDetails
import org.ikigaidigital.application.port.outbound.TimeDepositReader
import org.ikigaidigital.application.port.outbound.Withdrawal
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class JdbcTimeDepositReader(private val jdbc: JdbcTemplate) : TimeDepositReader {
    override fun findAll(): List<TimeDepositDetails> {
        val sql = """
            SELECT d.id, d."planType", d.balance, d.days,
                   w.id AS withdrawal_id, w.amount, w.date
              FROM "timeDeposits" d
              LEFT JOIN withdrawals w ON w."timeDepositId" = d.id
             ORDER BY d.id, w.date, w.id
        """.trimIndent()

        val deposits = mutableListOf<TimeDepositDetails>()
        var withdrawals = mutableListOf<Withdrawal>()
        jdbc.query(sql, RowCallbackHandler { row ->
            val depositId = row.getInt("id")
            if (depositId != deposits.lastOrNull()?.id) {
                withdrawals = mutableListOf()
                deposits += TimeDepositDetails(
                    id = depositId,
                    planType = row.getString("planType"),
                    balance = row.getBigDecimal("balance"),
                    days = row.getInt("days"),
                    withdrawals = withdrawals
                )
            }
            val withdrawalId = row.getInt("withdrawal_id")
            if (!row.wasNull()) {
                withdrawals += Withdrawal(
                    id = withdrawalId,
                    amount = row.getBigDecimal("amount"),
                    date = row.getObject("date", LocalDate::class.java)
                )
            }
        })
        return deposits
    }
}
