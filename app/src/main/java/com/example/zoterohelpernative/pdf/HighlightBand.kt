package com.example.zoterohelpernative.pdf

/**
 * Vertical extent of a highlight on one text line, matching Zotero desktop.
 *
 * Starts from the line's glyph box (median char box, so a superscript or a
 * symbol from another font doesn't stretch the line), centered on the text,
 * and caps its height at [MAX_PITCH_SHARE] of the distance to the nearest
 * line in the same column, so consecutive highlights keep a thin gap of paper
 * like Zotero's. Coordinates are page space, y growing downward.
 */
object HighlightBand {

    data class Band(val top: Float, val bottom: Float)

    /**
     * Share of the line pitch a band may take. Measured on Zotero desktop
     * highlights: ~14 px bands on a 15.3 px line pitch (0.91), leaving a hairline
     * of paper between lines. MuPDF's glyph box is a little taller than
     * Zotero's, so without this cap consecutive bands touch and read as one blob.
     */
    const val MAX_PITCH_SHARE = 0.92f

    /**
     * @param glyphTop/glyphBottom median char box top/bottom on the line
     * @param fallbackTop/fallbackBottom the line bbox, used if the inputs are unusable
     * @param prevCenter/nextCenter vertical centers of the nearest lines above/below
     *        in the same column, if any
     */
    fun compute(
        glyphTop: Float,
        glyphBottom: Float,
        fallbackTop: Float,
        fallbackBottom: Float,
        prevCenter: Float? = null,
        nextCenter: Float? = null
    ): Band {
        if (!glyphTop.isFinite() || !glyphBottom.isFinite() || glyphBottom <= glyphTop) {
            return Band(fallbackTop, fallbackBottom)
        }
        val center = (glyphTop + glyphBottom) / 2f
        val pitches = listOfNotNull(
            prevCenter?.takeIf { it < center }?.let { center - it },
            nextCenter?.takeIf { it > center }?.let { it - center }
        )
        val height = glyphBottom - glyphTop
        val maxHeight = pitches.minOrNull()?.let { it * MAX_PITCH_SHARE } ?: height
        val h = minOf(height, maxHeight)
        return Band(center - h / 2f, center + h / 2f)
    }

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return Float.NaN
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
