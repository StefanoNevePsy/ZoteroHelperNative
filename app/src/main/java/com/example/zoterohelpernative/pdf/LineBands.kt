package com.example.zoterohelpernative.pdf

import com.artifex.mupdf.fitz.StructuredText

/**
 * Font-sized highlight bands for every line of a page (see [HighlightBand]).
 * Neighbours are the closest lines above/below that overlap horizontally, so
 * the clamp works per column in multi-column layouts.
 */
fun StructuredText.highlightBands(): Map<StructuredText.TextLine, HighlightBand.Band> {
    class L(val line: StructuredText.TextLine, val baseline: Float, val glyph: Float, val x0: Float, val x1: Float)

    val lines = blocks.orEmpty().flatMap { it.lines.orEmpty().asList() }.mapNotNull { line ->
        val chars = line.chars.orEmpty().filter { !it.isWhitespace && it.origin != null && it.quad != null }
        if (chars.isEmpty()) return@mapNotNull null
        val rects = chars.map { it.quad.toRect() }
        L(
            line = line,
            baseline = HighlightBand.median(chars.map { it.origin.y }),
            glyph = HighlightBand.median(rects.map { it.y1 - it.y0 }),
            x0 = line.bbox.x0,
            x1 = line.bbox.x1
        )
    }

    val result = HashMap<StructuredText.TextLine, HighlightBand.Band>(lines.size)
    for (l in lines) {
        var prev: Float? = null
        var next: Float? = null
        for (o in lines) {
            if (o === l) continue
            val overlapsHorizontally = o.x0 < l.x1 && l.x0 < o.x1
            if (!overlapsHorizontally) continue
            // Ignore lines on the same baseline (e.g. split runs of one line)
            if (kotlin.math.abs(o.baseline - l.baseline) < l.glyph * 0.3f) continue
            if (o.baseline < l.baseline && (prev == null || o.baseline > prev)) prev = o.baseline
            if (o.baseline > l.baseline && (next == null || o.baseline < next)) next = o.baseline
        }
        result[l.line] = HighlightBand.compute(l.baseline, l.glyph, l.line.bbox.y0, l.line.bbox.y1, prev, next)
    }
    return result
}
