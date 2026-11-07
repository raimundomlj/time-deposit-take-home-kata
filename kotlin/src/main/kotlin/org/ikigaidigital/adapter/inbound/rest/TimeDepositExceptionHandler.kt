package org.ikigaidigital.adapter.inbound.rest

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.transaction.TransactionException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [TimeDepositController::class])
class TimeDepositExceptionHandler {
    private val logger = LoggerFactory.getLogger(TimeDepositExceptionHandler::class.java)

    @ExceptionHandler(DataAccessException::class, TransactionException::class)
    fun databaseError(exception: RuntimeException): ProblemDetail {
        logger.error("Time deposit database operation failed", exception)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "The database operation could not be completed."
        )
    }
}
