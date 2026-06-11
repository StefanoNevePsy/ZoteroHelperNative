package com.example.zoterohelpernative.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wrapper around the Artifex MuPDF (Fitz) library for loading PDFs
 * and rendering pages to Android Bitmaps for Jetpack Compose.
 */
class PdfEngine {

    private var document: Document? = null
    var pageCount: Int = 0
        private set

    suspend fun loadDocument(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            document = Document.openDocument(file.absolutePath)
            pageCount = document?.countPages() ?: 0
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun renderPage(pageIndex: Int, scale: Float = 1.0f): PageRenderResult? = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        var page: Page? = null
        try {
            page = doc.loadPage(pageIndex)
            val bounds: Rect = page.bounds
            
            // Native PDF dimensions (usually 72 dpi)
            val nativeWidth = bounds.x1 - bounds.x0
            val nativeHeight = bounds.y1 - bounds.y0

            // Apply scale for rendering resolution (e.g., 3.5x for retina/high-res screens)
            val renderScale = scale * 3.5f 
            // Translate the CTM so the top-left of the bounds maps to (0,0) in the bitmap
            val ctm = Matrix(renderScale, 0f, 0f, renderScale, -bounds.x0 * renderScale, -bounds.y0 * renderScale)

            val bbox: Rect = page.bounds.transform(ctm)
            val width = (bbox.x1 - bbox.x0).toInt()
            val height = (bbox.y1 - bbox.y0).toInt()

            if (width <= 0 || height <= 0) return@withContext null

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            val dev = AndroidDrawDevice(bitmap, 0, 0, 0, 0, width, height)
            page.run(dev, ctm, null)
            dev.close()

            // Extract text with bounding boxes
            val structuredText = page.toStructuredText("preserve-whitespace")
            val textBlocks = mutableListOf<TextRect>()
            
            if (structuredText != null) {
                // We must traverse blocks -> lines -> chars
                // However, Fitz Android wrapper usually only gives us blocks/lines.
                // We'll extract what we can to feed the React-like logic.
                // Note: The actual Fitz API might vary slightly, we use typical block extraction.
                // Assuming we can just store the structuredText for later parsing or parse it here.
            }

            return@withContext PageRenderResult(
                bitmap = bitmap,
                nativeWidth = nativeWidth,
                nativeHeight = nativeHeight,
                nativeBoundsLeft = bounds.x0,
                nativeBoundsTop = bounds.y0,
                structuredText = structuredText
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            page?.destroy()
        }
    }

    suspend fun renderViewport(pageIndex: Int, viewportRect: RectF, bitmapWidth: Int, bitmapHeight: Int): Bitmap? = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        var page: Page? = null
        try {
            page = doc.loadPage(pageIndex)
            val bounds = page.bounds
            
            // Calculate scale to map viewportRect to bitmap size
            val scaleX = bitmapWidth / viewportRect.width()
            val scaleY = bitmapHeight / viewportRect.height()
            
            // We want to translate the viewport's top-left to (0,0) in the bitmap.
            // But we must apply translation to the native coordinates *before* scaling, 
            // or apply the scaled translation.
            // CTM = Scale * Translate(-viewportRect.left, -viewportRect.top)
            val ctm = Matrix(scaleX, 0f, 0f, scaleY, -viewportRect.left * scaleX, -viewportRect.top * scaleY)

            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            val dev = AndroidDrawDevice(bitmap, 0, 0, 0, 0, bitmapWidth, bitmapHeight)
            page.run(dev, ctm, null)
            dev.close()
            return@withContext bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            page?.destroy()
        }
    }

    /**
     * Extracts the plain text of the whole document (used as context for the AI chat).
     * Stops once [maxChars] is reached to keep request payloads bounded.
     */
    suspend fun extractAllText(maxChars: Int = 120_000): String = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext ""
        val sb = StringBuilder()

