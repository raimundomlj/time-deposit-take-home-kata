package org.ikigaidigital.adapter.inbound.rest

import jakarta.servlet.http.HttpServletRequest
import org.ikigaidigital.application.usecase.ListTimeDeposits
import org.ikigaidigital.application.usecase.UpdateTimeDepositBalances
import org.ikigaidigital.application.port.outbound.TimeDepositDetails
import org.ikigaidigital.contract.api.TimeDepositsApi
import org.ikigaidigital.contract.model.TimeDeposit
import org.ikigaidigital.contract.model.Withdrawal
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class TimeDepositController(
    private val listDeposits: ListTimeDeposits,
    private val updateBalances: UpdateTimeDepositBalances,
    private val request: HttpServletRequest
) : TimeDepositsApi {
    override fun listTimeDeposits(): List<TimeDeposit> = listDeposits.execute().map { it.toResponse() }

    override fun updateTimeDepositBalances() {
        // Reading the stream also detects chunked request bodies with an unknown content length.
        if (request.contentLengthLong > 0 || request.inputStream.read() != -1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Send the balance update without a request body.")
        }
        updateBalances.execute()
    }
}

private fun TimeDepositDetails.toResponse() = TimeDeposit(
    id = id,
    planType = planType,
    balance = balance,
    days = days,
    withdrawals = withdrawals.map { Withdrawal(it.id, it.amount, it.date) }
)
