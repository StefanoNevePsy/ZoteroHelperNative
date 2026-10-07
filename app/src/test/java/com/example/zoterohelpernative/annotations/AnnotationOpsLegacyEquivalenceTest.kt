package com.example.zoterohelpernative.annotations

import com.example.zoterohelpernative.data.ItemData
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Differential test for the refactor: the merge and erase logic used to live
 * inline in ReaderViewModel, built on org.json. The functions below are that
 * code, copied verbatim minus the state/sync side effects, and serve as the
 * reference. AnnotationOps must produce the same result on thousands of random
 * scenarios built to overlap often.
 */
class AnnotationOpsLegacyEquivalenceTest {

    // ---- reference: previous ReaderViewModel.addAnnotation merge core -----------

    private fun legacyMerge(annotations: List<ItemData>, annotation: ItemData): Pair<List<ItemData>, String?> {
        val newAnnotations = annotations.toMutableList()
        var mergedIntoKey: String? = null
        if (annotation.annotationType == "highlight") {
            try {
                val newJson = org.json.JSONObject(annotation.annotationPosition)
                val newPageIndex = newJson.getInt("pageIndex")
                val newRects = newJson.getJSONArray("rects")
                for (i in newAnnotations.indices) {
                    val existing = newAnnotations[i]
                    if (existing.annotationType == "highlight" && existing.annotationColor == annotation.annotationColor) {
                        val existingJson = org.json.JSONObject(existing.annotationPosition)
                        if (existingJson.getInt("pageIndex") == newPageIndex) {
                            val existingRects = existingJson.getJSONArray("rects")
                            var overlaps = false
                            for (j in 0 until newRects.length()) {
                                val nRect = newRects.getJSONArray(j)
                                val nl = nRect.getDouble(0); val nt = nRect.getDouble(1)
                                val nr = nRect.getDouble(2); val nb = nRect.getDouble(3)
                                for (k in 0 until existingRects.length()) {
                                    val eRect = existingRects.getJSONArray(k)
                                    val el = eRect.getDouble(0); val et = eRect.getDouble(1)
                                    val er = eRect.getDouble(2); val eb = eRect.getDouble(3)
                                    val interLeft = maxOf(nl, el); val interTop = maxOf(nt, et)
                                    val interRight = minOf(nr, er); val interBottom = minOf(nb, eb)
                                    if (interLeft < interRight + 5.0 && interTop < interBottom + 5.0) { overlaps = true; break }
                                }
                                if (overlaps) break
                            }
                            if (overlaps) {
                                val mergedArray = org.json.JSONArray()
                                val allRects = mutableListOf<org.json.JSONArray>()
                                for (j in 0 until existingRects.length()) allRects.add(existingRects.getJSONArray(j))
                                for (j in 0 until newRects.length()) allRects.add(newRects.getJSONArray(j))
                                allRects.sortWith(Comparator { a, b ->
                                    val yA = a.getDouble(1); val yB = b.getDouble(1)
                                    if (kotlin.math.abs(yA - yB) > 5.0) yB.compareTo(yA) else a.getDouble(0).compareTo(b.getDouble(0))
                                })
                                for (r in allRects) mergedArray.put(r)
                                existingJson.put("rects", mergedArray)
                                val oldText = existing.annotationText ?: ""
                                val newText = annotation.annotationText ?: ""
                                val combinedText = if (oldText.isBlank()) newText else if (newText.isBlank()) oldText else {
                                    val oldFirstRect = existingRects.getJSONArray(0)
                                    val newFirstRect = newRects.getJSONArray(0)
                                    val oldY = oldFirstRect.getDouble(1); val newY = newFirstRect.getDouble(1)
                                    val oldX = oldFirstRect.getDouble(0); val newX = newFirstRect.getDouble(0)
                                    val newIsFirst = if (kotlin.math.abs(oldY - newY) > 5.0) newY > oldY else newX < oldX
                                    val firstText = if (newIsFirst) newText else oldText
                                    val secondText = if (newIsFirst) oldText else newText
                                    var maxOverlap = 0
                                    val minLen = minOf(firstText.length, secondText.length)
                                    for (len in 1..minLen) {
                                        if (firstText.endsWith(secondText.substring(0, len), ignoreCase = true)) maxOverlap = len
                                    }
                                    if (maxOverlap > 0) firstText + secondText.substring(maxOverlap)
                                    else if (firstText.endsWith(" ") || secondText.startsWith(" ") || firstText.endsWith("-")) {
                                        if (firstText.endsWith("-")) firstText.dropLast(1) + secondText else firstText + secondText
                                    } else firstText + secondText
                                }
                                val updatedAnn = existing.copy(annotationPosition = existingJson.toString(), annotationText = combinedText)
                                newAnnotations[i] = updatedAnn
                                mergedIntoKey = updatedAnn.key
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        if (mergedIntoKey == null) newAnnotations.add(annotation)
        return newAnnotations to mergedIntoKey
    }

    // ---- reference: previous ReaderViewModel.eraseAnnotationsIntersecting core ---

    private fun legacyErase(annotations: List<ItemData>, nativeRects: List<PdfRect>, pageIndex: Int): Pair<List<ItemData>, Set<String>> {
        val currentAnnotations = annotations.toMutableList()
        val removed = mutableSetOf<String>()
        for (i in currentAnnotations.indices) {
            val ann = currentAnnotations[i]
            if ((ann.annotationType == "highlight" || ann.annotationType == "underline") && ann.annotationPosition != null) {
                try {
                    val annJson = org.json.JSONObject(ann.annotationPosition)
                    if (annJson.getInt("pageIndex") != pageIndex) continue
                    val existingRects = annJson.getJSONArray("rects")
                    val newRects = org.json.JSONArray()
                    var overlapsAny = false
                    for (k in 0 until existingRects.length()) {
                        val eRect = existingRects.getJSONArray(k)
                        val el = eRect.getDouble(0).toFloat(); val et = eRect.getDouble(1).toFloat()
                        val er = eRect.getDouble(2).toFloat(); val eb = eRect.getDouble(3).toFloat()
                        var overlapsWithEraser = false
                        for (nRect in nativeRects) {
                            val interLeft = maxOf(nRect.left, el); val interTop = maxOf(nRect.top, et)
                            val interRight = minOf(nRect.right, er); val interBottom = minOf(nRect.bottom, eb)
                            if (interLeft < interRight && interTop < interBottom) { overlapsWithEraser = true; break }
                        }
                        if (overlapsWithEraser) overlapsAny = true else newRects.put(eRect)
                    }
                    if (overlapsAny) {
                        if (newRects.length() == 0) {
                            currentAnnotations[i] = ann.copy(annotationType = "DELETED_MARKER")
                            removed += ann.key
                        } else {
                            annJson.put("rects", newRects)
                            currentAnnotations[i] = ann.copy(annotationPosition = annJson.toString())
                        }
                    }
                } catch (e: Exception) { }
            }
        }
        currentAnnotations.removeAll { it.annotationType == "DELETED_MARKER" }
        return currentAnnotations to removed
    }

    // ---- random scenarios built to collide ------------------------------------

    private val words = listOf("the ", "quick ", "brown", "brown fox", "fox ", "develop-", "ment", "E", "ducators", " jumps", "")

    private fun randomAnnotation(random: Random, key: String): ItemData {
        val baseY = 600f + 13f * random.nextInt(4)                // few lines → frequent overlaps
        val rects = (1..random.nextInt(1, 4)).map {
            val y = baseY - 13f * random.nextInt(3)
            val x1 = 10f + 40f * random.nextInt(5)
            PdfRect(x1, y, x1 + 30f + 50f * random.nextInt(4), y + 12f)
        }
        return AnnotationOps.buildHighlight(
            parentItemKey = "PARENT01",
            pageIndex = random.nextInt(2),
            pageHeight = 800f,
            rects = rects,
            text = words[random.nextInt(words.size)] + words[random.nextInt(words.size)],
            colorHex = if (random.nextBoolean()) "#ffd400" else "#ff6666",
            annotationType = if (random.nextInt(5) == 0) "underline" else "highlight",
            key = key
        )
    }

    private fun rects(ann: ItemData): List<List<Double>> =
        JsonParser.parseString(ann.annotationPosition).asJsonObject.getAsJsonArray("rects")
            .map { r -> r.asJsonArray.map { it.asDouble } }

    private fun assertSame(expected: List<ItemData>, actual: List<ItemData>, scenario: Int) {
        assertEquals("scenario $scenario: keys", expected.map { it.key }, actual.map { it.key })
        expected.zip(actual).forEach { (e, a) ->
            assertEquals("scenario $scenario: text of ${e.key}", e.annotationText, a.annotationText)
            assertEquals("scenario $scenario: rects of ${e.key}", rects(e), rects(a))
            assertEquals("scenario $scenario: version of ${e.key}", e.version, a.version)
        }
    }

    @Test
    fun mergeMatchesThePreviousImplementation() {
        val random = Random(20261007)
        var merges = 0
        repeat(3000) { scenario ->
            val existing = (0 until random.nextInt(0, 6)).map { randomAnnotation(random, "E%07d".format(it)) }
            val incoming = randomAnnotation(random, "NEW00000")

            val (legacyList, legacyKey) = legacyMerge(existing, incoming)
            val result = AnnotationOps.mergeOrAppend(existing, incoming)

            assertEquals("scenario $scenario: merged into", legacyKey, result.mergedIntoKey)
            assertSame(legacyList, result.annotations, scenario)
            if (legacyKey != null) merges++
        }
        assertTrue("the scenarios must actually exercise merging (got $merges)", merges > 500)
    }

    @Test
    fun eraseMatchesThePreviousImplementation() {
        val random = Random(7)
        var erasures = 0
        repeat(3000) { scenario ->
            val annotations = (0 until random.nextInt(1, 6)).map { randomAnnotation(random, "E%07d".format(it)) }
            val eraser = (1..random.nextInt(1, 4)).map {
                val x = 10f + 25f * random.nextInt(10)
                val y = 595f + 13f * random.nextInt(5)
                PdfRect(x, y, x + 8f, y + 8f)
            }
            val page = random.nextInt(2)

            val (legacyList, legacyRemoved) = legacyErase(annotations, eraser, page)
            val result = AnnotationOps.erase(annotations, eraser, page)

            assertEquals("scenario $scenario: removed", legacyRemoved, result.removed.map { it.key }.toSet())
            assertSame(legacyList, result.annotations, scenario)
            if (result.changed) erasures++
        }
        assertTrue("the scenarios must actually exercise erasing (got $erasures)", erasures > 500)
    }
}
