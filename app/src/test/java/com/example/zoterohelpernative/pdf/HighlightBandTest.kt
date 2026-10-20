package com.example.zoterohelpernative.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected values follow zotero/pdf.js evaluator.js char rect rules. */
class HighlightBandTest {

    private val eps = 1e-4f

    @Test
    fun capHeightReplacesTheAscentWhenLower() {
        // Times-like: ascent 0.891, descent -0.216, capHeight 0.662
        val (a, d) = HighlightBand.zoteroAscentDescent(0.891f, -0.216f, 0.662f)
        assertEquals(0.662f, a, eps)
        assertEquals(-0.216f, d, eps)
    }

    @Test
    fun capHeightAboveTheAscentIsIgnored() {
        val (a, _) = HighlightBand.zoteroAscentDescent(0.7f, -0.2f, 0.9f)
        assertEquals(0.7f, a, eps)
    }

    @Test
    fun absurdMetricsAreClampedLikePdfJs() {
        val (a, d) = HighlightBand.zoteroAscentDescent(1.3f, -0.8f, null)
        assertEquals(0.75f, a, eps)
        assertEquals(-0.25f, d, eps)
    }

    @Test
    fun missingMetricsUseTheDefaults() {
        assertEquals(0.75f to -0.25f, HighlightBand.zoteroAscentDescent(0f, -0.2f, null))
    }

    @Test
    fun positiveDescentIsFlipped() {
        assertEquals(-0.2f, HighlightBand.zoteroAscentDescent(0.8f, 0.2f, null).second, eps)
    }

    @Test
    fun charBoxFromAMuPdfQuad() {
        // 10pt, baseline at y=100, MuPDF quad from ascent 0.891 / descent -0.216
        val box = HighlightBand.zoteroCharBox(100f, 10f, quadTop = 91.09f, quadBottom = 102.16f, capHeight = 0.662f)!!
        assertEquals(100f - 6.62f, box.top, 1e-3f)   // cap height, not ascender
        assertEquals(102.16f, box.bottom, 1e-3f)     // descender unchanged
    }

    @Test
    fun lineIsTheUnionOfItsChars() {
        val u = HighlightBand.union(listOf(HighlightBand.Band(93f, 102f), HighlightBand.Band(92f, 101f)))
        assertEquals(HighlightBand.Band(92f, 102f), u)
        assertNull(HighlightBand.union(emptyList()))
    }

    @Test
    fun standardFontCapHeights() {
        assertEquals(0.662f, HighlightBand.standardCapHeight("ABCDEF+Times-Roman")!!, eps)
        assertEquals(0.676f, HighlightBand.standardCapHeight("Times-Bold")!!, eps)
        assertEquals(0.718f, HighlightBand.standardCapHeight("ArialMT")!!, eps)
        assertNull(HighlightBand.standardCapHeight("MinionPro-Regular"))
    }

    @Test
    fun subsetTagIsIgnoredWhenMatchingFonts() {
        assertEquals(HighlightBand.normalizeFontName("Times-Roman"), HighlightBand.normalizeFontName("QWERTY+Times-Roman"))
    }
}
