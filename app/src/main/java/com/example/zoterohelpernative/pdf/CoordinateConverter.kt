package com.example.zoterohelpernative.pdf

import android.graphics.RectF

/**
 * Utility to convert between Android device screen coordinates (or MuPDF rendered page coordinates)
 * and Zotero's internal PDF coordinate system.
 * 
 * Zotero stores annotations in the format:
 * {"pageIndex":0,"rects":[[x1,y1,x2,y2]]}
 * where coordinates are usually based on a 72 DPI standard PDF size.
 */
object CoordinateConverter {

    /**
     * Converts a Zotero rect [x1, y1, x2, y2] to an Android RectF
     * @param zoteroRect The array of 4 floats from Zotero JSON
     * @param pdfPageWidth The native width of the PDF page (e.g. from MuPDF)
     * @param pdfPageHeight The native height of the PDF page
     * @param displayWidth The actual rendering width on the screen
     * @param displayHeight The actual rendering height on the screen
     */
    fun zoteroToAndroid(
        zoteroRect: FloatArray,
        pdfPageWidth: Float,
        pdfPageHeight: Float,
        displayWidth: Float,
        displayHeight: Float
    ): RectF {
        if (zoteroRect.size != 4) return RectF()

        val x1 = zoteroRect[0]
        val y1 = zoteroRect[1]
        val x2 = zoteroRect[2]
        val y2 = zoteroRect[3]

        // Calculate scaling factors
        val scaleX = displayWidth / pdfPageWidth
        val scaleY = displayHeight / pdfPageHeight

        // Depending on Zotero's exact coordinate origin (Bottom-Left vs Top-Left), 
        // we might need to invert Y. Typically PDF coordinates are Bottom-Left.
        // Assuming Bottom-Left for standard PDF:
        val invertedY1 = pdfPageHeight - y2
        val invertedY2 = pdfPageHeight - y1

        return RectF(
            x1 * scaleX,
            invertedY1 * scaleY,
            x2 * scaleX,
            invertedY2 * scaleY
        )
    }

    /**
     * Converts an Android screen RectF back to Zotero format array [x1, y1, x2, y2]
     */
    fun androidToZotero(
        screenRect: RectF,
        pdfPageWidth: Float,
        pdfPageHeight: Float,
        displayWidth: Float,
        displayHeight: Float
    ): FloatArray {
        val scaleX = pdfPageWidth / displayWidth
        val scaleY = pdfPageHeight / displayHeight

        val pdfX1 = screenRect.left * scaleX
        val pdfX2 = screenRect.right * scaleX
        
        val pdfY1 = screenRect.top * scaleY
        val pdfY2 = screenRect.bottom * scaleY

        // Invert Y back to Bottom-Left
        val zoteroY1 = pdfPageHeight - pdfY2
        val zoteroY2 = pdfPageHeight - pdfY1

        return floatArrayOf(pdfX1, zoteroY1, pdfX2, zoteroY2)
    }
}
