package com.example.zoterohelpernative.ui

import com.example.zoterohelpernative.annotations.AnnotationOps
import com.example.zoterohelpernative.annotations.PdfRect

import java.io.File
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zoterohelpernative.data.ItemData
import com.example.zoterohelpernative.data.SettingsRepository
import com.example.zoterohelpernative.data.ZoteroApiService
import com.example.zoterohelpernative.theme.DEFAULT_PALETTES
import com.example.zoterohelpernative.data.Palette
import com.example.zoterohelpernative.data.MappedColor
import com.example.zoterohelpernative.theme.AccentSecondary
import com.example.zoterohelpernative.theme.ZoteroYellow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.Icons

enum class ActiveTool { HIGHLIGHTER, UNDERLINE, PEN, ERASER, SHAPE }
enum class ShapeType { RECTANGLE, ELLIPSE, POLYGON }

data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String,
    val isError: Boolean = false,
    val savedAsNote: Boolean = false,
    val isSavingNote: Boolean = false
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
    /** Page whose bitmap is on screen; can briefly lag [currentPage] while the next one draws. */
    val displayedPage: Int = 0,
    /** A page is being drawn (the previous one stays visible meanwhile). */
    val isRenderingPage: Boolean = false,
    val showRenderStats: Boolean = false,
    val renderStats: String? = null,
    val pdfError: String? = null,
    val pdfTheme: String = "light",
    val syncError: String? = null,

    // AI Chat
    val chatMessages: List<ChatMessage> = emptyList(),
    val isChatSending: Boolean = false,
    val geminiKeySet: Boolean = false,
    val nvidiaKeySet: Boolean = false,
    val chatProvider: String = "gemini", // "gemini" | "nvidia"
    val nvidiaModels: List<String> = emptyList(),
    val selectedNvidiaModel: String = "",
    val isLoadingNvidiaModels: Boolean = false,

    // In-document search & table of contents
    val searchResults: List<com.example.zoterohelpernative.pdf.SearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val lastSearchQuery: String = "",
    val tocEntries: List<com.example.zoterohelpernative.pdf.TocEntry> = emptyList()
)

