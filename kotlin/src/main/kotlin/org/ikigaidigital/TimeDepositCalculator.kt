package org.ikigaidigital

import java.math.BigDecimal
import java.math.RoundingMode

class TimeDepositCalculator {
    fun updateBalance(xs: List<TimeDeposit>) {
        for (deposit in xs) {
            creditMonthlyInterest(deposit)
        }
    }

    // Updates the deposit balance and returns the rounded interest.
    // updateBalance only needs the updated balance. The use case also uses the returned
    // interest to add the same amount to the decimal balance in the database.
    internal fun creditMonthlyInterest(deposit: TimeDeposit): BigDecimal {
        val interest = monthlyInterest(deposit)
        deposit.balance += interest.toDouble()
        return interest
    }

    private fun monthlyInterest(deposit: TimeDeposit): BigDecimal {
        if (deposit.days <= 30) return BigDecimal.ZERO

        val annualRate = when {
            deposit.planType == "basic" -> 0.01
            deposit.planType == "student" && deposit.days <= 365 -> 0.03
            deposit.planType == "premium" && deposit.days > 45 -> 0.05
            else -> return BigDecimal.ZERO
        }

        val interest = deposit.balance * annualRate / 12
        // The legacy calculation rounds the exact binary value, including at half-cent boundaries.
        return BigDecimal(interest).setScale(2, RoundingMode.HALF_UP)
    }
}
