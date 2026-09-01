package com.smriti.app.ai

class LocalLlmBackend(private val engine: LlmEngine) : LlmBackend {
    override val label: String get() = engine.backend
    override suspend fun generate(prompt: String, maxTokens: Int): String = engine.generate(prompt, maxTokens)
    override fun close() = engine.close()
}