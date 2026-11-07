package org.ikigaidigital.adapter.outbound.persistence

import org.ikigaidigital.TimeDeposit
import org.ikigaidigital.application.port.outbound.TimeDepositBalances
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class JdbcTimeDepositBalances(private val jdbc: JdbcTemplate) : TimeDepositBalances {
    override fun lockAll(): List<TimeDeposit> = jdbc.query(
        """SELECT id, "planType", balance, "calculatorBalance", days
           FROM "timeDeposits" ORDER BY id FOR UPDATE"""
    ) { row, _ ->
        // Reuse the previous Double result to preserve legacy rounding across updates.
        // Restarting from the decimal balance can change interest at half-cent boundaries.
        val previous = row.getDouble("calculatorBalance")
        val principal = if (row.wasNull()) row.getDouble("balance") else previous
        if (!principal.isFinite()) throw DataIntegrityViolationException("Deposit balance must be finite")
        TimeDeposit(row.getInt("id"), row.getString("planType"), principal, row.getInt("days"))
    }

    override fun creditInterest(depositId: Int, interest: BigDecimal, calculatorBalance: Double) {
        jdbc.update(
            """UPDATE "timeDeposits" SET balance = balance + ?, "calculatorBalance" = ? WHERE id = ?""",
            interest, calculatorBalance, depositId
        )
    }
}
