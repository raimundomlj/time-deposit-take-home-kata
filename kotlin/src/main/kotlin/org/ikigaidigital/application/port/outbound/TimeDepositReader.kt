package org.ikigaidigital.application.port.outbound

import java.math.BigDecimal
import java.time.LocalDate

fun interface TimeDepositReader {
    fun findAll(): List<TimeDepositDetails>
}

data class TimeDepositDetails(
    val id: Int,
    val planType: String,
    val balance: BigDecimal,
    val days: Int,
    val withdrawals: List<Withdrawal>
)

data class Withdrawal(val id: Int, val amount: BigDecimal, val date: LocalDate)
