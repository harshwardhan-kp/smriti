package com.smriti.app.ai

import com.smriti.app.data.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EnricherTest {

    @Test
    fun testParseDueValidDate() {
        val dueStr = "2026-10-15"
        val parsed = Enricher.parseDue(dueStr)
        assertNotNull(parsed)
        val expected = LocalDate.parse("2026-10-15")
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, parsed)
    }

    @Test
    fun testParseDueNullOrBlankOrLiteralNull() {
        assertNull(Enricher.parseDue(null))
        assertNull(Enricher.parseDue(""))
        assertNull(Enricher.parseDue("   "))
        assertNull(Enricher.parseDue("null"))
        assertNull(Enricher.parseDue("NULL"))
    }

    @Test
    fun testParseDueInvalidStrings() {
        assertNull(Enricher.parseDue("tomorrow"))
        assertNull(Enricher.parseDue("15/10/2026"))
        assertNull(Enricher.parseDue("not a date"))
    }

    @Test
    fun testTitleForNormal() {
        val title = Enricher.titleFor("Sprint Planning", "Some transcript")
        assertEquals("Sprint Planning", title)
    }

    @Test
    fun testTitleForWhitespaceCollapse() {
        val title = Enricher.titleFor("  Sprint\n\nPlanning   Meeting  ", "")
        assertEquals("Sprint Planning Meeting", title)
    }

    @Test
    fun testTitleForFallbackToTranscript() {
        val title = Enricher.titleFor("", "Weekly review meeting with the team")
        assertEquals("Weekly review meeting with the team", title)
    }

    @Test
    fun testTitleForFallbackToUntitled() {
        val title = Enricher.titleFor("", "")
        assertTrue(title.startsWith("Untitled capture · "))
    }

    @Test
    fun testTitleForTruncationAt60Chars() {
        val longTitle = "This is a very long title that definitely exceeds the sixty character limit set by the requirements"
        val title = Enricher.titleFor(longTitle, "")
        assertTrue(title.length <= 61)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun testRecordEntityDefaults() {
        val record = RecordEntity(
            createdAt = 1000L,
            photoPath = "/path/to/photo.jpg",
            ocrText = "",
            transcript = "test",
            title = "test title",
            summary = "",
            peopleJson = "[]",
            amountsJson = "[]",
            tagsJson = "[]",
            embedding = null
        )
        assertEquals("PENDING", record.enrichmentState)
        assertEquals(0, record.enrichmentAttempts)
        assertNull(record.enrichedAt)
        assertNull(record.enrichmentModel)
        assertNull(record.enrichmentError)
    }

    @Test
    fun testRecordEntityEqualsAndHashCode() {
        val r1 = RecordEntity(
            createdAt = 1000L,
            photoPath = "/path/to/photo.jpg",
            ocrText = "",
            transcript = "test",
            title = "test title",
            summary = "",
            peopleJson = "[]",
            amountsJson = "[]",
            tagsJson = "[]",
            embedding = null,
            enrichmentState = "PENDING",
            enrichmentAttempts = 0,
            enrichedAt = null,
            enrichmentModel = null,
            enrichmentError = null
        )
        val r2 = r1.copy()
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())

        val r3 = r1.copy(enrichmentState = "DONE", enrichedAt = 2000L, enrichmentModel = "test-model")
        assertNotEquals(r1, r3)
        assertNotEquals(r1.hashCode(), r3.hashCode())

        val r4 = r1.copy(enrichmentAttempts = 1, enrichmentError = "failed once")
        assertNotEquals(r1, r4)
        assertNotEquals(r1.hashCode(), r4.hashCode())
    }
}
