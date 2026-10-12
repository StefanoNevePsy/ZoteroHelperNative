package com.example.zoterohelpernative.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightBandTest {

    // 10pt body text set solid-ish: baselines 12pt apart, glyph boxes 12pt tall,
    // MuPDF line boxes from baseline-9.6 to baseline+2.4 (adjacent boxes touch).
    private fun line(baseline: Float) = Triple(baseline, baseline - 9.6f, baseline + 2.4f)

    private fun band(baseline: Float, prev: Float? = baseline - 12f, next: Float? = baseline + 12f): HighlightBand.Band {
        val (b, top, bottom) = line(baseline)
        return HighlightBand.compute(b, glyphHeight = 12f, fallbackTop = top, fallbackBottom = bottom, prevBaseline = prev, nextBaseline = next)
    }

    @Test
    fun consecutiveLinesDoNotOverlap() {
        val bands = (0 until 20).map { band(100f + it * 12f) }
        bands.zipWithNext().forEach { (upper, lower) ->
            assertTrue("upper ${upper.bottom} vs lower ${lower.top}", upper.bottom < lower.top)
        }
    }

    @Test
    fun coversCapsAndDescenders() {
        val b = band(200f)
        val font = 10f
        assertTrue("caps reach ~0.7 em above baseline", b.top <= 200f - 0.7f * font)
        assertTrue("descenders ~0.2 em below", b.bottom >= 200f + 0.2f * font)
    }

    @Test
    fun scalesWithTheFontSize() {
        val small = HighlightBand.compute(100f, 9f, 92f, 102f)
        val large = HighlightBand.compute(100f, 24f, 78f, 106f)
        assertTrue((large.bottom - large.top) > 2.4f * (small.bottom - small.top))
    }

    @Test
    fun tightLeadingIsClampedAtTheMidpoint() {
        // Baselines only 10pt apart for 10pt text: bands must still not touch
        val a = HighlightBand.compute(100f, 12f, 88f, 104f, prevBaseline = 90f, nextBaseline = 110f)
        val b = HighlightBand.compute(110f, 12f, 98f, 114f, prevBaseline = 100f, nextBaseline = 120f)
        assertTrue(a.bottom <= b.top)
    }

    @Test
    fun staysInsideTheLineBox() {
        val b = HighlightBand.compute(100f, 30f, 95f, 101f)
        assertTrue(b.top >= 95f && b.bottom <= 101f)
    }

    @Test
    fun unusableInputsFallBackToTheLineBox() {
        assertEquals(HighlightBand.Band(90f, 102f), HighlightBand.compute(Float.NaN, 12f, 90f, 102f))
        assertEquals(HighlightBand.Band(90f, 102f), HighlightBand.compute(100f, 0f, 90f, 102f))
    }

    @Test
    fun medianIgnoresOutliers() {
        assertEquals(12f, HighlightBand.median(listOf(12f, 12f, 30f, 11f, 12f)), 0f)
    }
}
