package com.example.zoterohelpernative.pdf

/**
 * Reading themes for the PDF page, in the spirit of Zotero's reader themes.
 *
 * Each theme is a background/foreground pair the page is mapped onto: white
 * paper becomes [background], black text becomes [foreground] and everything
 * in between is interpolated per channel, so figures keep their colors
 * (tinted toward the theme) instead of turning grey.
 *
 * Dark themes first apply a *hue-preserving* inversion — invert, then rotate
 * the hue by 180° — so a red chart stays red and a blue logo stays blue while
 * lightness flips. A plain inversion (what the app did before) turns red into
 * cyan and blue into orange.
 *
 * This file is pure math (no Android types) so it can be unit tested.
 */
data class PdfTheme(
    val id: String,
    val label: String,
    val background: Int, // 0xRRGGBB
    val foreground: Int, // 0xRRGGBB
    val isDark: Boolean
)

object PdfThemes {

    /** Follows the app: the dark theme on dark app themes, the original page otherwise. */
    const val AUTO = "auto"

    val Original = PdfTheme("light", "Originale", 0xFFFFFF, 0x000000, isDark = false)

    /** Ids kept from the previous version so saved preferences still resolve. */
    val all = listOf(
        Original,
        PdfTheme("sepia", "Seppia", 0xF4ECD8, 0x5B4636, isDark = false),
        PdfTheme("paper", "Carta", 0xF6F0E6, 0x1A2131, isDark = false),
        PdfTheme("dark", "Scuro", 0x1E1F22, 0xDADADA, isDark = true),
        PdfTheme("oled", "Nero (AMOLED)", 0x000000, 0xC8C8C8, isDark = true),
        PdfTheme("nordic", "Nordico", 0x2E3440, 0xE5E9F0, isDark = true),
        PdfTheme("forest", "Foresta", 0x1E2A22, 0xD3E0D0, isDark = true)
    )

    fun resolve(id: String?, appIsDark: Boolean): PdfTheme = when (id) {
        AUTO -> if (appIsDark) byId("dark") else Original
        else -> byId(id)
    }

    fun byId(id: String?): PdfTheme = all.firstOrNull { it.id == id } ?: Original

    /**
     * 4x5 color matrix in Android/Compose layout (rows R,G,B,A; offsets in the
     * 0..255 range), or null for the original page (no filter at all).
     */
    fun colorMatrix(theme: PdfTheme): FloatArray? {
        if (theme.id == Original.id) return null

        val bg = channels(theme.background)
        val fg = channels(theme.foreground)
        val m = FloatArray(20)
        m[18] = 1f // alpha untouched

        if (!theme.isDark) {
            // c' = fg + (bg - fg) * c / 255  → white→bg, black→fg
            for (c in 0..2) {
                m[c * 5 + c] = (bg[c] - fg[c]) / 255f
                m[c * 5 + 4] = fg[c]
            }
        } else {
            // s = H·(255 - c): hue-preserving inversion (H = hue rotation by 180°,
            // whose rows sum to 1, so H·255 = 255 on every channel).
            // c' = bg + (fg - bg) * s / 255  → white→bg, black→fg
            // ⇒ M = -diag(k)·H, offset = fg, with k = (fg - bg) / 255
            for (row in 0..2) {
                val k = (fg[row] - bg[row]) / 255f
                for (col in 0..2) m[row * 5 + col] = -k * HUE_ROTATE_180[row][col]
                m[row * 5 + 4] = fg[row]
            }
        }
        return m
    }

    /** Applies a matrix to an RGB triple with the same clamping the GPU does. */
    fun apply(matrix: FloatArray?, rgb: Int): Int {
        if (matrix == null) return rgb
        val c = channels(rgb)
        val out = IntArray(3) { row ->
            val v = matrix[row * 5] * c[0] + matrix[row * 5 + 1] * c[1] + matrix[row * 5 + 2] * c[2] + matrix[row * 5 + 4]
            v.coerceIn(0f, 255f).let { Math.round(it) }
        }
        return (out[0] shl 16) or (out[1] shl 8) or out[2]
    }

    private fun channels(rgb: Int) = floatArrayOf(
        ((rgb shr 16) and 0xFF).toFloat(),
        ((rgb shr 8) and 0xFF).toFloat(),
        (rgb and 0xFF).toFloat()
    )

    /** feColorMatrix hueRotate(180deg): luminance-weighted, rows sum to 1. */
    private val HUE_ROTATE_180 = arrayOf(
        floatArrayOf(-0.574f, 1.430f, 0.144f),
        floatArrayOf(0.426f, 0.430f, 0.144f),
        floatArrayOf(0.426f, 1.430f, -0.856f)
    )
}
