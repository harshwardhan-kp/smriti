package com.smriti.app.ai

/**
 * Abstraction over on-device and cloud LLM backends.
 *
 * The shipping build must always be `offline`; `devcloud` exists solely so prompt work
 * does not cost 12-60 s per iteration on a handset.
 */
interface LlmBackend {
    val label: String
    suspend fun generate(prompt: String, maxTokens: Int = 512): String
    fun close() {}
}