class ReaderViewModel(
    private val settingsRepository: SettingsRepository,
    private val zoteroRepository: com.example.zoterohelpernative.data.sync.ZoteroRepository? = null
) : ViewModel() {
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
            settingsRepository.renderStats.collect { show ->
                _state.update { it.copy(showRenderStats = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.geminiApiKey.collect { key ->
                _state.update { it.copy(geminiKeySet = !key.isNullOrBlank()) }
            }
        }
        viewModelScope.launch {
            settingsRepository.nvidiaApiKey.collect { key ->
                val wasSet = _state.value.nvidiaKeySet
                _state.update { it.copy(nvidiaKeySet = !key.isNullOrBlank()) }
                if (!key.isNullOrBlank() && !wasSet) {
                    refreshNvidiaModels()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.chatProvider.collect { provider ->
                _state.update { it.copy(chatProvider = provider) }
            }
        }
        viewModelScope.launch {
            settingsRepository.nvidiaModel.collect { model ->
                _state.update { it.copy(selectedNvidiaModel = model ?: "") }
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
    private val attachmentDownloader = com.example.zoterohelpernative.data.AttachmentDownloader(settingsRepository)

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

    // Double tap: zoom in around the tapped point, or back to fit
    fun toggleZoomAt(centroid: Offset) {
        val current = _state.value.zoomLevel
        if (current > 1.3f) {
            fitPage()
        } else {
            updatePanZoom(Offset.Zero, 2.5f / current, centroid)
        }
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
        if (undoStack.isEmpty()) return
        val plan = AnnotationOps.planUndo(snapshot = undoStack.removeLast(), current = _state.value.annotations)

        // Annotations the undone action added: remove them from the server too
        plan.toDelete.forEach { discardAnnotation(it) }

        _state.update { it.copy(annotations = plan.restored) }
        selectAnnotation(null)

        // Push recreations and restored contents back to Zotero
        plan.toSync.forEach { syncItemToZotero(it) }
    }

    fun getRecentTags(): List<String> = AnnotationOps.recentTags(_state.value.annotations)

    fun getAllTags(): List<String> = AnnotationOps.allTags(_state.value.annotations, _state.value.allLibraryTags)

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

    fun goToPage(pageIndex: Int) {
        val target = pageIndex.coerceIn(0, (_state.value.numPages - 1).coerceAtLeast(0))
        if (target != _state.value.currentPage) {
            _state.update { it.copy(currentPage = target, zoomLevel = 1.0f, panOffset = Offset.Zero) }
            renderCurrentPage()
            persistCurrentPage()
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun searchInDocument(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false, lastSearchQuery = trimmed) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true, lastSearchQuery = trimmed) }
            val results = pdfEngine.searchText(trimmed)
            _state.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun exportAnnotationsMarkdown(): String = AnnotationOps.exportMarkdown(_state.value.annotations)

    fun updateAnnotationComment(id: String, comment: String) {
        val currentAnnotations = _state.value.annotations.toMutableList()
        val index = currentAnnotations.indexOfFirst { it.key == id }
        if (index != -1) {
            val ann = currentAnnotations[index]
            if ((ann.annotationComment ?: "") == comment) return
            pushUndoState()
            val updated = ann.copy(annotationComment = comment)
            currentAnnotations[index] = updated
            _state.value = _state.value.copy(annotations = currentAnnotations)
            syncItemToZotero(updated)
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

    fun addHighlight(
        parentItemKey: String,
        nativeRects: List<androidx.compose.ui.geometry.Rect>,
        extractedText: String,
        annotationType: String = "highlight"
    ) {
        if (nativeRects.isEmpty()) return
        val s = _state.value
        val newAnn = AnnotationOps.buildHighlight(
            parentItemKey = parentItemKey,
            pageIndex = s.displayedPage,
            pageHeight = s.pdfNativeHeight,
            rects = nativeRects.map { PdfRect(it.left, it.top, it.right, it.bottom) },
            text = extractedText,
            colorHex = s.activeColorHex,
            annotationType = annotationType
        )
        addAnnotation(newAnn)
    }

    /** Adds an annotation, merging it into an overlapping same-color highlight if there is one. */
    fun addAnnotation(annotation: ItemData) {
        pushUndoState()
        val result = AnnotationOps.mergeOrAppend(_state.value.annotations, annotation)
        _state.update { it.copy(annotations = result.annotations) }
        // Sync exactly what changed: the merged-into annotation, or the new one
        syncItemToZotero(if (result.mergedIntoKey != null) result.changedAnnotation else annotation)
    }

    fun deleteAnnotation(annotation: ItemData) {
        pushUndoState()
        _state.update { it.copy(annotations = it.annotations.filter { a -> a.key != annotation.key }) }
        discardAnnotation(annotation)
    }

    /** Removes an annotation from Zotero, or just drops the local copy if it never reached it. */
    private fun discardAnnotation(annotation: ItemData) {
        if (annotation.version != 0L && !annotation.key.startsWith("local_")) {
            deleteItemFromZotero(annotation)
        } else {
            viewModelScope.launch { zoteroRepository?.removeLocalItem(annotation.key) }
        }
    }

    fun eraseAnnotationsIntersecting(nativeRects: List<androidx.compose.ui.geometry.Rect>, pageIndex: Int) {
        val before = _state.value.annotations
        val result = AnnotationOps.erase(before, nativeRects.map { PdfRect(it.left, it.top, it.right, it.bottom) }, pageIndex)
        if (!result.changed) return

        pushUndoState() // state still holds the pre-erase annotations here
        result.removed.forEach { discardAnnotation(it) }
        result.modified.forEach { syncItemToZotero(it) }
        _state.update { it.copy(annotations = result.annotations) }
        selectAnnotation(null)
    }

    fun generateZoteroKey(): String = AnnotationOps.generateZoteroKey()

    private val pdfEngine = com.example.zoterohelpernative.pdf.PdfEngine()

    private var documentTextCache: String? = null
    private var currentAttachmentKey: String? = null
    private var currentParentItemKey: String? = null

    // ---- Zotero writes (see AnnotationSync) ----

    // Lazy: the init block above starts collectors that may reach these before
    // their declaration line would otherwise have run
    private val sync by lazy { AnnotationSync(_state, viewModelScope, settingsRepository, zoteroRepository, apiService) }

    private fun setSyncError(message: String) = sync.setSyncError(message)
    private fun syncItemToZotero(itemData: ItemData) = sync.syncItemToZotero(itemData)
    private fun deleteItemFromZotero(annotation: ItemData) = sync.deleteItemFromZotero(annotation)

    // ---- AI chat (see ReaderChat) ----

    private val chat by lazy { ReaderChat(
        state = _state,
        scope = viewModelScope,
        settingsRepository = settingsRepository,
        documentText = {
            documentTextCache ?: pdfEngine.extractAllText(maxChars = 400_000).also { documentTextCache = it }
        },
        createHighlight = { args -> createHighlightFromQuote(args) },
        saveNote = { args -> sync.createChildNote(currentParentItemKey, args) },
        showMessage = { setSyncError(it) }
    ) }

    fun setChatProvider(provider: String) = chat.setChatProvider(provider)
    fun setNvidiaModel(model: String) = chat.setNvidiaModel(model)
    fun refreshNvidiaModels() = chat.refreshNvidiaModels()
    fun clearChat() = chat.clearChat()
    fun sendChatMessage(userText: String) = chat.sendChatMessage(userText)
    fun saveChatMessageAsNote(index: Int) = chat.saveChatMessageAsNote(index)

    private suspend fun createHighlightFromQuote(args: Map<String, Any?>?): Map<String, Any?> {
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

        val colorHex = ReaderChat.HIGHLIGHT_COLORS[colorName] ?: _state.value.activeColorHex
        val annotation = AnnotationOps.buildHighlight(
            parentItemKey = parentKey,
            pageIndex = result.pageIndex,
            pageHeight = result.pageHeight,
            rects = result.rects.map { PdfRect(it.left, it.top, it.right, it.bottom) },
            text = result.matchedText,
            colorHex = colorHex,
            comment = comment
        )
        addAnnotation(annotation)
        return mapOf("success" to true, "page" to result.pageIndex + 1)
    }


    fun loadDocument(itemKey: String, cacheDir: File) {
        viewModelScope.launch {
            documentTextCache = null
            currentAttachmentKey = itemKey
            currentParentItemKey = null
            resetPageCache()
            _state.update {
                it.copy(
                    isLoadingPdf = true,
                    pageBitmap = null,
                    structuredText = null,
                    viewportBitmap = null,
                    viewportRect = null,
                    isRenderingPage = false,
                    pdfError = null,
                    annotations = emptyList(),
                    selectedAnnotationId = null,
                    chatMessages = emptyList(),
                    searchResults = emptyList(),
                    lastSearchQuery = "",
                    tocEntries = emptyList()
                )
            }
            try {
                val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                val userId = settingsRepository.zoteroUserId.firstOrNull()

                // WebDAV is optional now: attachments can also come from Zotero storage
                if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty()) {
                    _state.update { it.copy(isLoadingPdf = false, pdfError = "Credenziali Zotero mancanti: impostale nelle Impostazioni.") }
                    return@launch
                }

                // The itemKey passed from the UI IS the attachment key!
                val attachmentKey = itemKey
                val cachedPdf = com.example.zoterohelpernative.data.AttachmentDownloader.cachedPdf(cacheDir, attachmentKey)

                if (cachedPdf != null) {
                    // ---- Instant path: open from the cache, refresh in background ----
                    val cachedAnnotations = zoteroRepository?.getCachedAnnotations(attachmentKey) ?: emptyList()
                    _state.update { it.copy(annotations = cachedAnnotations) }

                    if (!openPdf(cachedPdf, attachmentKey)) return@launch

                    viewModelScope.launch { refreshAnnotationsFromServer(userId, apiKey, attachmentKey) }
                    refreshLibraryTags(userId, apiKey)
                    viewModelScope.launch { checkForUpdatedPdf(userId, apiKey, attachmentKey, cacheDir) }
                } else {
                    // ---- First open: the network is required ----
                    var remoteMd5: String? = null
                    try {
                        val res = apiService.getItems(userId, apiKey, itemKey = attachmentKey)
                        val attachmentData = res.body()?.firstOrNull()?.data
                        currentParentItemKey = attachmentData?.parentItem
                        remoteMd5 = attachmentData?.md5
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val pdfFile = when (val outcome = attachmentDownloader.ensurePdf(attachmentKey, cacheDir)) {
                        is com.example.zoterohelpernative.data.DownloadOutcome.Success -> outcome.pdf
                        is com.example.zoterohelpernative.data.DownloadOutcome.Failure -> {
                            _state.update {
                                it.copy(isLoadingPdf = false, pdfError = "Impossibile scaricare il documento: ${outcome.reason}.")
                            }
                            return@launch
                        }
                    }
                    remoteMd5?.let { settingsRepository.savePdfMd5(attachmentKey, it) }

                    refreshAnnotationsFromServer(userId, apiKey, attachmentKey)
                    refreshLibraryTags(userId, apiKey)
                    openPdf(pdfFile, attachmentKey)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(isLoadingPdf = false, pdfError = e.localizedMessage) }
            }
        }
    }

    private suspend fun openPdf(pdfFile: File, attachmentKey: String): Boolean {
        return if (pdfEngine.loadDocument(pdfFile)) {
            // Resume from the last read page, if any
            val savedPage = settingsRepository.lastPageMap.firstOrNull()?.get(attachmentKey) ?: 0
            val startPage = savedPage.coerceIn(0, (pdfEngine.pageCount - 1).coerceAtLeast(0))
            _state.update { it.copy(numPages = pdfEngine.pageCount, currentPage = startPage) }
            renderCurrentPage() // clears the loading screen once the first page is drawn
            // Table of contents, loaded off the critical path
            viewModelScope.launch {
                _state.update { it.copy(tocEntries = pdfEngine.getOutline()) }
            }
            true
        } else {
            _state.update { it.copy(isLoadingPdf = false, pdfError = "Impossibile aprire il PDF.") }
            false
        }
    }

    /**
     * Fetches the annotations from Zotero and reconciles them with local state:
     * pending offline edits win over their server copies, offline deletions stay
     * hidden, and annotations created in the reader meanwhile are preserved.
     * On network failure the currently shown (cached) annotations stay in place.
     */
    private suspend fun refreshAnnotationsFromServer(userId: String, apiKey: String, attachmentKey: String) {
        try {
            val remoteAnnotations = mutableListOf<ItemData>()
            var start = 0
            while (true) {
                val response = apiService.getItemChildren(userId, attachmentKey, apiKey, start = start)
                if (!response.isSuccessful) return
                val page = response.body() ?: emptyList()
                remoteAnnotations += page
                    .filter { it.data.itemType == "annotation" }
                    .map { it.data }
                if (page.size < 100 || start >= 5000) break
                start += 100
            }

            val pending = zoteroRepository?.getPendingAnnotations(attachmentKey) ?: emptyList()
            val pendingKeys = pending.map { it.key }.toSet()
            val deletedKeys = zoteroRepository?.getPendingDeletionKeys(attachmentKey) ?: emptySet()
            val remoteClean = remoteAnnotations.filter { it.key !in pendingKeys && it.key !in deletedKeys }

            _state.update { s ->
                // Anything created in the reader meanwhile and not yet synced
                val liveUnsynced = s.annotations.filter { local ->
                    local.version == 0L && local.key !in pendingKeys &&
                        remoteClean.none { it.key == local.key }
                }
                s.copy(annotations = remoteClean + pending + liveUnsynced)
            }
            zoteroRepository?.saveLocalAnnotations(remoteClean, dirty = false)
        } catch (e: Exception) {
            e.printStackTrace() // offline: the cached annotations stay in place
        }
    }

    /**
     * Compares the attachment's md5 with the cached copy; when the PDF was
     * replaced on Zotero the new version is downloaded in the background and
     * used at the next open.
     */
    private suspend fun checkForUpdatedPdf(userId: String, apiKey: String, attachmentKey: String, cacheDir: File) {
        try {
            val res = apiService.getItems(userId, apiKey, itemKey = attachmentKey)
            val attachmentData = res.body()?.firstOrNull()?.data ?: return
            currentParentItemKey = attachmentData.parentItem
            val remoteMd5 = attachmentData.md5 ?: return
            val storedMd5 = settingsRepository.pdfMd5Map.firstOrNull()?.get(attachmentKey)

            if (storedMd5 == null) {
                settingsRepository.savePdfMd5(attachmentKey, remoteMd5)
                return
            }
            if (storedMd5 == remoteMd5) return

            // The old copy stays in place unless the new one installs completely
            val outcome = attachmentDownloader.ensurePdf(attachmentKey, cacheDir, forceRefresh = true)
            if (outcome !is com.example.zoterohelpernative.data.DownloadOutcome.Success) return
            settingsRepository.savePdfMd5(attachmentKey, remoteMd5)
            setSyncError("È disponibile una versione aggiornata del PDF: riapri il documento per vederla.")
        } catch (e: Exception) {
            e.printStackTrace()
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

    // ---- Page rendering ---------------------------------------------------
    //
    // Pages are drawn at RenderBudget's resolution (1.5x the screen, never more
    // than the old fixed 3.5x cost), kept in an LRU of the current page and its
    // neighbours, and the next/previous pages are prepared in the background.
    // Text for highlight snapping is extracted after the bitmap is on screen.
    // A page turn never swaps the reader for the loading screen: the previous
    // page stays visible until the new one is ready.

    private class RenderedPage(
        val result: com.example.zoterohelpernative.pdf.PageRenderResult,
        var text: com.artifex.mupdf.fitz.StructuredText? = null
    )

    private val pageCache = com.example.zoterohelpernative.pdf.PageCache<RenderedPage>(capacity = 3)
    private val pagesInFlight = mutableMapOf<Int, kotlinx.coroutines.Deferred<RenderedPage?>>()
    private val textInFlight = mutableMapOf<Int, kotlinx.coroutines.Deferred<Unit>>()
    /** Bumped when a document is (re)loaded: work started for the old one is discarded. */
    private var renderGeneration = 0

    private var renderJob: kotlinx.coroutines.Job? = null
    private var prefetchJob: kotlinx.coroutines.Job? = null

    private fun resetPageCache() {
        renderGeneration++
        pagesInFlight.values.forEach { it.cancel() }
        textInFlight.values.forEach { it.cancel() }
        pagesInFlight.clear()
        textInFlight.clear()
        pageCache.clear()
        prefetchJob?.cancel()
        renderJob?.cancel()
    }

    private suspend fun targetScale(pageIndex: Int): Float {
        val (w, h) = pdfEngine.pageSize(pageIndex) ?: return 1f
        // The reader is full screen, so the screen size is the view size
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        return com.example.zoterohelpernative.pdf.RenderBudget.scaleFor(w, h, metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * The rendered page, from the cache or drawn now. Concurrent requests for
     * the same page share one render, so flipping into a page the prefetch is
     * already drawing doesn't draw it twice.
     */
    private suspend fun ensureRendered(pageIndex: Int): RenderedPage? {
        val generation = renderGeneration
        val wanted = targetScale(pageIndex)
        pageCache[pageIndex]?.let { cached ->
            if (com.example.zoterohelpernative.pdf.RenderBudget.isReusable(cached.result.scale, wanted)) return cached
        }
        // The render job owns its bookkeeping: a caller cancelled by a quick page
        // turn must not drop a render still in progress, or the next request
        // would draw the page again and this result would be lost.
        val deferred = pagesInFlight[pageIndex] ?: viewModelScope.async {
            val rendered = pdfEngine.renderPage(pageIndex, wanted)?.let { RenderedPage(it) }
            if (generation == renderGeneration) {
                if (rendered != null) pageCache.put(pageIndex, rendered)
                pagesInFlight.remove(pageIndex)
            }
            rendered
        }.also { pagesInFlight[pageIndex] = it }
        val rendered = deferred.await()
        return if (generation == renderGeneration) rendered else null
    }

    /** Extracts the page text once, then shows it if that page is still on screen. */
    private suspend fun ensureText(pageIndex: Int, page: RenderedPage) {
        if (page.text != null) return
        val generation = renderGeneration
        val deferred = textInFlight[pageIndex] ?: viewModelScope.async {
            val text = pdfEngine.extractStructuredText(pageIndex)
            if (generation == renderGeneration) {
                page.text = text
                textInFlight.remove(pageIndex)
            }
        }.also { textInFlight[pageIndex] = it }
        deferred.await()
        if (_state.value.displayedPage == pageIndex && page.text != null) {
            _state.update { it.copy(structuredText = page.text) }
        }
    }

    fun renderCurrentPage() {
        renderJob?.cancel()
        prefetchJob?.cancel()
        val target = _state.value.currentPage
        renderJob = viewModelScope.launch {
            val cached = pageCache[target]
            if (cached == null) _state.update { it.copy(isRenderingPage = true) }
            val page = ensureRendered(target)
            if (page == null) {
                // A failed page turn keeps the previous page; only a failed first
                // page (nothing on screen) is an error
                _state.update {
                    if (it.pageBitmap == null) it.copy(pdfError = "Impossibile renderizzare la pagina", isLoadingPdf = false, isRenderingPage = false)
                    else it.copy(isRenderingPage = false)
                }
                return@launch
            }
            if (_state.value.currentPage != target) return@launch // the user moved on meanwhile

            val r = page.result
            _state.update {
                it.copy(
                    pageBitmap = r.bitmap,
                    pdfNativeWidth = r.nativeWidth,
                    pdfNativeHeight = r.nativeHeight,
                    pdfNativeBoundsLeft = r.nativeBoundsLeft,
                    pdfNativeBoundsTop = r.nativeBoundsTop,
                    structuredText = page.text,
                    displayedPage = target,
                    viewportBitmap = null,
                    viewportRect = null,
                    isLoadingPdf = false,
                    isRenderingPage = false,
                    renderStats = renderStatsFor(target, r, fromCache = cached != null)
                )
            }
            ensureText(target, page)

            // Prepare the pages the user is most likely to open next
            prefetchJob = viewModelScope.launch {
                for (neighbour in listOf(target + 1, target - 1)) {
                    if (neighbour !in 0 until _state.value.numPages) continue
                    val next = ensureRendered(neighbour) ?: continue
                    ensureText(neighbour, next)
                }
            }
        }
    }

    private fun renderStatsFor(page: Int, r: com.example.zoterohelpernative.pdf.PageRenderResult, fromCache: Boolean): String {
        val mb = r.bitmap.width.toLong() * r.bitmap.height * 4 / 1_048_576.0
        val source = if (fromCache) "dalla memoria" else "${r.renderMillis} ms"
        return "Pag. ${page + 1}: ${r.bitmap.width}×${r.bitmap.height} (%.1f MB, %.2fx) · %s · in memoria %s"
            .format(java.util.Locale.ROOT, mb, r.scale, source, pageCache.pages.sorted().joinToString(",") { "${it + 1}" })
    }

    private var viewportJob: kotlinx.coroutines.Job? = null

    fun renderViewport(nativeRect: androidx.compose.ui.geometry.Rect, bitmapWidth: Int, bitmapHeight: Int) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return

        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            val page = _state.value.displayedPage
            val rectF = android.graphics.RectF(nativeRect.left, nativeRect.top, nativeRect.right, nativeRect.bottom)
            val result = pdfEngine.renderViewport(page, rectF, bitmapWidth, bitmapHeight)
            // Drop a tile that arrives after the page changed
            if (result != null && _state.value.displayedPage == page) {
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
