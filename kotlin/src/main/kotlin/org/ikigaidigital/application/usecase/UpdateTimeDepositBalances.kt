package org.ikigaidigital.application.usecase

import org.ikigaidigital.TimeDepositCalculator
import org.ikigaidigital.application.port.outbound.TimeDepositBalances
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateTimeDepositBalances(private val balances: TimeDepositBalances) {
    private val calculator = TimeDepositCalculator()

    @Transactional
    fun execute() {
        for (deposit in balances.lockAll()) {
            val interest = calculator.creditMonthlyInterest(deposit)
            if (interest.signum() != 0) {
                balances.creditInterest(deposit.id, interest, deposit.balance)
            }
        }
    }
}
