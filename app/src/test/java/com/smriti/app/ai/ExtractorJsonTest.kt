package com.smriti.app.ai

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun testDueDateResolverPinnedCases() {
        val today = LocalDate.of(2026, 9, 5) // a Saturday

        // - resolve("Ships API", "Rohit ships the API by Friday", "2026-09-09", today) == "2026-09-11"
        assertEquals(
            "2026-09-11",
            DueDateResolver.resolve("Ships API", "Rohit ships the API by Friday", "2026-09-09", today)
        )

        // - resolve("Order 200 units from Sharma Traders", "we need two hundred more units", "2026-09-09", today) == null
        assertNull(
            DueDateResolver.resolve("Order 200 units from Sharma Traders", "we need two hundred more units", "2026-09-09", today)
        )

        // - resolve("call back", "call him tomorrow", null, today) == "2026-09-06"
        assertEquals(
            "2026-09-06",
            DueDateResolver.resolve("call back", "call him tomorrow", null, today)
        )

        // - resolve("pay rent", "pay the rent", "2026-09-11", today) == null // no temporal words
        assertNull(
            DueDateResolver.resolve("pay rent", "pay the rent", "2026-09-11", today)
        )

        // - resolve("ship it", "ship it by 2077-01-01", "2077-01-01", today) == null // out of range
        assertNull(
            DueDateResolver.resolve("ship it", "ship it by 2077-01-01", "2077-01-01", today)
        )

        // - a weekday that is today: resolve("x","do it saturday","2026-09-05",today) == "2026-09-12"
        assertEquals(
            "2026-09-12",
            DueDateResolver.resolve("x", "do it saturday", "2026-09-05", today)
        )
    }

    @Test
    fun testDueDateClauseAttribution() {
        val today = LocalDate.of(2026, 9, 5) // a Saturday

        // - resolve("Rohit ships the API",
        //           "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders",
        //           "2026-09-09", today) == "2026-09-11"
        assertEquals(
            "2026-09-11",
            DueDateResolver.resolve(
                "Rohit ships the API",
                "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders",
                "2026-09-09",
                today
            )
        )

        // - resolve("Need two hundred units from Sharma Traders",
        //           "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders",
        //           "2026-09-09", today) == null          // THE BUG BEING FIXED
        assertNull(
            DueDateResolver.resolve(
                "Need two hundred units from Sharma Traders",
                "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders",
                "2026-09-09",
                today
            )
        )

        // - resolve("call the supplier tomorrow", "call the supplier tomorrow", null, today) == "2026-09-06"
        //   (temporal expression in the action text itself, rule 1)
        assertEquals(
            "2026-09-06",
            DueDateResolver.resolve(
                "call the supplier tomorrow",
                "call the supplier tomorrow",
                null,
                today
            )
        )

        // - a single-action transcript still works:
        //   resolve("pay Sharma", "pay Sharma on Monday", null, today) == "2026-09-07"
        assertEquals(
            "2026-09-07",
            DueDateResolver.resolve(
                "pay Sharma",
                "pay Sharma on Monday",
                null,
                today
            )
        )

        // Verify direct clause attribution
        assertEquals(
            "rohit ships the api by friday",
            DueDateResolver.attributeClause(
                "Rohit ships the API",
                "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders"
            )
        )
        assertNull(
            DueDateResolver.attributeClause(
                "Need two hundred units from Sharma Traders",
                "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders"
            )
        )

        // Tie on overlap returns null (rule 6)
        assertNull(
            DueDateResolver.attributeClause(
                "deliver shipment",
                "deliver shipment on Monday and deliver shipment on Friday"
            )
        )

        // Split on comma and then
        assertEquals(
            "2026-09-08",
            DueDateResolver.resolve(
                "deliver goods",
                "pack items, then deliver goods by Tuesday",
                null,
                today
            )
        )

        // Split on semicolon and after that
        assertEquals(
            "2026-09-09",
            DueDateResolver.resolve(
                "audit records",
                "clean warehouse; after that audit records by Wednesday",
                null,
                today
            )
        )
    }

    @Test
    fun testUnconstrainedCallsHaveNullSchemaByDefault() = kotlinx.coroutines.runBlocking {
        var capturedSchema: String? = "not-null"
        val fakeBackend = object : LlmBackend {
            override val label: String = "Fake"
            override suspend fun generate(prompt: String, maxTokens: Int, jsonSchema: String?): String {
                capturedSchema = jsonSchema
                return "answer"
            }
        }
        fakeBackend.generate("test prompt")
        assertNull(capturedSchema)
    }

    /**
     * The exact reply Gemma 4 E4B produced on the Hexagon NPU, byte for byte from logcat.
     * One `}` short: the amounts array closes while the object inside it is still open.
     * Before balanceBrackets this returned null twice and the capture fell back to a record
     * with no actions at all.
     */
    @Test
    fun testNpuMismatchedCloserIsRepaired() {
        val raw = """{"actions":[{"text":"Ship the API","due":"2026-09-08"}],""" +
            """"title":"Ship the API","summary":"Ship the API","people":[],""" +
            """"amounts":[{"value":0,"currency":"INR","label":""],"tags":[]}"""
        val record = Extractor(null).parseForTest(raw, "Rohit ships the API by Friday")
        assertNotNull(record)
        assertEquals(1, record!!.actions.size)
        assertEquals("Ship the API", record.actions[0].text)
        assertEquals("Ship the API", record.title)
    }

    /** A reply that simply stopped early, which the same walk closes. */
    @Test
    fun testTruncatedReplyIsClosed() {
        val raw = """{"actions":[{"text":"Pay Sharma","due":null}],"title":"Pay Sharma"""" + '"'
        val record = Extractor(null).parseForTest(raw, "Pay Sharma tomorrow")
        assertNotNull(record)
        assertEquals("Pay Sharma", record!!.actions[0].text)
    }

    /** Balancing must be a no-op on well-formed JSON, not a rewrite of it. */
    @Test
    fun testWellFormedJsonIsUnchangedByBalancing() {
        val raw = """{"actions":[],"title":"He said \"hi\"","summary":"","people":[],""" +
            """"amounts":[{"value":500,"currency":"INR","label":"cash"}],"tags":["a"]}"""
        val record = Extractor(null).parseForTest(raw, "")
        assertNotNull(record)
        assertEquals("He said \"hi\"", record!!.title)
        assertEquals(0, record.actions.size)
    }

    /** Brackets inside string values are text, not structure, and must not move the stack. */
    @Test
    fun testBracketsInsideStringsAreNotStructure() {
        val raw = """{"actions":[],"title":"a [b] {c}","summary":"","people":[],"amounts":[],"tags":[]}"""
        val record = Extractor(null).parseForTest(raw, "")
        assertNotNull(record)
        assertEquals("a [b] {c}", record!!.title)
    }
}
