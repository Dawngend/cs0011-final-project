package com.example.medimind.util

import com.example.medimind.R
import com.example.medimind.model.*

enum class ValidationField {
    NAME,
    DOSE,
    INSTRUCTIONS,
    START_DATE,
    END_DATE,
    TIMES,
    DAYS,
    MEDICINE,
    STOCK,
    UNITS_PER_DOSE,
    LOW_STOCK_THRESHOLD,
}

data class ValidationIssue(val field: ValidationField, val messageResId: Int)

object Validator {
    fun validateMedication(
        m: Medication,
        schedules: List<Schedule>,
        isEdit: Boolean,
        today: String,
        duplicateExists: Boolean,
    ): List<ValidationIssue> = buildList {
        fun error(field: ValidationField, id: Int) {
            add(ValidationIssue(field, id))
        }
        val name = m.name.trim()
        val dose = m.dose.trim()
        if (name.isEmpty()) error(ValidationField.NAME, R.string.error_name_required)
        else if (name.length !in 2..50) error(ValidationField.NAME, R.string.error_name_length)
        if (dose.isEmpty()) error(ValidationField.DOSE, R.string.error_dose_required)
        else if (dose.length > 30) error(ValidationField.DOSE, R.string.error_dose_length)
        if ((m.instructions?.trim()?.length ?: 0) > 100)
            error(ValidationField.INSTRUCTIONS, R.string.error_instructions_length)
        val start = DateUtils.parseDate(m.startDate)
        val end = m.endDate?.takeIf { it.isNotBlank() }?.let(DateUtils::parseDate)
        val current = requireNotNull(DateUtils.parseDate(today))
        when {
            m.startDate.isBlank() ->
                error(ValidationField.START_DATE, R.string.error_start_required)
            start == null -> error(ValidationField.START_DATE, R.string.error_date_invalid)
            !isEdit && start < current ->
                error(ValidationField.START_DATE, R.string.error_start_past)
        }
        if (!m.endDate.isNullOrBlank() && end == null)
            error(ValidationField.END_DATE, R.string.error_date_invalid)
        if (start != null && end != null && end < start)
            error(ValidationField.END_DATE, R.string.error_end_before_start)
        when {
            schedules.isEmpty() -> error(ValidationField.TIMES, R.string.error_time_required)
            schedules.any { DateUtils.parseTime(it.timeOfDay) == null } ->
                error(ValidationField.TIMES, R.string.error_time_invalid)
            schedules.map { it.timeOfDay }.distinct().size != schedules.size ->
                error(ValidationField.TIMES, R.string.error_time_duplicate)
        }
        when {
            schedules.any { it.daysOfWeek.isEmpty() } ->
                error(ValidationField.DAYS, R.string.error_days_required)
            schedules.any { it.daysOfWeek.any { day -> day !in 1..7 } } ||
                schedules.map { it.daysOfWeek }.distinct().size > 1 ->
                error(ValidationField.DAYS, R.string.error_days_invalid)
        }
        if (duplicateExists) error(ValidationField.MEDICINE, R.string.error_medicine_duplicate)
        if (m.stockQuantity != null && m.stockQuantity < 0)
            error(ValidationField.STOCK, R.string.error_stock)
        if (m.unitsPerDose < 1) error(ValidationField.UNITS_PER_DOSE, R.string.error_units_per_dose)
        if (m.lowStockThreshold < 0)
            error(ValidationField.LOW_STOCK_THRESHOLD, R.string.error_low_stock_threshold)
    }
}
