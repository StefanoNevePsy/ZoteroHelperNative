package com.example.zoterohelpernative.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightBandTest {

    @Test
    fun matchesZoteroOnTheReportedPage() {
        // Screenshot measurements: line pitch 15.3 px; MuPDF glyph box as tall as
        // the pitch (bands touched); Zotero's bands ~14 px with a gap between lines
        val pitch = 15.3f
        val center = 340f
        val b = HighlightBand.compute(center - pitch / 2, center + pitch / 2, 0f, 1000f,
            prevCenter = center - pitch, nextCenter = center + pitch)
        assertEquals(14f, b.bottom - b.top, 0.2f)
        assertEquals(center, (b.top + b.bottom) / 2f, 0.001f)
    }

    @Test
    fun consecutiveLinesKeepAGap() {
        val pitch = 12f
        val centers = (0 until 20).map { 100f + it * pitch }
        val bands = centers.mapIndexed { i, c ->
            // Glyph boxes taller than the pitch: they'd overlap uncapped
            HighlightBand.compute(c - 7f, c + 7f, c - 7f, c + 7f, centers.getOrNull(i - 1), centers.getOrNull(i + 1))
        }
        bands.zipWithNext().forEach { (a, b) -> assertTrue("gap ${b.top - a.bottom}", b.top - a.bottom >= pitch * 0.07f) }
    }

    @Test
    fun looseLeadingKeepsTheGlyphBox() {
        val b = HighlightBand.compute(100f, 111f, 98f, 113f, prevCenter = 85f, nextCenter = 126f)
        assertEquals(HighlightBand.Band(100f, 111f), b)
    }

    @Test
    fun aLoneLineUsesTheGlyphBox() {
        assertEquals(HighlightBand.Band(100f, 112f), HighlightBand.compute(100f, 112f, 90f, 120f))
    }

    @Test
    fun scalesWithTheFont() {
        val small = HighlightBand.compute(100f, 109f, 0f, 999f, 88f, 116f)
        val large = HighlightBand.compute(100f, 127f, 0f, 999f, 80f, 146f)
        assertTrue((large.bottom - large.top) > 2.5f * (small.bottom - small.top))
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
