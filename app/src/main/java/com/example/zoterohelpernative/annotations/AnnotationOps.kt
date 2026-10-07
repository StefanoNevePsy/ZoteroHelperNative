package com.example.zoterohelpernative.annotations

import com.example.zoterohelpernative.data.ItemData
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * A rectangle in Zotero PDF coordinates: points, y measured from the bottom of
 * the page, so [top] < [bottom] numerically ([x1, y1, x2, y2] in Zotero's JSON).
 */
data class PdfRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class MergeResult(
    val annotations: List<ItemData>,
    /** Key of the existing annotation the new one was merged into, or null if appended. */
    val mergedIntoKey: String?
) {
    val changedAnnotation: ItemData
        get() = annotations.first { it.key == mergedIntoKey } // only valid when merged
}

data class EraseResult(
    val annotations: List<ItemData>,
    /** Annotations the eraser removed entirely. */
    val removed: List<ItemData>,
    /** Annotations that lost some of their rects but still exist. */
    val modified: List<ItemData>
) {
    val changed: Boolean get() = removed.isNotEmpty() || modified.isNotEmpty()
}

data class UndoPlan(
    /** The annotation list to show after undoing. */
    val restored: List<ItemData>,
    /** Annotations the undone action had added: they must be deleted. */
    val toDelete: List<ItemData>,
    /** Restored annotations whose content differs from what's live: push them. */
    val toSync: List<ItemData>
)

/**
 * Pure annotation logic used by the reader: no Android types, no I/O, so all
 * of it is covered by unit tests. Positions are parsed with Gson rather than
 * org.json, which is only a stub outside a device.
 */
object AnnotationOps {

    private const val SAME_LINE_TOLERANCE = 5.0

    // ---- creation --------------------------------------------------------

    /**
     * Zotero object keys: 8 chars from this alphabet (no 0, 1, O to avoid
     * ambiguity). The API rejects items whose key has any other format.
     */
    private const val KEY_ALPHABET = "23456789ABCDEFGHIJKLMNPQRSTUVWXYZ"

    fun generateZoteroKey(random: Random = Random.Default): String =
        (1..8).map { KEY_ALPHABET[random.nextInt(KEY_ALPHABET.length)] }.joinToString("")

    /** Rects in Zotero coordinates (y from the page bottom), as annotationPosition expects. */
    fun buildHighlight(
        parentItemKey: String,
        pageIndex: Int,
        pageHeight: Float,
        rects: List<PdfRect>,
        text: String,
        colorHex: String,
        comment: String? = null,
        annotationType: String = "highlight",
        key: String = generateZoteroKey()
    ): ItemData {
        require(rects.isNotEmpty()) { "a highlight needs at least one rect" }
        val rectsJson = rects.joinToString(",") { "[${it.left},${it.top},${it.right},${it.bottom}]" }
        val positionJson = "{\"pageIndex\":$pageIndex,\"rects\":[$rectsJson]}"
        // Zotero PDF sort index: pageIndex|charOffset|topOffset. Locale.ROOT keeps
        // ASCII digits: some locales format %d with other numerals, which Zotero rejects.
        val topOffset = (pageHeight - rects.first().top).toInt().coerceIn(0, 99999)
        val sortIndex = String.format(Locale.ROOT, "%05d|%06d|%05d", pageIndex, 0, topOffset)
        return ItemData(
            key = key,
            version = 0,
            itemType = "annotation",
            parentItem = parentItemKey,
            annotationType = annotationType,
            annotationText = text,
            annotationComment = comment ?: "",
            annotationColor = colorHex,
            annotationPosition = positionJson,
            annotationPageLabel = (pageIndex + 1).toString(),
            annotationSortIndex = sortIndex,
            tags = emptyList()
        )
    }

    // ---- merging ---------------------------------------------------------

    /**
     * Adds [incoming] to [annotations]. A highlight that overlaps (or touches,
     * within 5pt) an existing highlight of the same color on the same page is
     * merged into it — rects combined in reading order, text joined — instead
     * of creating a second annotation.
     */
    fun mergeOrAppend(annotations: List<ItemData>, incoming: ItemData): MergeResult {
        if (incoming.annotationType == "highlight") {
            val newPos = parsePosition(incoming.annotationPosition)
            if (newPos != null) {
                for ((i, existing) in annotations.withIndex()) {
                    if (existing.annotationType != "highlight" || existing.annotationColor != incoming.annotationColor) continue
                    // An existing annotation with a malformed position is skipped,
                    // not allowed to abort the whole merge
                    val oldPos = parsePosition(existing.annotationPosition) ?: continue
                    if (oldPos.pageIndex != newPos.pageIndex) continue
                    if (!anyOverlap(newPos.rects, oldPos.rects)) continue

                    val combined = (oldPos.rectElements + newPos.rectElements).sortedWith(readingOrder)
                    val merged = existing.copy(
                        annotationPosition = withRects(oldPos.json, combined),
                        annotationText = joinHighlightTexts(
                            oldText = existing.annotationText ?: "",
                            newText = incoming.annotationText ?: "",
                            oldFirst = oldPos.rects.first(),
                            newFirst = newPos.rects.first()
                        )
                    )
                    val result = annotations.toMutableList().also { it[i] = merged }
                    return MergeResult(result, merged.key)
                }
            }
        }
        return MergeResult(annotations + incoming, null)
    }

    private fun anyOverlap(a: List<DoubleArray>, b: List<DoubleArray>): Boolean =
        a.any { n ->
            b.any { e ->
                val interLeft = maxOf(n[0], e[0])
                val interTop = maxOf(n[1], e[1])
                val interRight = minOf(n[2], e[2])
                val interBottom = minOf(n[3], e[3])
                interLeft < interRight + SAME_LINE_TOLERANCE && interTop < interBottom + SAME_LINE_TOLERANCE
            }
        }

    /** Top-to-bottom (y grows upward, so descending y), then left-to-right on the same line. */
    private val readingOrder = Comparator<com.google.gson.JsonElement> { a, b ->
        val ra = a.asJsonArray
        val rb = b.asJsonArray
        val yA = ra[1].asDouble
        val yB = rb[1].asDouble
        if (abs(yA - yB) > SAME_LINE_TOLERANCE) yB.compareTo(yA) else ra[0].asDouble.compareTo(rb[0].asDouble)
    }

    /**
     * Joins the text of two merged highlights in reading order. If the end of
     * the first repeats the start of the second (selections that overlap), the
     * repeated part is kept once; a trailing hyphen is treated as a word break.
     */
    fun joinHighlightTexts(oldText: String, newText: String, oldFirst: DoubleArray, newFirst: DoubleArray): String {
        if (oldText.isBlank()) return newText
        if (newText.isBlank()) return oldText

        val newIsFirst = if (abs(oldFirst[1] - newFirst[1]) > SAME_LINE_TOLERANCE) {
            newFirst[1] > oldFirst[1] // y from the bottom: higher y is earlier on the page
        } else {
            newFirst[0] < oldFirst[0]
        }
        val first = if (newIsFirst) newText else oldText
        val second = if (newIsFirst) oldText else newText

        var maxOverlap = 0
        for (len in 1..minOf(first.length, second.length)) {
            if (first.endsWith(second.substring(0, len), ignoreCase = true)) maxOverlap = len
        }
        return when {
            maxOverlap > 0 -> first + second.substring(maxOverlap)
            first.endsWith("-") -> first.dropLast(1) + second
            // Zotero extracts whole words, so adjacent fragments ("E" + "ducators")
            // belong to the same word: join without a space
            else -> first + second
        }
    }

    // ---- erasing ---------------------------------------------------------

    /**
     * Removes from every highlight/underline on [pageIndex] the rects the
     * eraser strokes intersect. Annotations left with no rects are removed.
     */
    fun erase(annotations: List<ItemData>, eraser: List<PdfRect>, pageIndex: Int): EraseResult {
        val removed = mutableListOf<ItemData>()
        val modified = mutableListOf<ItemData>()
        val result = mutableListOf<ItemData>()

        for (ann in annotations) {
            val isMark = ann.annotationType == "highlight" || ann.annotationType == "underline"
            val pos = if (isMark) parsePosition(ann.annotationPosition) else null
            if (pos == null || pos.pageIndex != pageIndex) {
                result += ann
                continue
            }
            val kept = pos.rectElements.filterIndexed { index, _ ->
                val r = pos.rects[index]
                eraser.none { e ->
                    val interLeft = maxOf(e.left.toDouble(), r[0])
                    val interTop = maxOf(e.top.toDouble(), r[1])
                    val interRight = minOf(e.right.toDouble(), r[2])
                    val interBottom = minOf(e.bottom.toDouble(), r[3])
                    interLeft < interRight && interTop < interBottom
                }
            }
            when {
                kept.size == pos.rectElements.size -> result += ann
                kept.isEmpty() -> removed += ann
                else -> {
                    val updated = ann.copy(annotationPosition = withRects(pos.json, kept))
                    modified += updated
                    result += updated
                }
            }
        }
        return EraseResult(result, removed, modified)
    }

    // ---- undo ------------------------------------------------------------

    /**
     * Plans how to bring the server back to [snapshot] from [current]:
     * annotations added since must be deleted; annotations deleted since are
     * recreated (version reset to 0); modified ones are pushed back with the
     * latest known version, otherwise the server answers 412.
     */
    fun planUndo(snapshot: List<ItemData>, current: List<ItemData>): UndoPlan {
        val snapshotKeys = snapshot.map { it.key }.toSet()
        val currentByKey = current.associateBy { it.key }

        val restored = snapshot.map { ann ->
            val live = currentByKey[ann.key]
            when {
                live == null && ann.version != 0L -> ann.copy(version = 0)
                live != null -> ann.copy(version = live.version)
                else -> ann
            }
        }
        val toSync = restored.filter { ann ->
            val live = currentByKey[ann.key]
            live == null || live.copy(version = 0) != ann.copy(version = 0)
        }
        return UndoPlan(
            restored = restored,
            toDelete = current.filter { it.key !in snapshotKeys },
            toSync = toSync
        )
    }

    // ---- tags and export -------------------------------------------------

    private val defaultTags = listOf("Important", "To Read", "Methodology", "Check reference")

    /** Tags used most recently first, padded with a few defaults, at most 10. */
    fun recentTags(annotations: List<ItemData>): List<String> {
        val used = annotations.flatMap { it.tags?.map { t -> t.tag } ?: emptyList() }.reversed().distinct()
        return (used + defaultTags).distinct().take(10)
    }

    fun allTags(annotations: List<ItemData>, libraryTags: List<String>): List<String> {
        val used = annotations.flatMap { it.tags?.map { t -> t.tag } ?: emptyList() }
        return (used + libraryTags + defaultTags).distinct().sorted()
    }

    /** Highlights and underlines as Markdown, grouped by page in reading order. */
    fun exportMarkdown(annotations: List<ItemData>): String {
        val marks = annotations
            .filter { it.annotationType == "highlight" || it.annotationType == "underline" }
            .sortedBy { it.annotationSortIndex ?: "" }

        return buildString {
            append("# Annotazioni\n")
            var lastPage: String? = null
            for (ann in marks) {
                val page = ann.annotationPageLabel ?: "?"
                if (page != lastPage) {
                    append("\n## Pagina $page\n\n")
                    lastPage = page
                }
                val text = ann.annotationText?.trim().orEmpty().replace("\n", " ")
                if (text.isNotEmpty()) append("> $text\n")
                ann.annotationComment?.takeIf { it.isNotBlank() }?.let { append("\n$it\n") }
                ann.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    append("\nTags: ${tags.joinToString(", ") { "#${it.tag.replace(' ', '-')}" }}\n")
                }
                append("\n")
            }
        }
    }

    // ---- position JSON ---------------------------------------------------

    private class Position(
        val json: JsonObject,
        val pageIndex: Int,
        val rectElements: List<com.google.gson.JsonElement>,
        val rects: List<DoubleArray>
    )

    /** Null for missing or malformed positions (and positions without rects). */
    private fun parsePosition(raw: String?): Position? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JsonParser.parseString(raw).asJsonObject
            val pageIndex = json.get("pageIndex").asInt
            val elements = json.getAsJsonArray("rects").toList()
            if (elements.isEmpty()) return null
            val rects = elements.map { el ->
                val a = el.asJsonArray
                doubleArrayOf(a[0].asDouble, a[1].asDouble, a[2].asDouble, a[3].asDouble)
            }
            Position(json, pageIndex, elements, rects)
        } catch (e: Exception) {
            null
        }
    }

    /** Same position object (other fields preserved) with its rects replaced. */
    private fun withRects(position: JsonObject, rects: List<com.google.gson.JsonElement>): String {
        val copy = position.deepCopy()
        copy.add("rects", JsonArray().also { array -> rects.forEach { array.add(it) } })
        return copy.toString()
    }
}
