package com.example.zoterohelpernative.annotations

import com.example.zoterohelpernative.data.ItemData
import com.example.zoterohelpernative.data.ZoteroTag
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

class AnnotationOpsTest {

    private val yellow = "#ffd400"
    private val red = "#ff6666"

    /** A highlight on [page] with one rect per [rects] entry (Zotero coords, y up). */
    private fun highlight(
        key: String,
        rects: List<PdfRect>,
        text: String = "",
        color: String = yellow,
        page: Int = 0,
        type: String = "highlight",
        version: Long = 0
    ) = AnnotationOps.buildHighlight(
        parentItemKey = "PARENT01",
        pageIndex = page,
        pageHeight = 800f,
        rects = rects,
        text = text,
        colorHex = color,
        annotationType = type,
        key = key
    ).copy(version = version)

    /** One text line at height [y] (bottom edge) spanning [x1]..[x2]. */
    private fun line(x1: Float, x2: Float, y: Float) = PdfRect(x1, y, x2, y + 12f)

    private fun rectsOf(ann: ItemData): List<List<Double>> =
        JsonParser.parseString(ann.annotationPosition).asJsonObject.getAsJsonArray("rects")
            .map { r -> r.asJsonArray.map { it.asDouble } }

    // ---- keys and creation --------------------------------------------------

    @Test
    fun zoteroKeysUseTheAcceptedFormat() {
        val random = Random(1)
        repeat(200) {
            val key = AnnotationOps.generateZoteroKey(random)
            assertTrue("bad key $key", key.matches(Regex("[23456789ABCDEFGHIJKLMNPQRSTUVWXYZ]{8}")))
        }
    }

    @Test
    fun sortIndexUsesAsciiDigitsWhateverTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            // Arabic numerals here would make Zotero reject the annotation
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val ann = highlight("K0000001", listOf(line(10f, 100f, 700f)), page = 3)
            assertEquals("00003|000000|00100", ann.annotationSortIndex)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun highlightPositionIsZoteroShaped() {
        val ann = highlight("K0000001", listOf(line(10f, 100f, 700f)), page = 2)
        val pos = JsonParser.parseString(ann.annotationPosition).asJsonObject
        assertEquals(2, pos.get("pageIndex").asInt)
        assertEquals(listOf(listOf(10.0, 700.0, 100.0, 712.0)), rectsOf(ann))
        assertEquals("3", ann.annotationPageLabel)
        assertEquals(0L, ann.version)
    }

    // ---- merging ------------------------------------------------------------

    @Test
    fun overlappingSameColorHighlightsMergeIntoOne() {
        val first = highlight("K0000001", listOf(line(10f, 200f, 700f)), text = "the quick brown")
        val second = highlight("K0000002", listOf(line(150f, 300f, 700f)), text = "brown fox")

        val result = AnnotationOps.mergeOrAppend(listOf(first), second)

        assertEquals(1, result.annotations.size)
        assertEquals("K0000001", result.mergedIntoKey)
        assertEquals("the quick brown fox", result.annotations[0].annotationText)
        assertEquals(2, rectsOf(result.annotations[0]).size)
    }

    @Test
    fun mergedRectsAreInReadingOrder() {
        // Existing highlight is the lower line; the new one is the line above it
        val lower = highlight("K0000001", listOf(line(10f, 300f, 680f)), text = "second line")
        val upper = highlight("K0000002", listOf(line(10f, 300f, 693f)), text = "first line ")

        val merged = AnnotationOps.mergeOrAppend(listOf(lower), upper).annotations.single()

        val tops = rectsOf(merged).map { it[1] }
        assertEquals("top line first", listOf(693.0, 680.0), tops)
        assertEquals("first line second line", merged.annotationText)
    }

    @Test
    fun differentColorPageOrTypeIsNeverMerged() {
        val existing = highlight("K0000001", listOf(line(10f, 200f, 700f)))
        val sameSpot = listOf(line(10f, 200f, 700f))

        assertNull(AnnotationOps.mergeOrAppend(listOf(existing), highlight("K2", sameSpot, color = red)).mergedIntoKey)
        assertNull(AnnotationOps.mergeOrAppend(listOf(existing), highlight("K3", sameSpot, page = 1)).mergedIntoKey)
        assertNull(AnnotationOps.mergeOrAppend(listOf(existing), highlight("K4", sameSpot, type = "underline")).mergedIntoKey)
        assertEquals(2, AnnotationOps.mergeOrAppend(listOf(existing), highlight("K2", sameSpot, color = red)).annotations.size)
    }

