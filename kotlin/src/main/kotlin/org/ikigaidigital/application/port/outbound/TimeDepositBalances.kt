package org.ikigaidigital.application.port.outbound

import org.ikigaidigital.TimeDeposit
import java.math.BigDecimal

interface TimeDepositBalances {
    fun lockAll(): List<TimeDeposit>
    fun creditInterest(depositId: Int, interest: BigDecimal, calculatorBalance: Double)
}
