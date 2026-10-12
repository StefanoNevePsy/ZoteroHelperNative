package com.example.zoterohelpernative.pdf

/**
 * Vertical extent of a highlight on one text line, sized to the font instead of
 * the line box.
 *
 * MuPDF's line bbox spans the font's full ascender-to-descender range plus any
 * leading, so consecutive lines' boxes overlap and highlights bleed into the
 * lines above and below. Here the band is built from the baseline and the
 * glyph height: from the cap height down to just below the baseline (where
 * descenders sit), then clamped so it never crosses the midpoint to a
 * neighbouring line. Coordinates are page space, y growing downward.
 */
object HighlightBand {
    /** Glyph boxes are ~1.2× the font size for typical text faces. */
    private const val GLYPH_TO_FONT = 1.0f / 1.2f
    private const val ABOVE_BASELINE = 0.80f // ≈ cap height + a little air
    private const val BELOW_BASELINE = 0.24f // ≈ descender depth

    data class Band(val top: Float, val bottom: Float)

    /**
     * @param baseline baseline y of the line (median of the chars' origins)
     * @param glyphHeight median char quad height on the line
     * @param fallbackTop/fallbackBottom the line bbox, used if the inputs are unusable
     * @param prevBaseline/nextBaseline baselines of the nearest lines above/below
     *        that share horizontal space with this one, if any
     */
    fun compute(
        baseline: Float,
        glyphHeight: Float,
        fallbackTop: Float,
        fallbackBottom: Float,
        prevBaseline: Float? = null,
        nextBaseline: Float? = null
    ): Band {
        if (!baseline.isFinite() || !glyphHeight.isFinite() || glyphHeight <= 0f) {
            return Band(fallbackTop, fallbackBottom)
        }
        val fontSize = glyphHeight * GLYPH_TO_FONT
        var top = baseline - fontSize * ABOVE_BASELINE
        var bottom = baseline + fontSize * BELOW_BASELINE

        // Never reach past halfway to an adjacent line's text
        if (prevBaseline != null && prevBaseline < baseline) {
            val gapTop = prevBaseline + (baseline - prevBaseline) * 0.5f - fontSize * (ABOVE_BASELINE - BELOW_BASELINE) * 0.5f
            top = maxOf(top, minOf(gapTop, baseline - fontSize * 0.5f))
        }
        if (nextBaseline != null && nextBaseline > baseline) {
            val gapBottom = baseline + (nextBaseline - baseline) * 0.5f - fontSize * (ABOVE_BASELINE - BELOW_BASELINE) * 0.5f
            bottom = minOf(bottom, maxOf(gapBottom, baseline + fontSize * 0.1f))
        }
        // Stay inside the line box MuPDF reports
        top = top.coerceAtLeast(fallbackTop)
        bottom = bottom.coerceAtMost(fallbackBottom)
        if (bottom <= top) return Band(fallbackTop, fallbackBottom)
        return Band(top, bottom)
    }

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return Float.NaN
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
