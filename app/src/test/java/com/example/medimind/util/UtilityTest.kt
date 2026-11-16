package com.example.medimind.util

import com.example.medimind.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class UtilityTest {
    @Test
    fun strictDatesAndTimesRejectImpossibleOrNonCanonicalValues() {
        assertNull(DateUtils.parseDate("2026-02-29"))
        assertNotNull(DateUtils.parseDate("2028-02-29"))
        assertNull(DateUtils.parseDate("2026-1-02"))
        assertNull(DateUtils.parseTime("24:00"))
        assertNull(DateUtils.parseTime("8:00"))
        assertEquals(
            "2026-11-16 08:05",
            DateUtils.formatTimestamp(requireNotNull(DateUtils.parseTimestamp("2026-11-16 08:05"))),
        )
    }

    @Test
    fun weekAndReportBoundariesMatchTheContract() {
        assertEquals(1, DateUtils.dayOfWeek("2026-11-16"))
        assertEquals(7, DateUtils.dayOfWeek("2026-11-22"))
        assertEquals("2025-12-27", DateUtils.periodStart("2026-01-02", 7))
        assertEquals("2025-12-04", DateUtils.periodStart("2026-01-02", 30))
    }

    @Test
    fun currentMedicineDatesAndWeekdaysDefineAlarmEligibility() {
        val m =
            Medication(
                name = "Demo",
                dose = "500 mg",
                startDate = "2027-02-01",
                endDate = "2027-02-01",
            )
        val schedule = Schedule(timeOfDay = "08:00", daysOfWeek = setOf(1))
        val at = LocalDateTime.parse("2027-02-01T08:00")
        assertEquals(at, ScheduleTiming.next(schedule, m, LocalDateTime.parse("2026-11-16T08:00")))
        assertTrue(ScheduleTiming.eligible(schedule, m, at))
        assertFalse(ScheduleTiming.eligible(schedule, m.copy(isActive = false), at))
        assertNull(ScheduleTiming.next(schedule, m, at))
        assertFalse(ScheduleTiming.eligible(schedule, m.copy(startDate = "2027-02-02"), at))
    }

    @Test
    fun validatorReportsInputsWithoutDoingDatabaseWork() {
        val m =
            Medication(
                name = " ",
                dose = " ",
                startDate = "2026-11-15",
                stockQuantity = -1,
                unitsPerDose = 0,
            )
        val errors =
            Validator.validateMedication(m, emptyList(), false, "2026-11-16", true)
                .map { it.field }
                .toSet()
        assertTrue(
            errors.containsAll(
                setOf(
                    ValidationField.NAME,
                    ValidationField.DOSE,
                    ValidationField.START_DATE,
                    ValidationField.TIMES,
                    ValidationField.MEDICINE,
                    ValidationField.STOCK,
                    ValidationField.UNITS_PER_DOSE,
                ),
            ),
        )
    }

    @Test
    fun editingPastStartIsValidButMixedWeekdaysAndDuplicateTimesAreNot() {
        val m = Medication(name = "Demo", dose = "500 mg", startDate = "2026-11-01")
        val schedules = listOf(Schedule(timeOfDay = "08:00", daysOfWeek = setOf(1)))
        assertTrue(Validator.validateMedication(m, schedules, true, "2026-11-16", false).isEmpty())
        val issues =
            Validator.validateMedication(
                m,
                schedules + Schedule(timeOfDay = "08:00", daysOfWeek = setOf(2)),
                true,
                "2026-11-16",
                false,
            )
        assertTrue(issues.any { it.field == ValidationField.TIMES })
        assertTrue(issues.any { it.field == ValidationField.DAYS })
    }
}
