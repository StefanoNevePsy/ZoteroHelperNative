package com.example.zoterohelpernative.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlignToLineTest {

    private val lines = mapOf(
        LineKey(72f, 100f, 520f, 112f) to HighlightBand.Band(102.7f, 112.2f),
        LineKey(72f, 113f, 520f, 125f) to HighlightBand.Band(115.7f, 125.2f),
        LineKey(300f, 100f, 520f, 112f) to HighlightBand.Band(0f, 0f) // other column, not overlapping x below
    ).filterKeys { it.x0 < 300f }

    @Test
    fun anOldTallRectSnapsToItsLinesBand() {
        // An app highlight saved with the full line box (100..113 in page space)
        assertEquals(HighlightBand.Band(102.7f, 112.2f), HighlightBand.alignToLine(80f, 100f, 300f, 113f, lines))
    }

    @Test
    fun aZoteroRectStaysOnTheSameBand() {
        assertEquals(HighlightBand.Band(115.7f, 125.2f), HighlightBand.alignToLine(80f, 115.7f, 300f, 125.2f, lines))
    }

    @Test
    fun aRectWithNoTextUnderItIsDrawnAsStored() {
        assertNull(HighlightBand.alignToLine(80f, 300f, 300f, 340f, lines)) // e.g. over a figure
        assertNull(HighlightBand.alignToLine(530f, 100f, 560f, 112f, lines)) // outside the line span
    }
}
