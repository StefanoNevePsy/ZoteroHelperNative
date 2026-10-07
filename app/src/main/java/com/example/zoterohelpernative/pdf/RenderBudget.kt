package com.example.zoterohelpernative.pdf

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Picks the resolution a page is drawn at.
 *
 * The page is shown "fit" inside the reader, so the pixels it covers on screen
 * are pageSize × fitScale. Drawing it at [OVERSAMPLE] times that keeps text
 * crisp (and slightly sharper during a pinch, before the zoomed tile arrives).
 *
 * The previous pipeline drew every page at a fixed 3.5× the PDF size: about
 * 6.1 megapixels (23 MB) for an A4 page whatever the screen. That is the
 * ceiling here — [MAX_PIXELS] — so a page never costs more memory than it did
 * before; on most tablets it costs much less. Only a very large portrait
 * screen (e.g. 2560×1600 held vertically) reaches the cap, at about 1.3×.
 */
object RenderBudget {
    const val OVERSAMPLE = 1.5f
    const val MAX_PIXELS = 2082L * 2947L // what 3.5× an A4 page used to cost
    const val MAX_SCALE = 3.5f            // the old fixed scale, as an upper bound

    /** Pixels per PDF point for a [pageWidth]×[pageHeight] page shown in a [viewWidth]×[viewHeight] view. */
    fun scaleFor(pageWidth: Float, pageHeight: Float, viewWidth: Int, viewHeight: Int): Float {
        if (pageWidth <= 0f || pageHeight <= 0f || viewWidth <= 0 || viewHeight <= 0) return 1f
        val fit = min(viewWidth / pageWidth, viewHeight / pageHeight)
        var scale = min(fit * OVERSAMPLE, MAX_SCALE)
        val pixels = (pageWidth * scale).toDouble() * (pageHeight * scale).toDouble()
        if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels).toFloat()
        return scale
    }

    /** Whether a page drawn at [cached] is close enough to [wanted] to reuse (e.g. after rotation it isn't). */
    fun isReusable(cached: Float, wanted: Float): Boolean = cached >= wanted * 0.9f
}

/**
 * Least-recently-used map of rendered pages. Holds the current page and its
 * neighbours so turning back and forth doesn't redraw anything.
 */
class PageCache<T>(private val capacity: Int) {
    private val entries = LinkedHashMap<Int, T>(capacity + 1, 0.75f, /* accessOrder = */ true)

    operator fun get(page: Int): T? = entries[page]

    fun put(page: Int, value: T) {
        entries[page] = value
        while (entries.size > capacity) {
            entries.remove(entries.keys.first())
        }
    }

    fun clear() = entries.clear()

    val size: Int get() = entries.size
    val pages: Set<Int> get() = entries.keys.toSet()
}
