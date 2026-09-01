package com.smriti.app.ai

import kotlin.math.sqrt

/**
 * Vector similarity utilities for in-memory record retrieval.
 *
 * Design Decision:
 * Brute-force linear scan over a few hundred records executes in sub-millisecond time on
 * modern mobile ARM cores. Introducing a dedicated vector database or SQLite vector extension
 * would add external dependencies, native compilation overhead, and edge-case failure modes
 * for no user-visible gain.
 *
 * Consider transitioning to an approximate nearest neighbor (ANN) index or SQLite vector
 * extension only if the database grows beyond ~10,000 embedded records.
 */
object VectorMath {

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) {
            return 0f
        }

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            val ai = a[i].toDouble()
            val bi = b[i].toDouble()
            dot += ai * bi
            normA += ai * ai
            normB += bi * bi
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0f
        }

        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator == 0.0) {
            return 0f
        }

        val similarity = (dot / denominator).toFloat()
        return when {
            similarity.isNaN() -> 0f
            similarity > 1f -> 1f
            similarity < -1f -> -1f
            else -> similarity
        }
    }

    fun topK(
        query: FloatArray,
        candidates: List<Pair<Long, FloatArray>>,
        k: Int
    ): List<Pair<Long, Float>> {
        if (candidates.isEmpty() || k <= 0) {
            return emptyList()
        }

        return candidates
            .asSequence()
            .map { (id, embedding) -> id to cosine(query, embedding) }
            .sortedByDescending { it.second }
            .take(k)
            .toList()
    }
}