        for (pageIndex in 0 until pageCount) {
            if (sb.length >= maxChars) break
            var page: Page? = null
            try {
                page = doc.loadPage(pageIndex)
                val structuredText = page.toStructuredText("preserve-whitespace") ?: continue
                sb.append("\n\n[Pagina ${pageIndex + 1}]\n")
                for (block in structuredText.blocks) {
                    for (line in block.lines) {
                        var lastRight = -1f
                        for (char in line.chars) {
                            val q = char.quad.toRect()
                            if (lastRight != -1f && q.x0 - lastRight > 2.0f) sb.append(' ')
                            sb.append(char.c.toChar())
                            lastRight = q.x1
                        }
                        sb.append('\n')
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                page?.destroy()
            }
        }

        return@withContext if (sb.length > maxChars) sb.substring(0, maxChars) else sb.toString()
    }

    /**
     * Finds a verbatim quote in the document and returns highlight-ready rects.
     * Search is whitespace-insensitive and tolerant of typographic quotes/dashes.
     * Rects use the same convention as manual highlights: y measured from the
     * bottom of the page (Zotero coordinates), one rect per text line.
     */
    suspend fun findText(query: String, preferredPage: Int? = null): TextSearchResult? {
        if (preferredPage != null) {
            findTextOnPage(preferredPage, query)?.let { return it }
            for (delta in listOf(-1, 1)) {
                findTextOnPage(preferredPage + delta, query)?.let { return it }
            }
        }
        for (p in 0 until pageCount) {
            if (preferredPage != null && kotlin.math.abs(p - preferredPage) <= 1) continue
            findTextOnPage(p, query)?.let { return it }
        }
        return null
    }

    suspend fun findTextOnPage(pageIndex: Int, query: String): TextSearchResult? = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null
        val normQuery = normalizeForSearch(query)
        if (normQuery.isBlank()) return@withContext null

        var page: Page? = null
        try {
            page = doc.loadPage(pageIndex)
            val bounds = page.bounds
            val pageHeight = bounds.y1 - bounds.y0
            val structuredText = page.toStructuredText("preserve-whitespace") ?: return@withContext null

            // Flatten chars; line breaks and wide gaps become whitespace separators
            val raw = StringBuilder()
            val charLines = mutableListOf<StructuredText.TextLine?>()
            val charQuads = mutableListOf<Rect?>()
            for (block in structuredText.blocks) {
                for (line in block.lines) {
                    var lastRight = -1f
                    for (char in line.chars) {
                        val q = char.quad.toRect()
                        if (lastRight != -1f && q.x0 - lastRight > 2.0f) {
                            raw.append(' '); charLines.add(null); charQuads.add(null)
                        }
                        raw.append(char.c.toChar()); charLines.add(line); charQuads.add(q)
                        lastRight = q.x1
                    }
                    raw.append(' '); charLines.add(null); charQuads.add(null)
                }
            }

            // Normalized text with a map back to raw char indices
            val norm = StringBuilder()
            val normToRaw = mutableListOf<Int>()
            var lastWasSpace = true
            for (i in raw.indices) {
                val c = normalizeChar(raw[i]) ?: continue
                if (c.isWhitespace()) {
                    if (!lastWasSpace) {
                        norm.append(' '); normToRaw.add(i); lastWasSpace = true
                    }
                } else {
                    norm.append(c); normToRaw.add(i); lastWasSpace = false
                }
            }

            val startNorm = norm.indexOf(normQuery)
            if (startNorm < 0) return@withContext null
            val rawStart = normToRaw[startNorm]
            val rawEnd = normToRaw[startNorm + normQuery.length - 1]

            // One rect per line, using the line bbox for height (same as manual highlights)
            val byLine = LinkedHashMap<StructuredText.TextLine, MutableList<Rect>>()
            val matched = StringBuilder()
            for (i in rawStart..rawEnd) {
                matched.append(raw[i])
                val line = charLines[i] ?: continue
                val q = charQuads[i] ?: continue
                byLine.getOrPut(line) { mutableListOf() }.add(q)
            }

            val rects = byLine.map { (line, quads) ->
                val left = quads.minOf { it.x0 }
                val right = quads.maxOf { it.x1 }
                val top = line.bbox.y0
                val bottom = line.bbox.y1
                RectF(left, pageHeight - bottom, right, pageHeight - top)
            }
            if (rects.isEmpty()) return@withContext null

            TextSearchResult(
                pageIndex = pageIndex,
                pageHeight = pageHeight,
                rects = rects,
                matchedText = matched.toString().trim().replace(Regex("\\s+"), " ")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            page?.destroy()
        }
    }

    // Returns null for chars to drop entirely (soft hyphen)
    private fun normalizeChar(c: Char): Char? = when (c) {
        '\u00AD' -> null                                      // soft hyphen
        '\u2018', '\u2019', '\u02BC' -> '\''                 // curly single quotes
        '\u201C', '\u201D' -> '"'                            // curly double quotes
        '\u2013', '\u2014', '\u2212' -> '-'                  // en/em dash, minus sign
        '\u00A0' -> ' '                                       // non-breaking space
        else -> c.lowercaseChar()
    }

    private fun normalizeForSearch(s: String): String {
        val sb = StringBuilder()
        var lastWasSpace = true
        for (ch in s) {
            val c = normalizeChar(ch) ?: continue
            if (c.isWhitespace()) {
                if (!lastWasSpace) { sb.append(' '); lastWasSpace = true }
            } else {
                sb.append(c); lastWasSpace = false
            }
        }
        return sb.toString().trim()
    }

    fun close() {
        document?.destroy()
        document = null
        pageCount = 0
    }
}

data class PageRenderResult(
    val bitmap: Bitmap,
    val nativeWidth: Float,
    val nativeHeight: Float,
    val nativeBoundsLeft: Float = 0f,
    val nativeBoundsTop: Float = 0f,
    val structuredText: StructuredText? = null
)

data class TextRect(
    val text: String,
    val rect: android.graphics.RectF
)

data class TextSearchResult(
    val pageIndex: Int, // 0-based
    val pageHeight: Float,
    val rects: List<RectF>, // Zotero coordinates: y from the bottom of the page
    val matchedText: String
)
