package com.smriti.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VectorMathTest {

    @Test
    fun `identical vectors score 1 within tolerance`() {
        val a = floatArrayOf(0.5f, 1.2f, -0.8f, 3.0f)
        val b = floatArrayOf(0.5f, 1.2f, -0.8f, 3.0f)
        val score = VectorMath.cosine(a, b)
        assertEquals(1.0f, score, 1e-5f)
    }

    @Test
    fun `orthogonal vectors score 0`() {
        val a = floatArrayOf(1.0f, 0.0f, 0.0f)
        val b = floatArrayOf(0.0f, 1.0f, 0.0f)
        val score = VectorMath.cosine(a, b)
        assertEquals(0.0f, score, 1e-5f)
    }

    @Test
    fun `mismatched vector lengths return 0`() {
        val a = floatArrayOf(1.0f, 2.0f)
        val b = floatArrayOf(1.0f, 2.0f, 3.0f)
        val score = VectorMath.cosine(a, b)
        assertEquals(0f, score, 0.0f)
    }

    @Test
    fun `empty arrays return 0`() {
        assertEquals(0f, VectorMath.cosine(floatArrayOf(), floatArrayOf(1f, 2f)), 0.0f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(1f, 2f), floatArrayOf()), 0.0f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(), floatArrayOf()), 0.0f)
    }

    @Test
    fun `zero vector does not produce NaN`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val nonZero = floatArrayOf(1f, 2f, 3f)

        val scoreWithNonZero = VectorMath.cosine(zero, nonZero)
        assertFalse(scoreWithNonZero.isNaN())
        assertEquals(0f, scoreWithNonZero, 0.0f)

        val scoreWithBothZero = VectorMath.cosine(zero, zero)
        assertFalse(scoreWithBothZero.isNaN())
        assertEquals(0f, scoreWithBothZero, 0.0f)
    }

    @Test
    fun `topK returns k items in descending score order`() {
        val query = floatArrayOf(1.0f, 0.0f, 0.0f)
        val candidates = listOf(
            1L to floatArrayOf(0.0f, 1.0f, 0.0f),         // cosine = 0.0
            2L to floatArrayOf(1.0f, 0.0f, 0.0f),         // cosine = 1.0
            3L to floatArrayOf(0.7071f, 0.7071f, 0.0f),  // cosine ~ 0.7071
            4L to floatArrayOf(-1.0f, 0.0f, 0.0f)        // cosine = -1.0
        )

        val top2 = VectorMath.topK(query, candidates, k = 2)

        assertEquals(2, top2.size)
        assertEquals(2L, top2[0].first)
        assertEquals(1.0f, top2[0].second, 1e-4f)

        assertEquals(3L, top2[1].first)
        assertEquals(0.7071f, top2[1].second, 1e-4f)
    }
}