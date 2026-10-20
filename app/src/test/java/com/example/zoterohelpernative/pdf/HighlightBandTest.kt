package com.example.zoterohelpernative.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightBandTest {

    @Test
    fun usesTheGlyphBoxLikeZotero() {
        // Normal leading: glyph boxes don't touch, band = glyph box exactly
        val b = HighlightBand.compute(100f, 111f, 98f, 113f, prevBottom = 99f, nextTop = 112f)
        assertEquals(HighlightBand.Band(100f, 111f), b)
    }

    @Test
    fun overlappingGlyphBoxesMeetAtTheMidpoint() {
        // Tight leading: line 1 glyphs 100..113, line 2 glyphs 111..124
        val upper = HighlightBand.compute(100f, 113f, 100f, 113f, nextTop = 111f)
        val lower = HighlightBand.compute(111f, 124f, 111f, 124f, prevBottom = 113f)
        assertEquals(112f, upper.bottom, 0f)
        assertEquals(112f, lower.top, 0f)
        assertTrue(upper.bottom <= lower.top)
    }

    @Test
    fun manyConsecutiveLinesNeverOverlap() {
        // 12pt glyph boxes on an 11pt pitch: every pair overlaps by 1pt
        val tops = (0 until 20).map { 100f + it * 11f }
        val bands = tops.mapIndexed { i, t ->
            HighlightBand.compute(t, t + 12f, t, t + 12f,
                prevBottom = if (i > 0) tops[i - 1] + 12f else null,
                nextTop = tops.getOrNull(i + 1))
        }
        bands.zipWithNext().forEach { (a, b) -> assertTrue(a.bottom <= b.top) }
    }

    @Test
    fun unusableInputsFallBackToTheLineBox() {
        assertEquals(HighlightBand.Band(90f, 102f), HighlightBand.compute(Float.NaN, 100f, 90f, 102f))
        assertEquals(HighlightBand.Band(90f, 102f), HighlightBand.compute(100f, 100f, 90f, 102f))
    }

    @Test
    fun medianIgnoresOutliers() {
        assertEquals(12f, HighlightBand.median(listOf(12f, 12f, 30f, 11f, 12f)), 0f)
    }
}
