package com.example.zoterohelpernative.pdf

/**
 * Highlight geometry ported from Zotero desktop, so highlights made in the app
 * match the ones made on the computer.
 *
 * Source: zotero/pdf.js (Zotero's fork), src/core/evaluator.js, char rect
 * construction, and zotero/reader src/pdf/selection.js getRectsFromChars:
 *
 *   ascent = font.ascent, descent = font.descent (descent made negative)
 *   if ascent and descent are both set:
 *       ascent > 1     → 0.75
 *       descent < -0.5 → -0.25
 *   else ascent = 0.75, descent = -0.25
 *   if 0 < capHeight < ascent → ascent = capHeight
 *   char box = baseline + fontSize × [descent, ascent]
 *   highlight rect of a line = union of its chars' boxes
 *
 * ascent/descent come from the embedded font (hhea), which is also what MuPDF
 * uses for its glyph quads, so they're recovered from the quads; capHeight comes
 * from the PDF font descriptor (/CapHeight) or the standard-14 font metrics.
 * Coordinates here are page space, y growing downward.
 */
object HighlightBand {

    data class Band(val top: Float, val bottom: Float)

    /** Zotero's normalized (ascent, descent) in em, ascent > 0 > descent. */
    fun zoteroAscentDescent(fontAscent: Float, fontDescent: Float, capHeight: Float?): Pair<Float, Float> {
        var ascent = fontAscent
        var descent = if (fontDescent > 0f) -fontDescent else fontDescent
        if (ascent != 0f && descent != 0f && ascent.isFinite() && descent.isFinite()) {
            if (ascent > 1f) ascent = 0.75f
            if (descent < -0.5f) descent = -0.25f
        } else {
            ascent = 0.75f
            descent = -0.25f
        }
        if (capHeight != null && capHeight > 0f && capHeight < ascent) ascent = capHeight
        return ascent to descent
    }

    /**
     * Vertical box of one char the way Zotero computes it.
     * @param baseline baseline y (page space, y down)
     * @param quadTop/quadBottom MuPDF's glyph quad, from which the font's own
     *        ascent/descent at [fontSize] are recovered
     */
    fun zoteroCharBox(baseline: Float, fontSize: Float, quadTop: Float, quadBottom: Float, capHeight: Float?): Band? {
        if (!(fontSize > 0f) || !baseline.isFinite()) return null
        val fontAscent = (baseline - quadTop) / fontSize
        val fontDescent = (baseline - quadBottom) / fontSize
        val (ascent, descent) = zoteroAscentDescent(fontAscent, fontDescent, capHeight)
        return Band(top = baseline - fontSize * ascent, bottom = baseline - fontSize * descent)
    }

    /** Line rect = union of its chars' boxes (getRectsFromChars). */
    fun union(boxes: List<Band>): Band? =
        if (boxes.isEmpty()) null else Band(boxes.minOf { it.top }, boxes.maxOf { it.bottom })

    /**
     * pdf.js standard-14 cap heights (em), used when a font has no /CapHeight in
     * its descriptor. Symbol and ZapfDingbats have none.
     */
    fun standardCapHeight(fontName: String): Float? {
        val n = fontName.substringAfter('+').lowercase().replace("-", "").replace(",", "").replace(" ", "")
        return when {
            n.startsWith("courier") -> 0.562f
            n.startsWith("helvetica") || n.startsWith("arial") -> 0.718f
            n.startsWith("times") -> when {
                "bolditalic" in n -> 0.669f
                "bold" in n -> 0.676f
                "italic" in n -> 0.653f
                else -> 0.662f
            }
            else -> null
        }
    }

    /**
     * Display-time alignment of a stored highlight rect to the text line it
     * covers: the line whose horizontal span overlaps the rect and whose box
     * contains the rect's vertical center (closest center if several). Returns
     * that line's Zotero band, or null to draw the rect as stored (no text there,
     * e.g. a highlight over an image). Page space, y down.
     */
    fun alignToLine(left: Float, top: Float, right: Float, bottom: Float, lines: Map<LineKey, Band>): Band? {
        val center = (top + bottom) / 2f
        var best: Band? = null
        var bestDistance = Float.MAX_VALUE
        for ((line, band) in lines) {
            if (line.x1 <= left || line.x0 >= right) continue
            if (center < line.y0 || center > line.y1) continue
            val d = kotlin.math.abs((line.y0 + line.y1) / 2f - center)
            if (d < bestDistance) { bestDistance = d; best = band }
        }
        return best
    }

    /** Font names compared without the "ABCDEF+" subset tag, case-insensitively. */
    fun normalizeFontName(name: String): String = name.substringAfter('+').lowercase()
}
