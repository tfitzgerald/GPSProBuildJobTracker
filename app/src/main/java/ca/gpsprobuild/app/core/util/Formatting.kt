package ca.gpsprobuild.app.core.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.CANADA)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.CANADA)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.CANADA)
    private val timeOfDay = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA)

    fun format(date: LocalDate): String = date.format(dayMonthYear)
    fun formatShort(date: LocalDate): String = date.format(shortDate)
    fun formatWithDay(date: LocalDate): String = date.format(dayMonth)
    fun formatTime(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault()).format(timeOfDay)

    fun formatDateTime(instant: Instant): String {
        val zoned = instant.atZone(ZoneId.systemDefault())
        return "${zoned.toLocalDate().format(dayMonth)} at ${zoned.format(timeOfDay)}"
    }

    /** "Today", "Yesterday", "3 days ago", then falls back to a date. */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days == -1L -> "Tomorrow"
            days in 2..6 -> "$days days ago"
            days in -6..-2 -> "In ${-days} days"
            else -> format(date)
        }
    }

    fun relative(instant: Instant): String {
        val elapsed = Duration.between(instant, Instant.now())
        return when {
            elapsed.toMinutes() < 1 -> "Just now"
            elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()} min ago"
            elapsed.toHours() < 24 -> "${elapsed.toHours()} hr ago"
            else -> relative(instant.atZone(ZoneId.systemDefault()).toLocalDate())
        }
    }

    fun toLocalDate(instant: Instant): LocalDate =
        instant.atZone(ZoneId.systemDefault()).toLocalDate()
}

object Hours {
    /** 7.5 -> "7h 30m". Contractors think in hours and minutes, not decimals. */
    fun format(hours: Double): String {
        val whole = hours.toInt()
        val minutes = Math.round((hours - whole) * 60).toInt()
        return when {
            whole == 0 && minutes == 0 -> "0h"
            whole == 0 -> "${minutes}m"
            minutes == 0 -> "${whole}h"
            else -> "${whole}h ${minutes}m"
        }
    }
}

object Phones {
    /** Formats 10-digit North American numbers, leaves anything else alone. */
    fun format(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.length == 10 ->
                "(${digits.take(3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
            digits.length == 11 && digits.startsWith("1") ->
                "(${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            else -> raw
        }
    }

    fun dialable(raw: String?): String = raw?.filter { it.isDigit() || it == '+' } ?: ""
}

object PostalCodes {
    /** Canadian postal codes, normalised to "A1A 1A1". */
    fun format(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = raw.filter { it.isLetterOrDigit() }.uppercase()
        return if (cleaned.length == 6) "${cleaned.take(3)} ${cleaned.substring(3)}" else cleaned
    }

    fun isValid(raw: String?): Boolean {
        val cleaned = raw?.filter { it.isLetterOrDigit() }?.uppercase() ?: return false
        return cleaned.matches(Regex("^[A-Z]\\d[A-Z]\\d[A-Z]\\d$"))
    }
}

object Initials {
    fun of(name: String): String = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
}
