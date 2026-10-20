package com.example.zoterohelpernative.pdf

import com.artifex.mupdf.fitz.StructuredText

/**
 * Highlight bands for every line of a page (see [HighlightBand]). Neighbours
 * are the closest lines above/below that overlap horizontally, so the
 * pitch cap works per column in multi-column layouts.
 */
/**
 * Identifies a line by its position. StructuredText.getBlocks() builds new
 * TextLine objects on every call and they don't implement equals, so lines
 * from two calls never match as map keys: the bbox is the stable identity.
 */
data class LineKey(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

val StructuredText.TextLine.key: LineKey get() = LineKey(bbox.x0, bbox.y0, bbox.x1, bbox.y1)

fun StructuredText.highlightBands(): Map<LineKey, HighlightBand.Band> {
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

    val result = HashMap<LineKey, HighlightBand.Band>(lines.size)
    for (l in lines) {
        val center = (l.top + l.bottom) / 2f
        var prevCenter: Float? = null
        var nextCenter: Float? = null
        for (o in lines) {
            if (o === l) continue
            if (!(o.x0 < l.x1 && l.x0 < o.x1)) continue // other column
            val oCenter = (o.top + o.bottom) / 2f
            // Runs of the same visual line aren't neighbours
            if (kotlin.math.abs(oCenter - center) < (l.bottom - l.top) * 0.5f) continue
            if (oCenter < center && (prevCenter == null || oCenter > prevCenter)) prevCenter = oCenter
            if (oCenter > center && (nextCenter == null || oCenter < nextCenter)) nextCenter = oCenter
        }
        result[l.line.key] = HighlightBand.compute(l.top, l.bottom, l.line.bbox.y0, l.line.bbox.y1, prevCenter, nextCenter)
    }
    return result
}
