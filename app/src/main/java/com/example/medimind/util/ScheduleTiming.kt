package com.example.medimind.util

import com.example.medimind.model.Medication
import com.example.medimind.model.Schedule
import java.time.LocalDateTime

/** Proposal eligibility uses the medicine's current dates/active state and current schedules. */
internal object ScheduleTiming {
    fun eligible(s: Schedule, m: Medication, at: LocalDateTime): Boolean =
        m.isActive &&
            at.dayOfWeek.value in s.daysOfWeek &&
            DateUtils.formatTime(at.toLocalTime()) == s.timeOfDay &&
            at.toLocalDate() >= requireNotNull(DateUtils.parseDate(m.startDate)) &&
            (m.endDate == null ||
                at.toLocalDate() <= requireNotNull(DateUtils.parseDate(m.endDate)))

    fun next(s: Schedule, m: Medication, after: LocalDateTime): LocalDateTime? {
        if (!m.isActive) return null
        val start = maxOf(requireNotNull(DateUtils.parseDate(m.startDate)), after.toLocalDate())
        val time = requireNotNull(DateUtils.parseTime(s.timeOfDay))
        for (offset in 0L..7L) {
            val candidate = start.plusDays(offset).atTime(time)
            if (candidate > after && eligible(s, m, candidate)) return candidate
        }
        return null
    }

    /** Sorted times that are already due today, for a medicine that starts today on today's weekday. */
    fun passedToday(
        times: List<String>,
        days: Set<Int>,
        startDate: String,
        now: LocalDateTime,
    ): List<String> {
        if (startDate != DateUtils.formatDate(now.toLocalDate()) || now.dayOfWeek.value !in days)
            return emptyList()
        val current = DateUtils.formatTime(now.toLocalTime())
        return times.filter { it <= current }.sorted()
    }
}
