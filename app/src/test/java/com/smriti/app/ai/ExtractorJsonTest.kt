package com.smriti.app.ai

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorJsonTest {

    private val extractor = Extractor(null)
    private val gson = Gson()

    @Test
    fun testCleanJson() {
        val input = """{"title":"Meeting Notes","summary":"Discuss budget","people":[],"amounts":[],"tags":[],"actions":[]}"""
        val repaired = extractor.repairJson(input)
        assertEquals(input, repaired)
        val record = gson.fromJson(repaired, StructuredRecord::class.java)
        assertNotNull(record)
        assertEquals("Meeting Notes", record.title)
    }

    @Test
    fun testJsonWrappedInFences() {
        val input = """
            ```json
            {
              "title": "Sprint Planning",
              "summary": "Plan sprint items",
              "people": ["Alice", "Bob"],
              "amounts": [],
              "tags": ["work"],
              "actions": []
            }
            ```
        """.trimIndent()
        val repaired = extractor.repairJson(input)
        val record = gson.fromJson(repaired, StructuredRecord::class.java)
        assertNotNull(record)
        assertEquals("Sprint Planning", record.title)
        assertEquals(listOf("Alice", "Bob"), record.people)
    }

    @Test
    fun testJsonWithLeadingProse() {
        val input = """
            Here is the extraction result:
            {
              "title": "Doctor Visit",
              "summary": "Annual checkup",
              "people": ["Dr. Sharma"],
              "amounts": [],
              "tags": ["health"],
              "actions": [{"text": "Follow up in 3 months", "due": "2026-12-01"}]
            }
            Hope this helps!
        """.trimIndent()
        val repaired = extractor.repairJson(input)
        val record = gson.fromJson(repaired, StructuredRecord::class.java)
        assertNotNull(record)
        assertEquals("Doctor Visit", record.title)
        assertEquals(1, record.actions.size)
        assertEquals("2026-12-01", record.actions[0].due)
    }

    @Test
    fun testJsonWithTrailingCommas() {
        val input = """
            {
              "title": "Market Run",
              "summary": "Weekly supplies",
              "people": ["John",],
              "amounts": [
                {
                  "value": 550.0,
                  "currency": "INR",
                  "label": "Groceries",
                },
              ],
              "tags": ["shopping",],
              "actions": [],
            }
        """.trimIndent()
        val repaired = extractor.repairJson(input)
        val record = gson.fromJson(repaired, StructuredRecord::class.java)
        assertNotNull(record)
        assertEquals("Market Run", record.title)
        assertEquals(1, record.amounts.size)
        assertEquals(550.0, record.amounts[0].value, 0.001)
        assertEquals("INR", record.amounts[0].currency)
    }

    @Test
    fun testTextWithNoBraces() {
        val input = "Unable to process the image and no content was found."
        val repaired = extractor.repairJson(input)
        assertTrue(repaired.isEmpty())
    }
}