    @Test
    fun distantHighlightsStaySeparate() {
        val top = highlight("K0000001", listOf(line(10f, 200f, 700f)))
        val farBelow = highlight("K0000002", listOf(line(10f, 200f, 300f)))
        assertNull(AnnotationOps.mergeOrAppend(listOf(top), farBelow).mergedIntoKey)
    }

    @Test
    fun aMalformedAnnotationDoesNotBlockMerging() {
        val broken = ItemData(key = "BROKEN01", itemType = "annotation", annotationType = "highlight",
            annotationColor = yellow, annotationPosition = "{not json")
        val good = highlight("K0000001", listOf(line(10f, 200f, 700f)), text = "abc")
        val incoming = highlight("K0000002", listOf(line(150f, 300f, 700f)), text = "def")

        val result = AnnotationOps.mergeOrAppend(listOf(broken, good), incoming)

        assertEquals("K0000001", result.mergedIntoKey)
        assertEquals(2, result.annotations.size)
    }

    @Test
    fun mergingKeepsOtherPositionFields() {
        val existing = highlight("K0000001", listOf(line(10f, 200f, 700f)), text = "a")
            .copy(annotationPosition = "{\"pageIndex\":0,\"rects\":[[10,700,200,712]],\"rotation\":90}")
        val incoming = highlight("K0000002", listOf(line(150f, 300f, 700f)), text = "b")

        val merged = AnnotationOps.mergeOrAppend(listOf(existing), incoming).annotations.single()

        assertEquals(90, JsonParser.parseString(merged.annotationPosition).asJsonObject.get("rotation").asInt)
    }

    // ---- joining text -------------------------------------------------------

    private val upperLine = doubleArrayOf(10.0, 700.0, 200.0, 712.0)
    private val lowerLine = doubleArrayOf(10.0, 680.0, 200.0, 692.0)
    private val leftOnLine = doubleArrayOf(10.0, 700.0, 80.0, 712.0)
    private val rightOnLine = doubleArrayOf(90.0, 700.0, 200.0, 712.0)

    @Test
    fun textIsJoinedInReadingOrderRegardlessOfSelectionOrder() {
        assertEquals("Alpha Beta", AnnotationOps.joinHighlightTexts("Beta", "Alpha ", oldFirst = lowerLine, newFirst = upperLine))
        assertEquals("Alpha Beta", AnnotationOps.joinHighlightTexts("Alpha ", "Beta", oldFirst = upperLine, newFirst = lowerLine))
        assertEquals("left right", AnnotationOps.joinHighlightTexts(" right", "left", oldFirst = rightOnLine, newFirst = leftOnLine))
    }

    @Test
    fun repeatedTextFromOverlappingSelectionsIsKeptOnce() {
        assertEquals("cognitive behavioural therapy",
            AnnotationOps.joinHighlightTexts("cognitive behav", "behavioural therapy", oldFirst = leftOnLine, newFirst = rightOnLine))
    }

    @Test
    fun hyphenatedWordsAreRejoined() {
        assertEquals("development",
            AnnotationOps.joinHighlightTexts("develop-", "ment", oldFirst = upperLine, newFirst = lowerLine))
    }

    @Test
    fun blankSideKeepsTheOther() {
        assertEquals("kept", AnnotationOps.joinHighlightTexts("", "kept", upperLine, lowerLine))
        assertEquals("kept", AnnotationOps.joinHighlightTexts("kept", "  ", upperLine, lowerLine))
    }

    // ---- erasing ------------------------------------------------------------

    @Test
    fun eraserRemovesOnlyTheRectsItTouches() {
        val twoLines = highlight("K0000001", listOf(line(10f, 300f, 700f), line(10f, 300f, 680f)))

        val result = AnnotationOps.erase(listOf(twoLines), listOf(PdfRect(50f, 702f, 60f, 708f)), pageIndex = 0)

        assertTrue(result.changed)
        assertTrue(result.removed.isEmpty())
        assertEquals(listOf(listOf(10.0, 680.0, 300.0, 692.0)), rectsOf(result.annotations.single()))
        assertEquals("K0000001", result.modified.single().key)
    }

