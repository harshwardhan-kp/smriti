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

    private val CLAUSE_DELIMITER_REGEX = Regex(
        """,|;|\s+and\s+|\s+then\s+|\s+after\s+that\s+"""
    )

    internal val STOP_WORDS = setOf(
        "the", "a", "an", "to", "of", "for", "from", "we", "i", "you", "it", "and",
        "more", "need", "needs", "by", "is", "are", "be"
    )

    internal fun hasTemporalExpression(text: String): Boolean {
        val lower = text.lowercase()
        return WEEKDAY_REGEX.containsMatchIn(lower) ||
            RELATIVE_DAY_REGEX.containsMatchIn(lower) ||
            OTHER_TEMPORAL_REGEX.containsMatchIn(lower)
    }

    internal fun extractWords(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.isNotEmpty() && it !in STOP_WORDS }
            .toSet()
    }

    internal fun splitClauses(transcript: String): List<String> {
        return transcript.lowercase()
            .split(CLAUSE_DELIMITER_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Attributes a temporal expression from the action text or transcript clauses.
     * Returns the winning clause/text containing the temporal expression, or null.
     */
    internal fun attributeClause(actionText: String, transcript: String): String? {
        // 1. If the ACTION TEXT itself contains a temporal expression, resolve from that alone.
        // Done - no transcript involved.
        if (hasTemporalExpression(actionText)) {
            return actionText.lowercase()
        }

        // 2. Otherwise split the transcript into clauses on ",", ";", " and ", " then ", " after that ".
        // Keep them in order. Lowercase for matching.
        val clauses = splitClauses(transcript)
        if (clauses.isEmpty()) return null

        // 3. Find the clause(s) containing a temporal expression. If there are none -> null.
        if (clauses.none { hasTemporalExpression(it) }) {
            return null
        }

        // When transcript has only 1 clause, there are no competing clauses.
        if (clauses.size == 1) {
            return clauses[0]
        }

        // 4. Pick the clause whose words overlap the action text most (case-insensitive word-set
        // intersection, ignoring stop words: the, a, an, to, of, for, from, we, i, you, it, and,
        // more, need, needs, by, is, are, be). Require the BEST overlap to be at least 1 real word.
        val actionWords = extractWords(actionText)
        var bestOverlap = 0
        var bestClause: String? = null
        var isTie = false

        for (clause in clauses) {
            val clauseWords = extractWords(clause)
            val overlap = actionWords.intersect(clauseWords).size
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                bestClause = clause
                isTie = false
            } else if (overlap == bestOverlap && overlap > 0) {
                isTie = true
            }
        }

        // Require the BEST overlap to be at least 1 real word.
        if (bestOverlap < 1) {
            return null
        }

        // 6. If two clauses tie on overlap, return null - ambiguous attribution must not invent a date.
        if (isTie) {
            return null
        }

        // 5. If the winning clause contains a temporal expression -> resolve from that clause.
        // If the winning clause does NOT -> return null. This is the fix: the units clause wins
        // nothing from the friday clause.
        val winner = bestClause ?: return null
        if (!hasTemporalExpression(winner)) {
            return null
        }

        return winner
    }

    internal fun attribute(actionText: String, transcript: String): String? =
        attributeClause(actionText, transcript)

    private fun isJson(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("{") ||
            trimmed.startsWith("```") ||
            text.contains("\"actions\"") ||
            text.contains("\"action_items\"") ||
            text.contains("\"actionItems\"") ||
            text.contains("\"title\"")
    }

    fun resolve(actionText: String, transcript: String, modelDue: String?, today: LocalDate): String? {
        if (isJson(transcript)) {
            val haystack = "$actionText $transcript".lowercase()
            return resolveFromHaystack(haystack, modelDue, today)
        }
        val target = attributeClause(actionText, transcript) ?: return null
        return resolveFromHaystack(target, modelDue, today)
    }

    private fun resolveFromHaystack(haystack: String, modelDue: String?, today: LocalDate): String? {
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
