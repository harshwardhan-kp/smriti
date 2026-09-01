package com.smriti.app.ai

import org.junit.Assert.*
import org.junit.Test

class VectorMathEdgeTest {

    @Test
    fun testCosineWithNaNDoesNotReturnNaN() {
        val a = floatArrayOf(Float.NaN, 1f, 2f)
        val b = floatArrayOf(1f, 2f, 3f)
        val result = VectorMath.cosine(a, b)
        assertTrue(result.isFinite())
        assertFalse(result.isNaN())
    }

    @Test
    fun testCosineWithZeroVectorReturnsZeroAndNotNaN() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val nonZero = floatArrayOf(1f, 2f, 3f)
        val result = VectorMath.cosine(zero, nonZero)
        assertTrue(result.isFinite())
        assertFalse(result.isNaN())
        assertEquals(0f, result, 0f)
    }

    @Test
    fun testCosineAntiParallelCloseToMinusOne() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        val result = VectorMath.cosine(a, b)
        assertTrue(result.isFinite())
        assertEquals(-1f, result, 1e-5f)
    }

    @Test
    fun testTopKWithKLargerThanCandidatesReturnsAll() {
        val query = floatArrayOf(1f, 0f, 0f)
        val candidates = listOf(
            1L to floatArrayOf(1f, 0f, 0f),
            2L to floatArrayOf(0f, 1f, 0f)
        )
        val result = VectorMath.topK(query, candidates, k = 5)
        assertEquals(2, result.size)
        assertEquals(1L, result[0].first)
    }

    @Test
    fun testTopKWithEmptyCandidatesReturnsEmpty() {
        val query = floatArrayOf(1f, 0f, 0f)
        val candidates = emptyList<Pair<Long, FloatArray>>()
        val result = VectorMath.topK(query, candidates, k = 3)
        assertTrue(result.isEmpty())
    }
}
