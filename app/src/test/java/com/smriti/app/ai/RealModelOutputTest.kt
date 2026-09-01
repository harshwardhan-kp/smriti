package com.smriti.app.ai

import org.junit.Assert.*
import org.junit.Test

class RealModelOutputTest {

    private val extractor = Extractor(null)

    // Gemma 3 1B on a Redmi Note 10S — fenced, correct key, actions as plain strings
    @Test
    fun testGemma3_1B_RedmiNote10S_fencedCorrectKeyPlainStrings() {
        val raw = "```json\n{\n  \"actions\": [\n    \"Ship API\",\n    \"Request from Sharma Traders: 200 units\"\n  ]\n}\n```"
        val repaired = extractor.repairJson(raw)
        assertTrue(repaired.contains("\"actions\""))
        val record = extractor.parseForTest(raw)
        assertNotNull(record)
        assertEquals(2, record!!.actions.size)
        assertTrue(record.actions[0].text.contains("Ship API"))
        assertTrue(record.actions.all { it.text.isNotBlank() })
    }

    // Qwen2.5 0.5B on a Redmi Note 10S — wrong key, plain strings
    @Test
    fun testQwen25_0_5B_RedmiNote10S_wrongKeyPlainStrings() {
        val raw = """{"actionItems": ["ship the API by Friday", "order two hundred more units from Sharma Traders"]}"""
        val repaired = extractor.repairJson(raw)
        assertTrue(repaired.isNotEmpty())
        val record = extractor.parseForTest(raw)
        assertNotNull(record)
        assertEquals(2, record!!.actions.size)
    }

    // Muse Spark 1.2 via api.meta.ai — third alias shape, objects with assignee/task/due
    @Test
    fun testMuseSpark1_2_viaApiMetaAi_actionItemsObjects() {
        val raw = """{"action_items": [{"assignee": "Rohit", "task": "Ship the API", "due": "Friday"}, {"assignee": null, "task": "Order two hundred more units from Sharma Traders", "due": null}]}"""
        val repaired = extractor.repairJson(raw)
        assertTrue(repaired.isNotEmpty())
        val record = extractor.parseForTest(raw)
        assertNotNull(record)
        assertEquals(2, record!!.actions.size)
        assertTrue(record.actions[0].text.contains("Ship the API"))
    }

    // Well-formed full schema — assert title, summary, one person, one tag, one action with due == 2026-09-04
    @Test
    fun testWellFormedFullSchema() {
        val raw = """{"title":"Sprint Delivery","summary":"Shipped API and ordered units from Sharma Traders","people":["Rohit"],"amounts":[],"tags":["work"],"actions":[{"text":"Ship API","due":"2026-09-04"}]}"""
        val repaired = extractor.repairJson(raw)
        assertEquals(raw, repaired)
        val record = extractor.parseForTest(raw)
        assertNotNull(record)
        assertEquals("Sprint Delivery", record!!.title)
        assertEquals("Shipped API and ordered units from Sharma Traders", record.summary)
        assertEquals(1, record.people.size)
        assertEquals("Rohit", record.people[0])
        assertEquals(1, record.tags.size)
        assertEquals("work", record.tags[0])
        assertEquals(1, record.actions.size)
        assertEquals("2026-09-04", record.actions[0].due)
    }

    // Prose before the JSON — "Here is the result:\n{...}" — assert it still parses
    @Test
    fun testProseBeforeJson() {
        val raw = "Here is the result:\n{\"title\":\"Sprint Delivery\",\"summary\":\"Shipped API and ordered units\",\"people\":[\"Rohit\"],\"amounts\":[],\"tags\":[\"work\"],\"actions\":[{\"text\":\"Ship API\",\"due\":\"2026-09-04\"}]}"
        val repaired = extractor.repairJson(raw)
        assertTrue(repaired.startsWith("{"))
        assertTrue(repaired.contains("\"title\""))
        val record = extractor.parseForTest(raw)
        assertNotNull(record)
        assertEquals("Sprint Delivery", record!!.title)
    }

    // Trailing commas before } and ] — assert it still parses
    @Test
    fun testTrailingCommas() {
        val raw = """
            {
              "title": "Market Run",
              "summary": "Weekly supplies",
              "people": ["John",],
              "amounts": [],
              "tags": ["shopping",],
              "actions": [{"text": "Ship API", "due": "2026-09-04"},],
            }
        """.trimIndent()
        val repaired = extractor.repairJson(raw)
        // trailing commas should be removed
        assertFalse(repaired.contains(",}"))
        assertFalse(repaired.contains(",]"))
        val record = extractor.parseForTest(raw)
        assertNotNull(record)
        assertEquals("Market Run", record!!.title)
    }

    // Pure prose with no braces — assert parseForTest returns null
    @Test
    fun testPureProseWithNoBraces() {
        val raw = "Just some prose without any JSON object and no braces at all"
        val repaired = extractor.repairJson(raw)
        assertTrue(repaired.isEmpty())
        val record = extractor.parseForTest(raw)
        assertNull(record)
    }

    // An empty string — assert parseForTest returns null
    @Test
    fun testEmptyString() {
        val raw = ""
        val repaired = extractor.repairJson(raw)
        assertTrue(repaired.isEmpty())
        val record = extractor.parseForTest(raw)
        assertNull(record)
    }
}
