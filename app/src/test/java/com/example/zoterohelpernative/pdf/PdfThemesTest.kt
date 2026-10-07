package com.example.zoterohelpernative.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class PdfThemesTest {

    private val themed = PdfThemes.all.filter { it != PdfThemes.Original }

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    private fun assertColorNear(expected: Int, actual: Int, message: String) {
        val close = abs(r(expected) - r(actual)) <= 1 && abs(g(expected) - g(actual)) <= 1 && abs(b(expected) - b(actual)) <= 1
        assertTrue("$message: expected #%06X, got #%06X".format(expected, actual), close)
    }

    private fun luminance(c: Int): Double {
        fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(r(c)) + 0.7152 * lin(g(c)) + 0.0722 * lin(b(c))
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun originalPageIsNotFiltered() {
        assertNull(PdfThemes.colorMatrix(PdfThemes.Original))
    }

    @Test
    fun whitePaperBecomesThemeBackground() {
        for (theme in themed) {
            val out = PdfThemes.apply(PdfThemes.colorMatrix(theme), 0xFFFFFF)
            assertColorNear(theme.background, out, "${theme.id} paper")
        }
    }

    @Test
    fun blackTextBecomesThemeForeground() {
        for (theme in themed) {
            val out = PdfThemes.apply(PdfThemes.colorMatrix(theme), 0x000000)
            assertColorNear(theme.foreground, out, "${theme.id} text")
        }
    }

    @Test
    fun sepiaActuallyTintsThePage() {
        // The previous "photo sepia" matrix left white paper at (255,255,239)
        val out = PdfThemes.apply(PdfThemes.colorMatrix(PdfThemes.byId("sepia")), 0xFFFFFF)
        assertTrue("sepia paper should be visibly warm, got #%06X".format(out), r(out) - b(out) >= 20)
    }

    @Test
    fun darkThemesKeepTheHueOfFigures() {
        // A plain inversion turned red into cyan and blue into orange
        for (theme in themed.filter { it.isDark }) {
            val m = PdfThemes.colorMatrix(theme)
            val red = PdfThemes.apply(m, 0xCC2222)
            assertTrue("${theme.id}: red stays red, got #%06X".format(red), r(red) > g(red) && r(red) > b(red))
            val blue = PdfThemes.apply(m, 0x2244CC)
            assertTrue("${theme.id}: blue stays blue, got #%06X".format(blue), b(blue) > r(blue) && b(blue) > g(blue))
            val green = PdfThemes.apply(m, 0x22AA33)
            assertTrue("${theme.id}: green stays green, got #%06X".format(green), g(green) > r(green) && g(green) > b(green))
        }
    }

    @Test
    fun darkThemesInvertLightness() {
        for (theme in themed.filter { it.isDark }) {
            val m = PdfThemes.colorMatrix(theme)
            val lightGrey = PdfThemes.apply(m, 0xDDDDDD)
            val darkGrey = PdfThemes.apply(m, 0x333333)
            assertTrue("${theme.id}: light content should turn dark", luminance(lightGrey) < luminance(darkGrey))
        }
    }

    @Test
    fun everyThemeIsComfortableToRead() {
        // WCAG AAA (7:1) for long-form reading
        for (theme in themed) {
            val ratio = contrast(theme.foreground, theme.background)
            assertTrue("${theme.id}: text contrast %.2f:1 below 7:1".format(ratio), ratio >= 7.0)
        }
    }

    @Test
    fun autoFollowsTheAppAppearance() {
        assertEquals("dark", PdfThemes.resolve(PdfThemes.AUTO, appIsDark = true).id)
        assertEquals("light", PdfThemes.resolve(PdfThemes.AUTO, appIsDark = false).id)
    }

    @Test
    fun savedIdsFromThePreviousVersionStillResolve() {
        for (id in listOf("light", "dark", "sepia", "nordic", "oled", "forest")) {
            assertEquals(id, PdfThemes.resolve(id, appIsDark = false).id)
        }
        assertEquals("light", PdfThemes.resolve("unknown", appIsDark = true).id)
        assertEquals("light", PdfThemes.resolve(null, appIsDark = true).id)
    }
}
