package org.ikigaidigital.application.usecase

import org.ikigaidigital.application.port.outbound.TimeDepositDetails
import org.ikigaidigital.application.port.outbound.TimeDepositReader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListTimeDeposits(private val reader: TimeDepositReader) {
    @Transactional(readOnly = true)
    fun execute(): List<TimeDepositDetails> = reader.findAll()
}
