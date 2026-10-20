package com.example.zoterohelpernative.pdf

import com.artifex.mupdf.fitz.StructuredText

/**
 * Highlight bands for every line of a page (see [HighlightBand]). Neighbours
 * are the closest lines above/below that overlap horizontally, so the
 * overlap clamp works per column in multi-column layouts.
 */
fun StructuredText.highlightBands(): Map<StructuredText.TextLine, HighlightBand.Band> {
    class L(val line: StructuredText.TextLine, val top: Float, val bottom: Float, val x0: Float, val x1: Float)

    val lines = blocks.orEmpty().flatMap { it.lines.orEmpty().asList() }.mapNotNull { line ->
        val rects = line.chars.orEmpty()
            .filter { !it.isWhitespace && it.quad != null }
            .map { it.quad.toRect() }
        if (rects.isEmpty()) return@mapNotNull null
        L(
            line = line,
            top = HighlightBand.median(rects.map { it.y0 }),
            bottom = HighlightBand.median(rects.map { it.y1 }),
            x0 = line.bbox.x0,
            x1 = line.bbox.x1
        )
    }

    val result = HashMap<StructuredText.TextLine, HighlightBand.Band>(lines.size)
    for (l in lines) {
        val center = (l.top + l.bottom) / 2f
        var prevBottom: Float? = null
        var nextTop: Float? = null
        for (o in lines) {
            if (o === l) continue
            if (!(o.x0 < l.x1 && l.x0 < o.x1)) continue // other column
            val oCenter = (o.top + o.bottom) / 2f
            // Runs of the same visual line aren't neighbours
            if (kotlin.math.abs(oCenter - center) < (l.bottom - l.top) * 0.5f) continue
            if (oCenter < center && (prevBottom == null || o.bottom > prevBottom)) prevBottom = o.bottom
            if (oCenter > center && (nextTop == null || o.top < nextTop)) nextTop = o.top
        }
        result[l.line] = HighlightBand.compute(l.top, l.bottom, l.line.bbox.y0, l.line.bbox.y1, prevBottom, nextTop)
    }
    return result
}
