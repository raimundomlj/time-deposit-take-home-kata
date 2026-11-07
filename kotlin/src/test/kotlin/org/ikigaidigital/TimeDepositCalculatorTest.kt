package org.ikigaidigital

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TimeDepositCalculatorTest {
    private val calculator = TimeDepositCalculator()

    @ParameterizedTest(name = "{0} on day {1} ends with balance {2}")
    @CsvSource(
        "basic,   30, 1200.00",
        "basic,   31, 1201.00",
        "student, 30, 1200.00",
        "student, 31, 1203.00",
        "student, 365, 1203.00",
        "student, 366, 1200.00",
        "premium, 30, 1200.00",
        "premium, 45, 1200.00",
        "premium, 46, 1205.00",
        "unknown, 46, 1200.00",
        "Basic,   46, 1200.00"
    )
    fun `applies monthly interest according to the plan name and deposit age`(
        planType: String, days: Int, expectedBalance: Double
    ) {
        val deposit = TimeDeposit(1, planType, 1200.00, days)

        calculator.updateBalance(listOf(deposit))

        assertThat(deposit.balance).isEqualTo(expectedBalance)
    }

    @ParameterizedTest(name = "basic balance {0} becomes {1}")
    @CsvSource(
        "150.00, 150.13",   // Interest is exactly 0.125, an exact half-cent rounds up.
        "18.00, 18.01",     // The calculated Double is just below 0.015.
        "123.456, 123.556"  // Only the interest is rounded to cents.
    )
    fun `rounds the calculated interest before adding it to the balance`(
        initialBalance: Double, expectedBalance: Double
    ) {
        val deposit = TimeDeposit(1, "basic", initialBalance, 31)

        calculator.updateBalance(listOf(deposit))

        assertThat(deposit.balance).isEqualTo(expectedBalance)
    }

    @Test
    fun `each call applies interest to the current balance of every eligible deposit`() {
        val premium = TimeDeposit(1, "premium", 1200.00, 46)
        val expiredStudent = TimeDeposit(2, "student", 1200.00, 366)
        val basic = TimeDeposit(3, "basic", 1200.00, 31)
        val deposits = listOf(premium, expiredStudent, basic)

        calculator.updateBalance(deposits)
        assertThat(deposits.map { it.balance }).containsExactly(1205.00, 1200.00, 1201.00)

        calculator.updateBalance(deposits)
        assertThat(deposits.map { it.balance }).containsExactly(1210.02, 1200.00, 1202.00)
    }

    @Test
    fun `accepts an empty list of deposits`() {
        assertDoesNotThrow { calculator.updateBalance(emptyList()) }
    }
}
