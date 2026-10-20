package com.example.zoterohelpernative.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineKeyTest {

    @Test
    fun keysBuiltFromTwoWalksOfTheSameLineMatch() {
        // StructuredText.getBlocks() returns fresh TextLine objects on every call
        // (no equals), so bands keyed by object identity were never found and the
        // old line box was always used. Keys built from the same bbox must match.
        val bands = mapOf(LineKey(72f, 100f, 520f, 112f) to HighlightBand.Band(101f, 111f))
        assertEquals(HighlightBand.Band(101f, 111f), bands[LineKey(72f, 100f, 520f, 112f)])
    }

    @Test
    fun differentLinesDoNotCollide() {
        val bands = mapOf(LineKey(72f, 100f, 520f, 112f) to HighlightBand.Band(101f, 111f))
        assertNull(bands[LineKey(72f, 114f, 520f, 126f)])
        assertNotEquals(LineKey(72f, 100f, 520f, 112f), LineKey(300f, 100f, 520f, 112f))
    }
}
