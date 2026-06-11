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
