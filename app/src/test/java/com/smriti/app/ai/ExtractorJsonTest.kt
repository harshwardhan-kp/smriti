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

    @Test
    fun testSingleLineBackslashEscapedModelOutput() {
        // Measured on an iQOO 15, Qwen2.5-1.5B q8, 2026-09-05: the model returned its
        // answer as single-line, backslash-escaped JSON — literal \n and \" sequences
        // rather than real newlines and quotes, with "people" unescaped.
        val input = "```json\\n{\\n  \\\"title\\\": \\\"Action Items for Delivery\\\",\\n  \\\"summary\\\": \\\"Sharma Traders needs two hundred more API units by Friday.\\\",\\n  \\\"people\\\": [\"Rohit\", \"Sharma Traders\"],\\n  \\\"amounts\\\": [{\\\"value\\\": 200, \\\"currency\\\": \\\"INR\\\", \\\"label\\\": \\\"Units\\\"}]\\n}\\n```"
        val record = extractor.parseForTest(input)
        assertNotNull(record)
        assertEquals("Action Items for Delivery", record!!.title)
        assertTrue(record.summary.isNotBlank())
        assertEquals(listOf("Rohit", "Sharma Traders"), record.people)
    }

    @Test
    fun testLegitimatelyEscapedQuotesStillParse() {
        // {"title": "He said \"hi\""} is VALID JSON today and must keep parsing to
        // He said "hi": the unescape must only run after a genuine parse failure.
        val input = "{\"title\": \"He said \\\"hi\\\"\"}"
        val repaired = extractor.repairJson(input)
        assertEquals(input, repaired)
        val record = extractor.parseForTest(input)
        assertNotNull(record)
        assertEquals("He said \"hi\"", record!!.title)
    }

    @Test
    fun testAlreadyValidJsonReturnedUnchanged() {
        val input = """{"title":"Sprint Delivery","summary":"Shipped API","people":["Rohit"],"amounts":[],"tags":[],"actions":[]}"""
        val repaired = extractor.repairJson(input)
        assertEquals(input, repaired)
        val record = extractor.parseForTest(input)
        assertNotNull(record)
        assertEquals("Sprint Delivery", record!!.title)
    }
}