package com.example.medimind.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateUtils {
    @Volatile internal var clockOverride: Clock? = null
    private val dateFormat =
        DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
    private val timestampFormat =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)

    fun now(): LocalDateTime =
        LocalDateTime.now(clockOverride ?: Clock.systemDefaultZone())
            .truncatedTo(ChronoUnit.MINUTES)

    fun formatDate(value: LocalDate): String = value.format(dateFormat)

    fun formatTime(value: LocalTime): String = value.format(timeFormat)

    fun formatTimestamp(value: LocalDateTime): String = value.format(timestampFormat)

    fun parseDate(value: String): LocalDate? = parse {
        LocalDate.parse(value, dateFormat).takeIf { formatDate(it) == value }
    }

    fun parseTime(value: String): LocalTime? = parse {
        LocalTime.parse(value, timeFormat).takeIf { formatTime(it) == value }
    }

    fun parseTimestamp(value: String): LocalDateTime? = parse {
        LocalDateTime.parse(value, timestampFormat).takeIf { formatTimestamp(it) == value }
    }

    fun dayOfWeek(date: String): Int = requireNotNull(parseDate(date)).dayOfWeek.value

    fun periodStart(today: String, days: Int): String {
        require(days == 7 || days == 30)
        return formatDate(requireNotNull(parseDate(today)).minusDays(days - 1L))
    }

    private inline fun <T> parse(block: () -> T?): T? =
        try {
            block()
        } catch (_: DateTimeParseException) {
            null
        }
}
