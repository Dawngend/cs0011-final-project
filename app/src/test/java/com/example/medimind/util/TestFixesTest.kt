package com.example.medimind.util

import com.example.medimind.model.Medication
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

/** Unit tests for the fixes made after the TC-11 to TC-29 test run. */
class TestFixesTest {
    private val medicine = Medication(name = "Demo", dose = "500 mg", startDate = "2026-11-01")

    @Test
    fun medicineIsCurrentUntilItsEndDatePasses() {
        assertTrue(medicine.isCurrentOn("2026-11-21"))
        assertTrue(medicine.copy(endDate = "2026-11-21").isCurrentOn("2026-11-21"))
        assertFalse(medicine.copy(endDate = "2026-11-20").isCurrentOn("2026-11-21"))
        assertFalse(medicine.copy(isActive = false).isCurrentOn("2026-11-21"))
    }

    @Test
    fun trackedStockTextMustBeAWholeNumberThatFitsAnInt() {
        fun fields(stock: String, units: String = "1", threshold: String = "5") =
            Validator.validateStockText(true, stock, units, threshold).map { it.field }

        assertTrue(fields("10").isEmpty())
        assertTrue(fields("0").isEmpty())
        assertEquals(listOf(ValidationField.STOCK), fields(""))
        assertEquals(listOf(ValidationField.STOCK), fields("99999999999"))
        assertEquals(listOf(ValidationField.UNITS_PER_DOSE), fields("10", units = "99999999999"))
        assertEquals(listOf(ValidationField.LOW_STOCK_THRESHOLD), fields("10", threshold = "99999999999"))
        assertTrue(fields("10", units = "", threshold = "").isEmpty())
    }

    @Test
    fun stockTextIsIgnoredWhileTrackingIsOff() {
        assertTrue(Validator.validateStockText(false, "", "", "").isEmpty())
    }

    @Test
    fun refillMustBePositiveAndStayInsideTheIntRange() {
        assertTrue(Validator.isValidRefill(0, 1))
        assertFalse(Validator.isValidRefill(0, 0))
        assertFalse(Validator.isValidRefill(0, -3))
        assertTrue(Validator.isValidRefill(Int.MAX_VALUE - 5, 5))
        assertFalse(Validator.isValidRefill(Int.MAX_VALUE - 5, 6))
        assertFalse(Validator.isValidRefill(Int.MAX_VALUE, 1))
    }

    @Test
    fun onlyDueTimesOfAMedicineStartingTodayAreReportedAsPassed() {
        val now = LocalDateTime.parse("2026-11-21T09:30") // a Saturday
        val times = listOf("12:00", "09:30", "08:00")
        assertEquals(listOf("08:00", "09:30"), ScheduleTiming.passedToday(times, setOf(6), "2026-11-21", now))
        assertTrue(ScheduleTiming.passedToday(times, setOf(1, 2), "2026-11-21", now).isEmpty())
        assertTrue(ScheduleTiming.passedToday(times, setOf(6), "2026-11-22", now).isEmpty())
        assertTrue(ScheduleTiming.passedToday(listOf("09:31"), setOf(6), "2026-11-21", now).isEmpty())
    }
}