    @Test
    fun eraserRemovesAnnotationsLeftWithoutRects() {
        val oneLine = highlight("K0000001", listOf(line(10f, 300f, 700f)))
        val underline = highlight("K0000002", listOf(line(10f, 300f, 600f)), type = "underline")

        val result = AnnotationOps.erase(listOf(oneLine, underline),
            listOf(PdfRect(50f, 702f, 60f, 708f), PdfRect(50f, 602f, 60f, 608f)), pageIndex = 0)

        assertEquals(listOf("K0000001", "K0000002"), result.removed.map { it.key })
        assertTrue(result.annotations.isEmpty())
    }

    @Test
    fun eraserIgnoresOtherPagesAndMisses() {
        val onPageTwo = highlight("K0000001", listOf(line(10f, 300f, 700f)), page = 2)
        val result = AnnotationOps.erase(listOf(onPageTwo), listOf(PdfRect(50f, 702f, 60f, 708f)), pageIndex = 0)
        assertFalse(result.changed)
        assertEquals(listOf(onPageTwo), result.annotations)

        val miss = AnnotationOps.erase(listOf(onPageTwo), listOf(PdfRect(400f, 100f, 410f, 110f)), pageIndex = 2)
        assertFalse(miss.changed)
    }

    // ---- undo ---------------------------------------------------------------

    @Test
    fun undoingAnAdditionDeletesTheNewAnnotation() {
        val existing = highlight("K0000001", listOf(line(10f, 300f, 700f)), version = 5)
        val added = highlight("K0000002", listOf(line(10f, 300f, 600f)), version = 7)

        val plan = AnnotationOps.planUndo(snapshot = listOf(existing), current = listOf(existing, added))

        assertEquals(listOf("K0000002"), plan.toDelete.map { it.key })
        assertEquals(listOf(existing), plan.restored)
        assertTrue(plan.toSync.isEmpty())
    }

    @Test
    fun undoingADeletionRecreatesTheAnnotation() {
        val deleted = highlight("K0000001", listOf(line(10f, 300f, 700f)), version = 5)

        val plan = AnnotationOps.planUndo(snapshot = listOf(deleted), current = emptyList())

        assertEquals(0L, plan.restored.single().version) // version 0 = create again
        assertEquals(listOf("K0000001"), plan.toSync.map { it.key })
        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun undoingAnEditPushesOldContentWithTheLiveVersion() {
        val before = highlight("K0000001", listOf(line(10f, 300f, 700f)), color = yellow, version = 5)
        val after = before.copy(annotationColor = red, version = 9)

        val plan = AnnotationOps.planUndo(snapshot = listOf(before), current = listOf(after))

        val pushed = plan.toSync.single()
        assertEquals(yellow, pushed.annotationColor)
        assertEquals("live version, or the server answers 412", 9L, pushed.version)
    }

    // ---- tags and export ----------------------------------------------------

    @Test
    fun recentTagsPutTheLatestFirstAndCapAtTen() {
        val anns = listOf(
            highlight("K1", listOf(line(0f, 1f, 1f))).copy(tags = listOf(ZoteroTag("old"))),
            highlight("K2", listOf(line(0f, 1f, 1f))).copy(tags = listOf(ZoteroTag("new")))
        )
        val recent = AnnotationOps.recentTags(anns)
        assertEquals(listOf("new", "old"), recent.take(2))
        assertTrue(recent.size <= 10)
    }

    @Test
    fun markdownExportGroupsByPageInOrder() {
        val p2 = highlight("K1", listOf(line(10f, 100f, 500f)), text = "second", page = 1)
            .copy(annotationComment = "a note", tags = listOf(ZoteroTag("key idea")))
        val p1 = highlight("K2", listOf(line(10f, 100f, 500f)), text = "first", page = 0)

        val md = AnnotationOps.exportMarkdown(listOf(p2, p1))

        assertTrue(md.indexOf("## Pagina 1") < md.indexOf("## Pagina 2"))
        assertTrue(md.contains("> first"))
        assertTrue(md.contains("a note"))
        assertTrue(md.contains("#key-idea"))
    }
}
