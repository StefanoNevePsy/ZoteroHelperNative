package com.example.zoterohelpernative.pdf

import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFObject
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.StructuredTextWalker

/**
 * Identifies a line by its position. StructuredText.getBlocks() builds new
 * TextLine objects on every call and they don't implement equals, so lines
 * from two calls never match as map keys: the bbox is the stable identity.
 */
data class LineKey(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

val StructuredText.TextLine.key: LineKey get() = LineKey(bbox.x0, bbox.y0, bbox.x1, bbox.y1)

/**
 * Zotero-style highlight band for every text line of a page (see
 * [HighlightBand]): each char's box from its baseline, size and the font's
 * metrics with Zotero's cap-height rule, unioned per line.
 *
 * @param capHeights cap height in em by [HighlightBand.normalizeFontName], read
 *        from the page's font descriptors ([readCapHeights])
 */
fun StructuredText.zoteroLineBands(capHeights: Map<String, Float>): Map<LineKey, HighlightBand.Band> {
    val result = HashMap<LineKey, HighlightBand.Band>()
    var lineKey: LineKey? = null
    val boxes = ArrayList<HighlightBand.Band>()

    walk(object : StructuredTextWalker {
        override fun onImageBlock(bbox: Rect?, transform: Matrix?, image: Image?) {}
        override fun beginTextBlock(bbox: Rect?) {}
        override fun endTextBlock() {}

        override fun beginLine(bbox: Rect, wmode: Int, dir: Point?) {
            lineKey = LineKey(bbox.x0, bbox.y0, bbox.x1, bbox.y1)
            boxes.clear()
        }

        override fun onChar(c: Int, origin: Point?, font: Font?, size: Float, quad: Quad?) {
            if (origin == null || quad == null || Character.isWhitespace(c)) return
            val name = font?.name.orEmpty()
            val capHeight = capHeights[HighlightBand.normalizeFontName(name)] ?: HighlightBand.standardCapHeight(name)
            val r = quad.toRect()
            HighlightBand.zoteroCharBox(origin.y, size, r.y0, r.y1, capHeight)?.let { boxes += it }
        }

        override fun endLine() {
            val key = lineKey ?: return
            HighlightBand.union(boxes)?.let { result[key] = it }
            lineKey = null
        }
    })
    return result
}

/**
 * Cap heights (em) of the fonts a PDF page uses, from their /FontDescriptor
 * /CapHeight, keyed by normalized font name. Covers the page resources (also
 * inherited from the page tree), Type0 descendant fonts and fonts of form
 * XObjects one level down.
 */
fun readCapHeights(pageObject: PDFObject?): Map<String, Float> {
    val out = HashMap<String, Float>()
    if (pageObject == null) return out

    fun readFonts(resources: PDFObject?) {
        val fonts = resources?.get("Font")?.resolve() ?: return
        if (!fonts.isDictionary) return
        for (key in fonts) {
            val font = fonts.get(key)?.resolve() ?: continue
            if (!font.isDictionary) continue
            val name = font.get("BaseFont")?.takeIf { it.isName }?.asName() ?: continue
            var descriptor = font.get("FontDescriptor")?.resolve()
            if (descriptor == null || descriptor.isNull) {
                val descendant = font.get("DescendantFonts")?.resolve()?.takeIf { it.isArray && it.size() > 0 }?.get(0)?.resolve()
                descriptor = descendant?.get("FontDescriptor")?.resolve()
            }
            val cap = descriptor?.get("CapHeight")?.takeIf { it.isNumber }?.asFloat() ?: continue
            if (cap > 0f) out[HighlightBand.normalizeFontName(name)] = cap / 1000f
        }
    }

    // Resources may be inherited from an ancestor in the page tree
    var node: PDFObject? = pageObject
    var resources: PDFObject? = null
    var depth = 0
    while (node != null && !node.isNull && depth < 32) {
        val r = node.get("Resources")?.resolve()
        if (r != null && !r.isNull) { resources = r; break }
        node = node.get("Parent")?.resolve()
        depth++
    }
    readFonts(resources)
    val xobjects = resources?.get("XObject")?.resolve()
    if (xobjects != null && xobjects.isDictionary) {
        for (key in xobjects) {
            readFonts(xobjects.get(key)?.resolve()?.get("Resources")?.resolve())
        }
    }
    return out
}
