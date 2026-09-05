package com.smriti.app.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object DueDateResolver {

    // TODO: Whisper emits Devanagari for Hindi speech (e.g. सोमवार, मंगलवार) and those tokens
    // are not yet matched. Hindi/Devanagari weekday names are currently out of scope.

    private val WEEKDAY_REGEX = Regex(
        """\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun)\b"""
    )

    private val RELATIVE_DAY_REGEX = Regex(
        """\b(day after tomorrow|tomorrow|today|tonight)\b"""
    )

    private val OTHER_TEMPORAL_REGEX = Regex(
        """\b(next week|this week|next month|end of the week|end of month|january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec|\d{4}-\d{2}-\d{2}|\d{4})\b"""
    )

    fun resolve(actionText: String, transcript: String, modelDue: String?, today: LocalDate): String? {
        // 1. Build the haystack as actionText + " " + transcript, lowercased.
        val haystack = "$actionText $transcript".lowercase()

        // 2. If the haystack contains NO temporal expression at all -> return null, ALWAYS,
        // no matter what modelDue says. This is the fix for fault (b).
        val hasTemporalExpression = WEEKDAY_REGEX.containsMatchIn(haystack) ||
            RELATIVE_DAY_REGEX.containsMatchIn(haystack) ||
            OTHER_TEMPORAL_REGEX.containsMatchIn(haystack)

        if (!hasTemporalExpression) {
            return null
        }

        // 3. If a WEEKDAY name is present -> compute the date ourselves and IGNORE modelDue entirely.
        // This is the fix for fault (a). Use the NEXT occurrence strictly after today
        // (so on Saturday 2026-09-05, "friday" -> 2026-09-11, never today and never in the past).
        // If several weekdays appear, use the first one in the haystack.
        val weekdayMatch = WEEKDAY_REGEX.find(haystack)
        if (weekdayMatch != null) {
            val dayOfWeek = when (weekdayMatch.value) {
                "monday", "mon" -> DayOfWeek.MONDAY
                "tuesday", "tue", "tues" -> DayOfWeek.TUESDAY
                "wednesday", "wed" -> DayOfWeek.WEDNESDAY
                "thursday", "thu", "thur", "thurs" -> DayOfWeek.THURSDAY
                "friday", "fri" -> DayOfWeek.FRIDAY
                "saturday", "sat" -> DayOfWeek.SATURDAY
                "sunday", "sun" -> DayOfWeek.SUNDAY
                else -> null
            }
            if (dayOfWeek != null) {
                val resolvedDate = today.with(TemporalAdjusters.next(dayOfWeek))
                return if (isOutOfRange(resolvedDate, today)) null else resolvedDate.toString()
            }
        }

        // 4. "today" -> today. "tonight" -> today. "tomorrow" -> today.plusDays(1).
        // "day after tomorrow" -> today.plusDays(2). These also override modelDue.
        val relativeDayMatch = RELATIVE_DAY_REGEX.find(haystack)
        if (relativeDayMatch != null) {
            val resolvedDate = when (relativeDayMatch.value) {
                "today", "tonight" -> today
                "tomorrow" -> today.plusDays(1)
                "day after tomorrow" -> today.plusDays(2)
                else -> null
            }
            if (resolvedDate != null) {
                return if (isOutOfRange(resolvedDate, today)) null else resolvedDate.toString()
            }
        }

        // 5. Otherwise a temporal expression exists but is not one we resolve ourselves (a month name,
        // an explicit date). Accept modelDue ONLY if it parses as a strict ISO-8601 LocalDate.
        // Anything unparseable -> null.
        if (OTHER_TEMPORAL_REGEX.containsMatchIn(haystack)) {
            if (modelDue.isNullOrBlank()) return null
            val parsedDate = try {
                LocalDate.parse(modelDue.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: Exception) {
                null
            } ?: return null
            return if (isOutOfRange(parsedDate, today)) null else parsedDate.toString()
        }

        return null
    }

    // 6. Never return a date more than 2 years from today in either direction - a 4B model
    // occasionally emits years like 2014 or 2077. Out of range -> null.
    private fun isOutOfRange(date: LocalDate, today: LocalDate): Boolean {
        val minDate = today.minusYears(2)
        val maxDate = today.plusYears(2)
        return date.isBefore(minDate) || date.isAfter(maxDate)
    }
}
