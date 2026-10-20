package com.example.zoterohelpernative.pdf

/**
 * Vertical extent of a highlight on one text line.
 *
 * Matches Zotero desktop: the band is the glyph box of the line's characters —
 * the font's ascender-to-descender range at that font size, which both
 * Zotero's reader and MuPDF compute per character. The median top/bottom of
 * the characters is used, so a superscript or a symbol from another font
 * doesn't stretch the whole line.
 *
 * The only adjustment: where the glyph boxes of two consecutive lines in the
 * same column overlap (tight leading), both bands stop at the midpoint of the
 * overlap so highlights never cover each other. Coordinates are page space,
 * y growing downward.
 */
object HighlightBand {

    data class Band(val top: Float, val bottom: Float)

    /**
     * @param glyphTop/glyphBottom median char box top/bottom on the line
     * @param fallbackTop/fallbackBottom the line bbox, used if the inputs are unusable
     * @param prevBottom glyph bottom of the nearest line above in the same column
     * @param nextTop glyph top of the nearest line below in the same column
     */
    fun compute(
        glyphTop: Float,
        glyphBottom: Float,
        fallbackTop: Float,
        fallbackBottom: Float,
        prevBottom: Float? = null,
        nextTop: Float? = null
    ): Band {
        if (!glyphTop.isFinite() || !glyphBottom.isFinite() || glyphBottom <= glyphTop) {
            return Band(fallbackTop, fallbackBottom)
        }
        var top = glyphTop
        var bottom = glyphBottom
        if (prevBottom != null && prevBottom > top && prevBottom < bottom) top = (top + prevBottom) / 2f
        if (nextTop != null && nextTop < bottom && nextTop > top) bottom = (bottom + nextTop) / 2f
        if (bottom <= top) return Band(glyphTop, glyphBottom)
        return Band(top, bottom)
    }

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return Float.NaN
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
