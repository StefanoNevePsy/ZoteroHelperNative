package com.example.zoterohelpernative.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderBudgetTest {

    private val a4w = 595f
    private val a4h = 842f

    private fun pixels(scale: Float) = (a4w * scale).toDouble() * (a4h * scale)

    @Test
    fun drawsAtOneAndAHalfTimesTheScreenWhenAffordable() {
        // 2000x1200 tablet held vertically: the page fits the 1200px width
        val scale = RenderBudget.scaleFor(a4w, a4h, viewWidth = 1200, viewHeight = 2000)
        val fit = 1200f / a4w
        assertEquals(fit * 1.5f, scale, 0.001f)
    }

    @Test
    fun neverCostsMorePixelsThanTheOldFixedScale() {
        for ((w, h) in listOf(1600 to 2560, 2560 to 1600, 1200 to 2000, 2000 to 1200, 1440 to 3200, 3000 to 2000)) {
            val scale = RenderBudget.scaleFor(a4w, a4h, w, h)
            assertTrue("$w x $h: ${pixels(scale)} px", pixels(scale) <= RenderBudget.MAX_PIXELS * 1.001)
            assertTrue("$w x $h: scale $scale above the old 3.5", scale <= 3.5f)
        }
    }

    @Test
    fun largePortraitTabletIsCappedNotOverspent() {
        // 2560x1600 vertical: 1.5x would be 8.2 MP (31 MB); capped to the old 6.1 MP
        val scale = RenderBudget.scaleFor(a4w, a4h, viewWidth = 1600, viewHeight = 2560)
        val fit = 1600f / a4w
        assertTrue(scale < fit * 1.5f)
        assertTrue("still sharper than 1:1 (got ${scale / fit}x)", scale / fit > 1.25f)
        assertEquals(RenderBudget.MAX_PIXELS.toDouble(), pixels(scale), RenderBudget.MAX_PIXELS * 0.01)
    }

    @Test
    fun landscapeUsesFarLessMemoryThanBefore() {
        val scale = RenderBudget.scaleFor(a4w, a4h, viewWidth = 2560, viewHeight = 1600)
        assertTrue(pixels(scale) < RenderBudget.MAX_PIXELS * 0.75)
    }

    @Test
    fun degenerateSizesFallBackSafely() {
        assertEquals(1f, RenderBudget.scaleFor(0f, a4h, 1200, 2000))
        assertEquals(1f, RenderBudget.scaleFor(a4w, a4h, 0, 0))
    }

    @Test
    fun aRotatedScreenNeedsARedraw() {
        val portrait = RenderBudget.scaleFor(a4w, a4h, 1200, 2000)
        val landscape = RenderBudget.scaleFor(a4w, a4h, 2000, 1200)
        assertTrue(RenderBudget.isReusable(cached = portrait, wanted = landscape)) // sharper is fine
        assertFalse(RenderBudget.isReusable(cached = landscape, wanted = portrait)) // blurrier isn't
    }

    @Test
    fun cacheKeepsTheMostRecentlyUsedPages() {
        val cache = PageCache<String>(capacity = 3)
        cache.put(1, "p1"); cache.put(2, "p2"); cache.put(3, "p3")
        cache[1]                 // touch page 1: page 2 is now the oldest
        cache.put(4, "p4")
        assertNull(cache[2])
        assertEquals(setOf(1, 3, 4), cache.pages)
        assertEquals(3, cache.size)
    }
}
