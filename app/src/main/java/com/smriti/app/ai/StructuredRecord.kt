package com.smriti.app.ai

data class StructuredRecord(
    val title: String = "",
    val summary: String = "",
    val people: List<String> = emptyList(),
    val amounts: List<Amount> = emptyList(),
    val tags: List<String> = emptyList(),
    val actions: List<Action> = emptyList()
) {
    companion object {
        fun fallback(rawText: String): StructuredRecord = StructuredRecord(
            title = rawText.take(60),
            summary = "",
            people = emptyList(),
            amounts = emptyList(),
            tags = emptyList(),
            actions = emptyList()
        )
    }
}

data class Amount(
    val value: Double = 0.0,
    val currency: String = "INR",
    val label: String = ""
)

data class Action(
    val text: String = "",
    val due: String? = null
)