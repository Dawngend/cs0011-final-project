package com.example.medimind.util

import com.example.medimind.model.Schedule
import java.time.LocalDateTime

/** Shared interval checks for generated doses and future alarms. Upper bounds are exclusive. */
internal object ScheduleTiming {
    fun eligible(s: Schedule, at: LocalDateTime): Boolean =
        at.dayOfWeek.value in s.daysOfWeek &&
            DateUtils.formatTime(at.toLocalTime()) == s.timeOfDay &&
            at >= requireNotNull(DateUtils.parseTimestamp(s.effectiveFrom)) &&
            (s.effectiveUntil == null ||
                at < requireNotNull(DateUtils.parseTimestamp(s.effectiveUntil)))

    fun next(s: Schedule, after: LocalDateTime): LocalDateTime? {
        val lower = requireNotNull(DateUtils.parseTimestamp(s.effectiveFrom))
        val start = maxOf(lower.toLocalDate(), after.toLocalDate())
        val time = requireNotNull(DateUtils.parseTime(s.timeOfDay))
        for (offset in 0L..7L) {
            val candidate = start.plusDays(offset).atTime(time)
            if (candidate > after && eligible(s, candidate)) return candidate
        }
        return null
    }
}
