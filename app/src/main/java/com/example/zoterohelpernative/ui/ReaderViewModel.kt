package com.example.zoterohelpernative.ui

import java.io.File
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zoterohelpernative.data.ItemData
import com.example.zoterohelpernative.data.SettingsRepository
import com.example.zoterohelpernative.data.WebDavClient
import com.example.zoterohelpernative.data.ZoteroApiService
import com.example.zoterohelpernative.theme.DEFAULT_PALETTES
import com.example.zoterohelpernative.data.Palette
import com.example.zoterohelpernative.data.MappedColor
import com.example.zoterohelpernative.theme.AccentSecondary
import com.example.zoterohelpernative.theme.ZoteroYellow
import com.example.zoterohelpernative.utils.ZipUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.Icons

enum class ActiveTool { HIGHLIGHTER, PEN, ERASER, SHAPE }
enum class ShapeType { RECTANGLE, ELLIPSE, POLYGON }

data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String,
    val isError: Boolean = false
)

data class ReaderState(
    val activeTool: ActiveTool = ActiveTool.HIGHLIGHTER,
    val activeColorHex: String = "#ffd400", // Zotero Hex
    val activePaletteId: String = "stabilo",
    val customPalettes: Map<String, Palette> = emptyMap(),
    val activeShapeType: ShapeType = ShapeType.RECTANGLE,
    val zoomLevel: Float = 1.0f,
    val panOffset: Offset = Offset.Zero,
    val currentPage: Int = 0,
    val numPages: Int = 1,
    val radialMenuOpen: Boolean = false,
    val radialMenuPosition: Offset = Offset.Zero,
    val radialMenuLevel: String = "root",
    val readerSidebarOpen: Boolean = false,
    val annotations: List<ItemData> = emptyList(),
    val isSyncing: Boolean = false,
    val docType: String = "pdf",
    val squaredHighlighter: Boolean = false,
    val snapToWord: Boolean = false,
    val isFullscreen: Boolean = false,
    val selectedAnnotationId: String? = null,
    val annotationPopupPosition: Offset = Offset.Zero,
    val allLibraryTags: List<String> = emptyList(),
    val fingerSelectionOnly: Boolean = false,
    val penHighlightOnly: Boolean = false,
    val toolIcons: Map<String, String> = emptyMap(),
    
    // PDF Render State
    val pageBitmap: android.graphics.Bitmap? = null,
    val pdfNativeWidth: Float = 0f,
    val pdfNativeHeight: Float = 0f,
    val pdfNativeBoundsLeft: Float = 0f,
    val pdfNativeBoundsTop: Float = 0f,
    val viewportBitmap: android.graphics.Bitmap? = null,
    val viewportRect: androidx.compose.ui.geometry.Rect? = null,
    val structuredText: com.artifex.mupdf.fitz.StructuredText? = null,
    
    val isLoadingPdf: Boolean = false,
    val pdfError: String? = null,
    val pdfTheme: String = "light",
    val syncError: String? = null,

    // AI Chat
    val chatMessages: List<ChatMessage> = emptyList(),
    val isChatSending: Boolean = false,
    val geminiKeySet: Boolean = false
)

class ReaderViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    
    val allPalettes: Map<String, Palette>
        get() = DEFAULT_PALETTES + _state.value.customPalettes

    val activePalette: Palette
        get() = allPalettes[_state.value.activePaletteId] ?: DEFAULT_PALETTES["stabilo"]!!

    init {
        viewModelScope.launch {
            settingsRepository.activePaletteId.collect { id ->
                _state.update { it.copy(activePaletteId = id ?: "stabilo") }
            }
        }
        viewModelScope.launch {
            settingsRepository.customPalettes.collect { palettes ->
                _state.update { it.copy(customPalettes = palettes) }
            }
        }
        viewModelScope.launch {
            settingsRepository.penHighlightOnly.collect { value ->
                _state.update { it.copy(penHighlightOnly = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.fingerSelectionOnly.collect { value ->
                _state.update { it.copy(fingerSelectionOnly = value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.pdfTheme.collect { theme ->
                if (theme != null) {
                    _state.update { it.copy(pdfTheme = theme) }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.toolIcons.collect { icons ->
                _state.update { it.copy(toolIcons = icons) }
            }
        }
        viewModelScope.launch {
            settingsRepository.geminiApiKey.collect { key ->
                _state.update { it.copy(geminiKeySet = !key.isNullOrBlank()) }
            }
        }
    }

    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.zotero.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ZoteroApiService::class.java)
    }
    private val webDavClient = WebDavClient()

    fun setActiveTool(tool: ActiveTool) {
        _state.update { it.copy(activeTool = tool) }
    }

    fun setActiveColor(zoteroHex: String) {
        _state.update { it.copy(activeColorHex = zoteroHex.lowercase()) }
    }

    fun getUiColorHex(zoteroHex: String): String {
        val normalized = zoteroHex.lowercase()
        // Legacy mapping for old Zotero colors if needed could go here, but normally Zotero gives #ffd400 etc.
        val legacyToZotero = mapOf(
            "#ffd6a5" to "#ffd400",
            "#ffadad" to "#ff6666",
            "#b9fbc0" to "#5fb236",
            "#a0c4ff" to "#2ea8e5",
            "#bdb2ff" to "#a28ae5"
        )
        val finalZoteroHex = legacyToZotero[normalized] ?: normalized
        
        val colorMatch = activePalette.colors.find { it.zoteroHex.lowercase() == finalZoteroHex }
        return colorMatch?.uiHex ?: finalZoteroHex
    }

    fun getUiColor(zoteroHex: String): Color {
        val hexStr = getUiColorHex(zoteroHex).replace("#", "")
        return if (hexStr.length == 6) {
            Color(android.graphics.Color.parseColor("#$hexStr"))
        } else {
            ZoteroYellow
        }
    }

    fun cyclePalette() {
        val keys = allPalettes.keys.toList()
        val currentIndex = keys.indexOf(_state.value.activePaletteId)
        val nextIndex = (currentIndex + 1) % keys.size
        val nextId = keys[nextIndex]
        
        viewModelScope.launch {
            settingsRepository.saveActivePaletteId(nextId)
        }
    }

    fun setActivePalette(id: String) {
        if (allPalettes.containsKey(id)) {
            viewModelScope.launch {
                settingsRepository.saveActivePaletteId(id)
            }
        }
    }

    fun toggleRadialMenu() {
        _state.update { it.copy(radialMenuOpen = !it.radialMenuOpen) }
    }

    fun setRadialMenuLevel(level: String) {
        _state.value = _state.value.copy(radialMenuLevel = level)
    }
    
    fun toggleSquaredHighlighter() {
        _state.value = _state.value.copy(squaredHighlighter = !_state.value.squaredHighlighter)
    }

    fun toggleSnapToWord() {
        _state.value = _state.value.copy(snapToWord = !_state.value.snapToWord)
    }

    fun toggleFullscreen() {
        _state.value = _state.value.copy(isFullscreen = !_state.value.isFullscreen)
    }

    fun zoomIn(centroid: Offset) {
        updatePanZoom(Offset.Zero, 1.2f, centroid)
    }

    fun zoomOut(centroid: Offset) {
        updatePanZoom(Offset.Zero, 0.833f, centroid)
    }

    fun fitPage() {
        _state.update { it.copy(zoomLevel = 1.0f, panOffset = Offset.Zero) }
    }

    fun updatePanZoom(panChange: Offset, zoomChange: Float, centroid: Offset) {
        _state.update { 
            val oldZoom = it.zoomLevel
            val newZoom = (oldZoom * zoomChange).coerceIn(0.5f, 5.0f)
            val actualZoomChange = newZoom / oldZoom
            
            val newPan = if (actualZoomChange == 1f) {
                it.panOffset + panChange
            } else {
                (it.panOffset - centroid) * actualZoomChange + centroid
            }
            
            it.copy(
                zoomLevel = newZoom,
                panOffset = newPan
            ) 
        }
    }

    fun cyclePdfTheme() {
        val themes = listOf("light", "dark", "sepia", "nordic", "oled", "forest")
        val currentIndex = themes.indexOf(_state.value.pdfTheme)
        val nextIndex = (currentIndex + 1) % themes.size
        val nextTheme = themes[nextIndex]
        _state.update { it.copy(pdfTheme = nextTheme) }
        viewModelScope.launch { settingsRepository.savePdfTheme(nextTheme) }
    }

    fun setPdfTheme(theme: String) {
        _state.update { it.copy(pdfTheme = theme) }
        viewModelScope.launch { settingsRepository.savePdfTheme(theme) }
    }

    fun updateToolIcon(toolName: String, iconName: String) {
        val currentIcons = _state.value.toolIcons.toMutableMap()
        currentIcons[toolName] = iconName
        _state.update { it.copy(toolIcons = currentIcons) }
        viewModelScope.launch { settingsRepository.saveToolIcons(currentIcons) }
    }

    fun selectAnnotation(id: String?, position: Offset = Offset.Zero) {
        _state.value = _state.value.copy(
            selectedAnnotationId = id,
            annotationPopupPosition = position
        )
    }

    private val undoStack = mutableListOf<List<ItemData>>()
    
    private fun pushUndoState() {
        undoStack.add(_state.value.annotations.toList())
        if (undoStack.size > 20) {
            undoStack.removeAt(0)
        }
    }

    fun undoLastAction() {
        if (undoStack.isNotEmpty()) {
            val prevState = undoStack.removeLast()
            _state.update { it.copy(annotations = prevState) }
            selectAnnotation(null)
        }
    }

    fun getRecentTags(): List<String> {
        val standard = listOf("Important", "To Read", "Methodology", "Check reference")
        val fromAnns = _state.value.annotations
            .flatMap { it.tags?.map { t -> t.tag } ?: emptyList() }
            .reversed()
            .distinct()
        return (fromAnns + standard).distinct().take(10)
    }
    
    fun getAllTags(): List<String> {
        val standard = listOf("Important", "To Read", "Methodology", "Check reference")
        val fromAnns = _state.value.annotations
            .flatMap { it.tags?.map { t -> t.tag } ?: emptyList() }
        val globalTags = _state.value.allLibraryTags
        return (fromAnns + globalTags + standard).distinct().sorted()
    }

    fun updateAnnotationColor(id: String, newColorHex: String) {
        val currentAnnotations = _state.value.annotations.toMutableList()
        val index = currentAnnotations.indexOfFirst { it.key == id }
        if (index != -1) {
            pushUndoState()
            val updated = currentAnnotations[index].copy(annotationColor = newColorHex)
            currentAnnotations[index] = updated
            _state.value = _state.value.copy(annotations = currentAnnotations)
            // Trigger a re-render by clearing selected annotation
            selectAnnotation(null)
            
            // Sync
            syncItemToZotero(updated)
        }
    }
    
    fun toggleAnnotationTag(annotationId: String, tag: com.example.zoterohelpernative.data.ZoteroTag) {
        val currentAnnotations = _state.value.annotations.toMutableList()
        val index = currentAnnotations.indexOfFirst { it.key == annotationId }
        if (index != -1) {
            val ann = currentAnnotations[index]
            val currentTags = ann.tags?.toMutableList() ?: mutableListOf()
            if (currentTags.any { it.tag == tag.tag }) {
                currentTags.removeAll { it.tag == tag.tag }
            } else {
                currentTags.add(tag)
            }
            pushUndoState()
            val updated = ann.copy(tags = currentTags)
            currentAnnotations[index] = updated
            _state.value = _state.value.copy(
                annotations = currentAnnotations,
                // A brand-new tag becomes immediately available for other annotations
                allLibraryTags = (_state.value.allLibraryTags + tag.tag).distinct()
            )

            // Sync
            syncItemToZotero(updated)
        }
    }

    fun closeRadialMenu() {
        _state.update { it.copy(radialMenuOpen = false) }
    }

    fun previousPage() {
        if (_state.value.currentPage > 0) {
            _state.update { it.copy(currentPage = it.currentPage - 1) }
            renderCurrentPage()
            persistCurrentPage()
        }
    }

    fun nextPage() {
        if (_state.value.currentPage < _state.value.numPages - 1) {
            _state.update { it.copy(currentPage = it.currentPage + 1) }
            renderCurrentPage()
            persistCurrentPage()
        }
    }

    private fun persistCurrentPage() {
        val key = currentAttachmentKey ?: return
        val page = _state.value.currentPage
        viewModelScope.launch {
            try {
                settingsRepository.saveLastReadPage(key, page)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleSidebar() {
        _state.update { it.copy(readerSidebarOpen = !it.readerSidebarOpen) }
    }

    fun addHighlight(parentItemKey: String, nativeRects: List<androidx.compose.ui.geometry.Rect>, extractedText: String) {
        if (nativeRects.isEmpty()) return
        val s = _state.value
        val newAnn = buildHighlightItem(
            parentItemKey = parentItemKey,
            pageIndex = s.currentPage,
            pageHeight = s.pdfNativeHeight,
            rects = nativeRects.map { android.graphics.RectF(it.left, it.top, it.right, it.bottom) },
            text = extractedText,
            colorHex = s.activeColorHex
        )
        addAnnotation(newAnn)
    }

    // rects in Zotero coordinates (y from page bottom), like annotationPosition expects
    private fun buildHighlightItem(
        parentItemKey: String,
        pageIndex: Int,
        pageHeight: Float,
        rects: List<android.graphics.RectF>,
        text: String,
        colorHex: String,
        comment: String? = null
    ): ItemData {
        val rectsJson = rects.joinToString(",") { "[${it.left},${it.top},${it.right},${it.bottom}]" }
        val positionJson = "{\"pageIndex\":$pageIndex,\"rects\":[$rectsJson]}"
        // Zotero PDF sort index format: pageIndex|charOffset|topOffset
        val topOffset = (pageHeight - rects.first().top).toInt().coerceIn(0, 99999)
        val sortIndexStr = String.format("%05d|%06d|%05d", pageIndex, 0, topOffset)
        return ItemData(
            key = generateZoteroKey(),
            version = 0,
            itemType = "annotation",
            parentItem = parentItemKey,
            annotationType = "highlight",
            annotationText = text,
            annotationComment = comment ?: "",
            annotationColor = colorHex,
            annotationPosition = positionJson,
            annotationPageLabel = (pageIndex + 1).toString(),
            annotationSortIndex = sortIndexStr,
            tags = emptyList()
        )
    }

    fun addAnnotation(annotation: ItemData) {
        val newAnnotations = _state.value.annotations.toMutableList()
        
        // Find if it overlaps with an existing annotation of the same color on the same page
        var merged = false
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
                            
                            // Check overlap
                            var overlaps = false
                            for (j in 0 until newRects.length()) {
                                val nRect = newRects.getJSONArray(j)
                                val nl = nRect.getDouble(0)
                                val nt = nRect.getDouble(1)
                                val nr = nRect.getDouble(2)
                                val nb = nRect.getDouble(3)
                                
                                for (k in 0 until existingRects.length()) {
                                    val eRect = existingRects.getJSONArray(k)
                                    val el = eRect.getDouble(0)
                                    val et = eRect.getDouble(1)
                                    val er = eRect.getDouble(2)
                                    val eb = eRect.getDouble(3)
                                    
                                    val interLeft = maxOf(nl, el)
                                    val interTop = maxOf(nt, et)
                                    val interRight = minOf(nr, er)
                                    val interBottom = minOf(nb, eb)
                                    
                                    if (interLeft < interRight + 5.0 && interTop < interBottom + 5.0) {
                                        overlaps = true
                                        break
                                    }
                                }
                                if (overlaps) break
                            }
                            
                            if (overlaps) {
                                // Merge them!
                                pushUndoState()
                                
                                // Collect all rects                                // Sort rectangles by reading order
                                val mergedArray = org.json.JSONArray()
                                val allRects = mutableListOf<org.json.JSONArray>()
                                for (j in 0 until existingRects.length()) allRects.add(existingRects.getJSONArray(j))
                                for (j in 0 until newRects.length()) allRects.add(newRects.getJSONArray(j))
                                
                                // Sort by top-to-bottom reading order (Y is from bottom, so sort descending)
                                allRects.sortWith(Comparator { a, b ->
                                    val yA = a.getDouble(1)
                                    val yB = b.getDouble(1)
                                    if (kotlin.math.abs(yA - yB) > 5.0) {
                                        yB.compareTo(yA) // Descending Y (higher Y = higher on page)
                                    } else {
                                        a.getDouble(0).compareTo(b.getDouble(0)) // Ascending X
                                    }
                                })
                                
                                for (r in allRects) mergedArray.put(r)
                                
                                existingJson.put("rects", mergedArray)
                                
                                // Combine text smartly using overlap detection AND geometric order
                                val oldText = existing.annotationText ?: ""
                                val newText = annotation.annotationText ?: ""
                                
                                val combinedText = if (oldText.isBlank()) {
                                    newText
                                } else if (newText.isBlank()) {
                                    oldText
                                } else {
                                    // Determine geometric order of texts
                                    val oldFirstRect = existingRects.getJSONArray(0)
                                    val newFirstRect = newRects.getJSONArray(0)
                                    val oldY = oldFirstRect.getDouble(1)
                                    val newY = newFirstRect.getDouble(1)
                                    val oldX = oldFirstRect.getDouble(0)
                                    val newX = newFirstRect.getDouble(0)
                                    
                                    val newIsFirst = if (kotlin.math.abs(oldY - newY) > 5.0) {
                                        newY > oldY // Y from bottom
                                    } else {
                                        newX < oldX
                                    }
                                    
                                    val firstText = if (newIsFirst) newText else oldText
                                    val secondText = if (newIsFirst) oldText else newText
                                    
                                    // Check for overlap: suffix of first matching prefix of second
                                    var maxOverlap = 0
                                    val minLen = minOf(firstText.length, secondText.length)
                                    for (len in 1..minLen) {
                                        if (firstText.endsWith(secondText.substring(0, len), ignoreCase = true)) {
                                            maxOverlap = len
                                        }
                                    }
                                    
                                    if (maxOverlap > 0) {
                                        firstText + secondText.substring(maxOverlap)
                                    } else {
                                        // If no overlap, concatenate. Add a space only if neither has trailing/leading space.
                                        if (firstText.endsWith(" ") || secondText.startsWith(" ") || firstText.endsWith("-")) {
                                            if (firstText.endsWith("-")) firstText.dropLast(1) + secondText
                                            else firstText + secondText
                                        } else {
                                            // Assume they are just adjacent words missing a space, or just parts of a word.
                                            // Usually, Zotero extracts full words. If it's a part of a word (e.g. "E" and "ducators"),
                                            // they should be joined WITHOUT space!
                                            firstText + secondText
                                        }
                                    }
                                }
                                
                                val updatedAnn = existing.copy(
                                    annotationPosition = existingJson.toString(),
                                    annotationText = combinedText
                                )
                                newAnnotations[i] = updatedAnn
                                _state.update { it.copy(annotations = newAnnotations) }
                                merged = true
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        
        if (!merged) {
            pushUndoState()
            newAnnotations.add(annotation)
            _state.update { it.copy(annotations = newAnnotations) }

            // Sync new annotation
            syncItemToZotero(annotation)
        } else {
            // Find the merged annotation to sync
            val mergedAnn = newAnnotations.find { it.key == annotation.key } 
                ?: newAnnotations.lastOrNull { it.annotationType == "highlight" && it.annotationColor == annotation.annotationColor }
            if (mergedAnn != null) {
                syncItemToZotero(mergedAnn)
            }
        }
    }

    fun deleteAnnotation(annotation: ItemData) {
        pushUndoState()
        val newAnnotations = _state.value.annotations.filter { it.key != annotation.key }
        _state.update { it.copy(annotations = newAnnotations) }

        // Delete from Zotero (version 0 = never synced, nothing to delete remotely)
        if (annotation.version != 0L && !annotation.key.startsWith("local_")) {
            deleteItemFromZotero(annotation.key, annotation.version)
        }
    }

    fun eraseAnnotationsIntersecting(nativeRects: List<androidx.compose.ui.geometry.Rect>, pageIndex: Int) {
        val currentAnnotations = _state.value.annotations.toMutableList()
        var changed = false

        for (i in currentAnnotations.indices) {
            val ann = currentAnnotations[i]
            if (ann.annotationType == "highlight" && ann.annotationPosition != null) {
                try {
                    val annJson = org.json.JSONObject(ann.annotationPosition)
                    if (annJson.getInt("pageIndex") != pageIndex) continue

                    val existingRects = annJson.getJSONArray("rects")
                    val newRects = org.json.JSONArray()
                    var overlapsAny = false

                    for (k in 0 until existingRects.length()) {
                        val eRect = existingRects.getJSONArray(k)
                        val el = eRect.getDouble(0).toFloat()
                        val et = eRect.getDouble(1).toFloat()
                        val er = eRect.getDouble(2).toFloat()
                        val eb = eRect.getDouble(3).toFloat()

                        var overlapsWithEraser = false
                        for (j in 0 until nativeRects.size) {
                            val nRect = nativeRects[j]
                            val interLeft = maxOf(nRect.left, el)
                            val interTop = maxOf(nRect.top, et)
                            val interRight = minOf(nRect.right, er)
                            val interBottom = minOf(nRect.bottom, eb)

                            if (interLeft < interRight && interTop < interBottom) {
                                overlapsWithEraser = true
                                break
                            }
                        }

                        if (overlapsWithEraser) {
                            overlapsAny = true
                        } else {
                            newRects.put(eRect)
                        }
                    }

                    if (overlapsAny) {
                        if (!changed) pushUndoState()
                        changed = true
                        
                        if (newRects.length() == 0) {
                            currentAnnotations[i] = ann.copy(annotationType = "DELETED_MARKER")
                            if (ann.version != 0L) {
                                deleteItemFromZotero(ann.key, ann.version)
                            }
                        } else {
                            annJson.put("rects", newRects)
                            val updatedAnn = ann.copy(annotationPosition = annJson.toString())
                            currentAnnotations[i] = updatedAnn
                            syncItemToZotero(updatedAnn)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        if (changed) {
            currentAnnotations.removeAll { it.annotationType == "DELETED_MARKER" }
            _state.value = _state.value.copy(annotations = currentAnnotations)
            selectAnnotation(null)
        }
    }

    // Zotero object keys: 8 chars from this alphabet (no 0, 1, O to avoid ambiguity).
    // The API rejects items whose key has any other format.
    private val zoteroKeyAlphabet = "23456789ABCDEFGHIJKLMNPQRSTUVWXYZ"

    fun generateZoteroKey(): String =
        (1..8).map { zoteroKeyAlphabet.random() }.joinToString("")

    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    private var syncErrorJob: kotlinx.coroutines.Job? = null

    private fun setSyncError(message: String) {
        _state.update { it.copy(syncError = message) }
        syncErrorJob?.cancel()
        syncErrorJob = viewModelScope.launch {
            kotlinx.coroutines.delay(8000)
            _state.update { it.copy(syncError = null) }
        }
    }

    private fun updateLocalAnnotation(key: String, transform: (ItemData) -> ItemData) {
        _state.update { s ->
            s.copy(annotations = s.annotations.map { if (it.key == key) transform(it) else it })
        }
    }

    // "annotation" items reject fields like title/collections: strip them before writing.
    private fun sanitizeForWrite(itemData: ItemData): ItemData =
        if (itemData.itemType == "annotation") itemData.copy(title = null, collections = null) else itemData

    private fun syncItemToZotero(itemData: ItemData) {
        viewModelScope.launch {
            syncMutex.withLock {
                try {
                    val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                    val userId = settingsRepository.zoteroUserId.firstOrNull()
                    if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty()) {
                        setSyncError("Credenziali Zotero mancanti: annotazione non salvata sul server.")
                        return@withLock
                    }

                    // Re-read from state: a previous sync may have assigned key/version
                    val current = _state.value.annotations.find { it.key == itemData.key } ?: itemData
                    val payload = sanitizeForWrite(current)

                    if (payload.version == 0L) {
                        val response = apiService.createItems(userId, apiKey, items = listOf(payload))
                        val body = response.body()
                        val created = body?.successful?.values?.firstOrNull()
                        when {
                            response.isSuccessful && created != null -> {
                                updateLocalAnnotation(current.key) { it.copy(key = created.key, version = created.version) }
                            }
                            else -> {
                                val reason = body?.failed?.values?.firstOrNull()?.message
                                    ?: "HTTP ${response.code()}"
                                setSyncError("Salvataggio annotazione fallito: $reason")
                            }
                        }
                    } else {
                        val response = apiService.updateItem(userId, payload.key, apiKey, itemData = payload)
                        if (response.isSuccessful) {
                            response.headers()["Last-Modified-Version"]?.toLongOrNull()?.let { newVersion ->
                                updateLocalAnnotation(payload.key) { it.copy(version = newVersion) }
                            }
                        } else {
                            setSyncError("Aggiornamento annotazione fallito: HTTP ${response.code()}")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    setSyncError("Errore di rete: annotazione non salvata (${e.localizedMessage ?: "sconosciuto"})")
                }
            }
        }
    }

    private fun deleteItemFromZotero(itemKey: String, version: Long) {
        viewModelScope.launch {
            syncMutex.withLock {
                try {
                    val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                    val userId = settingsRepository.zoteroUserId.firstOrNull()
                    if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty()) {
                        setSyncError("Credenziali Zotero mancanti: eliminazione non sincronizzata.")
                        return@withLock
                    }

                    val response = apiService.deleteItem(userId, itemKey, apiKey, version = version)
                    // 404 = already gone on server, treat as success
                    if (!response.isSuccessful && response.code() != 404) {
                        setSyncError("Eliminazione annotazione fallita: HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    setSyncError("Errore di rete durante l'eliminazione (${e.localizedMessage ?: "sconosciuto"})")
                }
            }
        }
    }

    private val pdfEngine = com.example.zoterohelpernative.pdf.PdfEngine()

    // ---- AI Chat (Gemini) ----

    private val geminiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.example.zoterohelpernative.data.GeminiApiService::class.java)
    }

    private var documentTextCache: String? = null
    private var currentAttachmentKey: String? = null

    fun clearChat() {
        _state.update { it.copy(chatMessages = emptyList()) }
    }

    private val highlightColorMap = mapOf(
        "yellow" to "#ffd400",
        "red" to "#ff6666",
        "green" to "#5fb236",
        "blue" to "#2ea8e5",
        "purple" to "#a28ae5",
        "magenta" to "#e56eee",
        "orange" to "#f19837",
        "gray" to "#aaaaaa"
    )

    private fun chatTools(): List<com.example.zoterohelpernative.data.GeminiTool> {
        return listOf(
            com.example.zoterohelpernative.data.GeminiTool(
                functionDeclarations = listOf(
                    com.example.zoterohelpernative.data.GeminiFunctionDeclaration(
                        name = "create_highlight",
                        description = "Crea un'evidenziazione permanente nel PDF su un passaggio del documento. " +
                            "Usala quando l'utente chiede di evidenziare, marcare o segnare passaggi del testo. " +
                            "Puoi chiamarla più volte per evidenziare più passaggi.",
                        parameters = com.example.zoterohelpernative.data.GeminiSchema(
                            type = "OBJECT",
                            properties = mapOf(
                                "quote" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "STRING",
                                    description = "Citazione ESATTA e contigua copiata letteralmente dal testo del documento, " +
                                        "tra 5 e 300 caratteri. Non parafrasare e non attraversare i marcatori [Pagina N]."
                                ),
                                "page" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "INTEGER",
                                    description = "Numero della pagina in cui si trova la citazione, come indicato dai marcatori [Pagina N]."
                                ),
                                "color" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "STRING",
                                    description = "Colore dell'evidenziazione (opzionale).",
                                    enum = highlightColorMap.keys.toList()
                                ),
                                "comment" to com.example.zoterohelpernative.data.GeminiSchema(
                                    type = "STRING",
                                    description = "Breve nota da allegare all'evidenziazione (opzionale)."
                                )
                            ),
                            required = listOf("quote", "page")
                        )
                    )
                )
            )
        )
    }

    private suspend fun executeCreateHighlight(args: Map<String, Any?>?): Map<String, Any?> {
        val quote = (args?.get("quote") as? String)?.trim()
        val pageNumber = (args?.get("page") as? Number)?.toInt()
        val colorName = (args?.get("color") as? String)?.lowercase()
        val comment = (args?.get("comment") as? String)?.takeIf { it.isNotBlank() }
        val parentKey = currentAttachmentKey

        if (quote.isNullOrBlank() || parentKey == null) {
            return mapOf("success" to false, "error" to "Parametro 'quote' mancante.")
        }

        val result = pdfEngine.findText(quote, preferredPage = pageNumber?.minus(1))
            ?: return mapOf(
                "success" to false,
                "error" to "Testo non trovato nel documento. Riprova con una citazione esatta più breve e senza omissioni."
            )

        val colorHex = highlightColorMap[colorName] ?: _state.value.activeColorHex
        val annotation = buildHighlightItem(
            parentItemKey = parentKey,
            pageIndex = result.pageIndex,
            pageHeight = result.pageHeight,
            rects = result.rects,
            text = result.matchedText,
            colorHex = colorHex,
            comment = comment
        )
        addAnnotation(annotation)
        return mapOf("success" to true, "page" to result.pageIndex + 1)
    }

    fun sendChatMessage(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || _state.value.isChatSending) return

        _state.update {
            it.copy(
                chatMessages = it.chatMessages + ChatMessage("user", text),
                isChatSending = true
            )
        }

        viewModelScope.launch {
            try {
                val apiKey = settingsRepository.geminiApiKey.firstOrNull()
                if (apiKey.isNullOrBlank()) {
                    appendChatError("Imposta la API key di Gemini nelle Impostazioni per usare la chat.")
                    return@launch
                }

                val docText = documentTextCache ?: pdfEngine.extractAllText().also { documentTextCache = it }

                val systemPrompt = buildString {
                    append("Sei un assistente di ricerca accademica integrato in un lettore PDF. ")
                    append("Rispondi in modo conciso e nella lingua dell'utente, basandoti sul documento fornito. ")
                    append("Se l'informazione non è nel documento, dillo esplicitamente. ")
                    append("Quando citi un passaggio, indica la pagina (i marcatori [Pagina N] delimitano le pagine). ")
                    append("Se l'utente chiede di evidenziare passaggi, usa lo strumento create_highlight con citazioni esatte ")
                    append("copiate dal documento; al termine riassumi brevemente cosa hai evidenziato.\n\n")
                    if (docText.isBlank()) {
                        append("ATTENZIONE: non è stato possibile estrarre testo dal documento (potrebbe essere una scansione).")
                    } else {
                        append("=== TESTO DEL DOCUMENTO ===\n")
                        append(docText)
                    }
                }

                val contents = _state.value.chatMessages
                    .filter { !it.isError }
                    .map {
                        com.example.zoterohelpernative.data.GeminiContent(
                            role = it.role,
                            parts = listOf(com.example.zoterohelpernative.data.GeminiPart(text = it.text))
                        )
                    }
                    .toMutableList()

                val systemInstruction = com.example.zoterohelpernative.data.GeminiContent(
                    parts = listOf(com.example.zoterohelpernative.data.GeminiPart(text = systemPrompt))
                )

                var finalText: String? = null
                var highlightsCreated = 0

                // Tool-use loop: the model may request highlights before answering
                for (round in 0 until 4) {
                    val request = com.example.zoterohelpernative.data.GeminiRequest(
                        contents = contents,
                        systemInstruction = systemInstruction,
                        tools = chatTools()
                    )
                    val response = geminiService.generateContent("gemini-flash-latest", apiKey, request)
                    val body = response.body()

                    if (!response.isSuccessful) {
                        val errorBody = try {
                            response.errorBody()?.string()?.let { raw ->
                                com.google.gson.Gson().fromJson(raw, com.example.zoterohelpernative.data.GeminiResponse::class.java)
                            }
                        } catch (e: Exception) { null }
                        val reason = errorBody?.error?.message ?: body?.error?.message ?: "HTTP ${response.code()}"
                        appendChatError("Errore Gemini: $reason")
                        return@launch
                    }

                    val content = body?.candidates?.firstOrNull()?.content
                    if (content == null) {
                        appendChatError("Errore Gemini: risposta vuota (${body?.error?.message ?: "nessun candidato"})")
                        return@launch
                    }

                    val functionCalls = content.parts.mapNotNull { it.functionCall }
                    val textParts = content.parts.mapNotNull { it.text }.joinToString("")

                    if (functionCalls.isEmpty()) {
                        finalText = textParts
                        break
                    }

                    // Execute the requested highlights, then send the results back
                    contents += com.example.zoterohelpernative.data.GeminiContent(role = "model", parts = content.parts)
                    val responseParts = functionCalls.map { call ->
                        val result = if (call.name == "create_highlight") {
                            executeCreateHighlight(call.args).also {
                                if (it["success"] == true) highlightsCreated++
                            }
                        } else {
                            mapOf("success" to false, "error" to "Funzione sconosciuta: ${call.name}")
                        }
                        com.example.zoterohelpernative.data.GeminiPart(
                            functionResponse = com.example.zoterohelpernative.data.GeminiFunctionResponse(
                                name = call.name ?: "create_highlight",
                                response = result
                            )
                        )
                    }
                    contents += com.example.zoterohelpernative.data.GeminiContent(role = "user", parts = responseParts)
                }

                val answer = finalText?.takeIf { it.isNotBlank() }
                    ?: if (highlightsCreated > 0) {
                        "Ho creato $highlightsCreated evidenziazion${if (highlightsCreated == 1) "e" else "i"} nel documento."
                    } else null

                if (answer != null) {
                    _state.update {
                        it.copy(chatMessages = it.chatMessages + ChatMessage("model", answer.trim()))
                    }
                } else {
                    appendChatError("Errore Gemini: nessuna risposta ricevuta.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                appendChatError("Errore di rete: ${e.localizedMessage ?: "sconosciuto"}")
            } finally {
                _state.update { it.copy(isChatSending = false) }
            }
        }
    }

    private fun appendChatError(message: String) {
        _state.update {
            it.copy(chatMessages = it.chatMessages + ChatMessage("model", message, isError = true))
        }
    }

    fun loadDocument(itemKey: String, cacheDir: File) {
        viewModelScope.launch {
            documentTextCache = null
            currentAttachmentKey = itemKey
            _state.update {
                it.copy(
                    isLoadingPdf = true,
                    pdfError = null,
                    annotations = emptyList(),
                    selectedAnnotationId = null,
                    chatMessages = emptyList()
                )
            }
            try {
                val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                val userId = settingsRepository.zoteroUserId.firstOrNull()
                val webDavUrl = settingsRepository.webdavUrl.firstOrNull()
                val webDavUser = settingsRepository.webdavUser.firstOrNull()
                val webDavPass = settingsRepository.webdavPass.firstOrNull()

                if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty() || webDavUrl.isNullOrEmpty()) {
                    _state.update { it.copy(isLoadingPdf = false, pdfError = "Credenziali mancanti (API o WebDAV).") }
                    return@launch
                }

                // The itemKey passed from the UI IS the attachment key!
                val attachmentKey = itemKey

                // 2. Download from WebDAV
                val zipFile = webDavClient.downloadAttachment(webDavUrl, webDavUser, webDavPass, attachmentKey, cacheDir)
                if (zipFile == null) {
                    _state.update { it.copy(isLoadingPdf = false, pdfError = "Errore download da WebDAV.") }
                    return@launch
                }

                // 3. Extract ZIP
                val extractDir = File(cacheDir, "extracted_$attachmentKey")
                val pdfFile = ZipUtils.extractPdfFromZip(zipFile, extractDir)

                if (pdfFile == null) {
                    _state.update { it.copy(isLoadingPdf = false, pdfError = "Il file ZIP non conteneva alcun PDF.") }
                    return@launch
                }
                
                // 3.5 Fetch existing annotations for this attachment (paginated)
                try {
                    val remoteAnnotations = mutableListOf<ItemData>()
                    var start = 0
                    while (true) {
                        val annotationsResponse = apiService.getItemChildren(userId, attachmentKey, apiKey, start = start)
                        if (!annotationsResponse.isSuccessful) break
                        val page = annotationsResponse.body() ?: emptyList()
                        remoteAnnotations += page
                            .filter { it.data.itemType == "annotation" }
                            .map { it.data }
                        if (page.size < 100 || start >= 5000) break
                        start += 100
                    }
                    _state.update { it.copy(annotations = remoteAnnotations) }

                    // Library tags load in parallel: they must not delay the PDF
                    refreshLibraryTags(userId, apiKey)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Don't fail the whole PDF load just because annotations failed
                }

                // 4. Load into MuPDF
                if (pdfEngine.loadDocument(pdfFile)) {
                    // Resume from the last read page, if any
                    val savedPage = settingsRepository.lastPageMap.firstOrNull()?.get(attachmentKey) ?: 0
                    val startPage = savedPage.coerceIn(0, (pdfEngine.pageCount - 1).coerceAtLeast(0))
                    _state.update { it.copy(numPages = pdfEngine.pageCount, currentPage = startPage, isLoadingPdf = false) }
                    renderCurrentPage()
                } else {
                    _state.update { it.copy(isLoadingPdf = false, pdfError = "Impossibile aprire il PDF.") }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(isLoadingPdf = false, pdfError = e.localizedMessage) }
            }
        }
    }

    private fun refreshLibraryTags(userId: String, apiKey: String) {
        viewModelScope.launch {
            try {
                // The tags endpoint caps each page at 100 entries regardless of
                // the requested limit, so everything past the first page was lost.
                val tags = mutableListOf<String>()
                var start = 0
                while (true) {
                    val response = apiService.getTags(userId, apiKey, start = start)
                    if (!response.isSuccessful) break
                    val page = response.body() ?: emptyList()
                    tags += page.map { it.tag }
                    if (page.size < 100 || start >= 5000) break
                    start += 100
                }
                if (tags.isNotEmpty()) {
                    _state.update { s ->
                        // Keep tags added locally in the meantime
                        s.copy(allLibraryTags = (tags + s.allLibraryTags).distinct())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadPdf(file: java.io.File) {
        viewModelScope.launch {
            if (pdfEngine.loadDocument(file)) {
                _state.update { it.copy(numPages = pdfEngine.pageCount, currentPage = 0) }
                renderCurrentPage()
            }
        }
    }

    private var renderJob: kotlinx.coroutines.Job? = null

    fun renderCurrentPage() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingPdf = true, viewportBitmap = null, viewportRect = null) }
            val result = pdfEngine.renderPage(_state.value.currentPage)
            if (result != null) {
                _state.update { 
                    it.copy(
                        pageBitmap = result.bitmap,
                        pdfNativeWidth = result.nativeWidth,
                        pdfNativeHeight = result.nativeHeight,
                        pdfNativeBoundsLeft = result.nativeBoundsLeft,
                        pdfNativeBoundsTop = result.nativeBoundsTop,
                        structuredText = result.structuredText,
                        isLoadingPdf = false
                    ) 
                }
            } else {
                _state.update { it.copy(pdfError = "Impossibile renderizzare la pagina", isLoadingPdf = false) }
            }
        }
    }

    private var viewportJob: kotlinx.coroutines.Job? = null

    fun renderViewport(nativeRect: androidx.compose.ui.geometry.Rect, bitmapWidth: Int, bitmapHeight: Int) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return
        
        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            val rectF = android.graphics.RectF(nativeRect.left, nativeRect.top, nativeRect.right, nativeRect.bottom)
            val result = pdfEngine.renderViewport(_state.value.currentPage, rectF, bitmapWidth, bitmapHeight)
            if (result != null) {
                _state.update { 
                    it.copy(
                        viewportBitmap = result,
                        viewportRect = nativeRect
                    ) 
                }
            }
        }
    }
}
