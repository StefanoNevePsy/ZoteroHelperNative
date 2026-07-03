package com.example.zoterohelpernative.ui.main

import com.example.zoterohelpernative.ui.components.RadialMenuCapacitor
import com.example.zoterohelpernative.ui.components.GlassSurface
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.pdf.PdfPageView
import com.example.zoterohelpernative.ui.ActiveTool
import com.example.zoterohelpernative.ui.ReaderViewModel
import com.example.zoterohelpernative.ui.components.ReaderSidebar
import com.example.zoterohelpernative.theme.ZoteroYellow
import com.example.zoterohelpernative.theme.ZoteroBlue
import com.example.zoterohelpernative.theme.ZoteroRed
import com.example.zoterohelpernative.ui.components.safeParseColor
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import android.app.Activity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.example.zoterohelpernative.ui.components.LocalHazeState

@Composable
fun ReaderScreen(
    itemKey: String,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var radialMenuPosition by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(state.isFullscreen) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (state.isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(itemKey) {
        viewModel.loadDocument(itemKey, context.cacheDir)
    }

    val hazeState = remember { HazeState() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoadingPdf) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.example.zoterohelpernative.ui.components.BackgroundCanvas(modifier = Modifier.fillMaxSize().hazeSource(hazeState))
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF60A5FA))
                }
            }
        } else if (state.pdfError != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.example.zoterohelpernative.ui.components.BackgroundCanvas(modifier = Modifier.fillMaxSize().hazeSource(hazeState))
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.example.zoterohelpernative.ui.components.GlassSurface(color = Color(0x33FF0000)) {
                        Text(
                            text = state.pdfError ?: "Errore caricamento PDF",
                            color = Color.White,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        } else {
            PdfPageView(
            currentPage = state.currentPage,
            pageBitmap = state.pageBitmap,
            pdfNativeWidth = state.pdfNativeWidth,
            pdfNativeHeight = state.pdfNativeHeight,
            pdfNativeBoundsLeft = state.pdfNativeBoundsLeft,
            pdfNativeBoundsTop = state.pdfNativeBoundsTop,
            structuredText = state.structuredText,
            annotations = state.annotations,
            getUiColor = viewModel::getUiColor,
            activeUiColor = viewModel.getUiColor(state.activeColorHex),
            snapToWord = state.snapToWord,
            penHighlightOnly = state.penHighlightOnly,
            fingerSelectionOnly = state.fingerSelectionOnly,
            pdfTheme = state.pdfTheme,
            viewportBitmap = state.viewportBitmap,
            viewportRect = state.viewportRect,
            onViewportChanged = { rect, width, height ->
                viewModel.renderViewport(rect, width, height)
            },
            pan = state.panOffset,
            zoom = state.zoomLevel,
            onPanZoomUpdate = { panChange, zoomChange, centroid ->
                viewModel.updatePanZoom(panChange, zoomChange, centroid)
            },
            onAnnotationCreated = { nativeRects, extractedText ->
                if (state.activeTool == ActiveTool.ERASER) {
                    viewModel.eraseAnnotationsIntersecting(nativeRects, state.currentPage)
                } else {
                    val type = if (state.activeTool == ActiveTool.UNDERLINE) "underline" else "highlight"
                    viewModel.addHighlight(itemKey, nativeRects, extractedText, type)
                }
            },
            onAnnotationTapped = { id, offset ->
                viewModel.selectAnnotation(id, offset)
            },
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .pointerInput(Unit) {
                    coroutineScope {
                        launch {
                            detectTapGestures(
                                onLongPress = { offset ->
                                    radialMenuPosition = offset
                                    if (!viewModel.state.value.radialMenuOpen) {
                                        viewModel.toggleRadialMenu()
                                    }
                                }
                            )
                        }
                        launch {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull()
                                    val isStylusButton = (event.buttons.hashCode() and 34) != 0
                                    
                                    if (change != null && change.pressed && isStylusButton && change.type == androidx.compose.ui.input.pointer.PointerType.Stylus) {
                                        radialMenuPosition = change.position
                                        if (!viewModel.state.value.radialMenuOpen) {
                                            viewModel.toggleRadialMenu()
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                }
            )
        
        val density = androidx.compose.ui.platform.LocalDensity.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val screenCenter = Offset(screenWidthPx / 2f, screenHeightPx / 2f)

        // Radial Menu Overlay
        RadialMenuCapacitor(
            visible = state.radialMenuOpen,
            position = radialMenuPosition,
            activeToolId = state.activeTool.name,
            activeColor = viewModel.getUiColor(state.activeColorHex),
            level = state.radialMenuLevel,
            activePaletteColors = viewModel.activePalette.colors,
            squaredHighlighter = state.squaredHighlighter,
            snapToWord = state.snapToWord,
            isFullscreen = state.isFullscreen,
            toolIcons = state.toolIcons,
            onLevelChange = { viewModel.setRadialMenuLevel(it) },
            onToolSelect = { toolName ->
                try {
                    viewModel.setActiveTool(ActiveTool.valueOf(toolName))
                } catch (e: Exception) {}
            },
            onColorSelect = { color ->
                viewModel.setActiveColor(color.zoteroHex)
            },
            onCyclePalette = { viewModel.cyclePalette() },
            onUndo = { viewModel.undoLastAction() },
            onToggleSquared = { viewModel.toggleSquaredHighlighter() },
            onToggleSnap = { viewModel.toggleSnapToWord() },
            onToggleFullscreen = { viewModel.toggleFullscreen() },
            onZoomIn = { viewModel.zoomIn(screenCenter) },
            onZoomOut = { viewModel.zoomOut(screenCenter) },
            onFitPage = { viewModel.fitPage() },
            onSetPdfTheme = { viewModel.setPdfTheme(it) },
            onClose = { if (state.radialMenuOpen) viewModel.closeRadialMenu() }
        )

        // Annotation Popup
        if (state.selectedAnnotationId != null) {
            val ann = state.annotations.find { it.key == state.selectedAnnotationId }
            if (ann != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.selectAnnotation(null) }
                ) {
                    val popupPos = state.annotationPopupPosition
                    
                    val popupWidthDp = 260.dp
                    val popupHeightDp = 420.dp
                    
                    val popupWidthPx = with(density) { popupWidthDp.toPx() }
                    val popupHeightPx = with(density) { popupHeightDp.toPx() }
                    
                    val rawX = popupPos.x - (popupWidthPx / 2)
                    val rawY = popupPos.y + with(density) { 20.dp.toPx() }

                    val maxX = (screenWidthPx - popupWidthPx - 16f).coerceAtLeast(16f)
                    val maxY = (screenHeightPx - popupHeightPx - 16f).coerceAtLeast(16f)
                    val clampedX = rawX.coerceIn(16f, maxX)
                    val clampedY = rawY.coerceIn(16f, maxY)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                            .width(popupWidthDp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // Prevent click-through
                    ) {
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = Color(0xFA111827),
                            borderColor = Color(0x33FFFFFF),
                            borderWidth = 1.dp,
                            blurRadius = 24.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Text("Opzioni Annotazione", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteAnnotation(ann)
                                            viewModel.selectAnnotation(null)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Elimina", tint = Color(0xFFFCA5A5))
                                    }
                                }
                                
                                // Color Picker Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    viewModel.activePalette.colors.forEach { color ->
                                        val isCurrent = ann.annotationColor == color.zoteroHex
                                        val parsedColor = safeParseColor(color.uiHex)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(parsedColor)
                                                .border(if (isCurrent) 2.dp else 0.dp, if (isCurrent) Color.White else Color.Transparent, CircleShape)
                                                .clickable {
                                                    viewModel.updateAnnotationColor(ann.key, color.zoteroHex)
                                                }
                                        )
                                    }
                                }
                                // Comment field
                                Spacer(modifier = Modifier.height(16.dp))
                                var commentText by remember(ann.key) { mutableStateOf(ann.annotationComment ?: "") }
                                val commentChanged = commentText != (ann.annotationComment ?: "")
                                androidx.compose.material3.OutlinedTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                    placeholder = { androidx.compose.material3.Text("Aggiungi un commento…", color = Color(0x80FFFFFF), fontSize = 12.sp) },
                                    trailingIcon = {
                                        if (commentChanged) {
                                            IconButton(onClick = { viewModel.updateAnnotationComment(ann.key, commentText.trim()) }) {
                                                Icon(Icons.Outlined.Check, contentDescription = "Salva commento", tint = Color(0xFF34D399))
                                            }
                                        }
                                    },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color(0xFF60A5FA),
                                        focusedBorderColor = Color(0x8060A5FA),
                                        unfocusedBorderColor = Color(0x33FFFFFF)
                                    )
                                )

                                // New tag input: type to filter the lists below, or create a new tag
                                Spacer(modifier = Modifier.height(16.dp))
                                var tagQuery by remember(ann.key) { mutableStateOf("") }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = tagQuery,
                                        onValueChange = { tagQuery = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                        placeholder = { androidx.compose.material3.Text("Cerca o crea tag…", color = Color(0x80FFFFFF), fontSize = 12.sp) },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = Color(0xFF60A5FA),
                                            focusedBorderColor = Color(0x8060A5FA),
                                            unfocusedBorderColor = Color(0x33FFFFFF)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val currentTagsNow = ann.tags?.map { it.tag } ?: emptyList()
                                    val canCreate = tagQuery.isNotBlank() && tagQuery.trim() !in currentTagsNow
                                    IconButton(
                                        onClick = {
                                            viewModel.toggleAnnotationTag(ann.key, com.example.zoterohelpernative.data.ZoteroTag(tagQuery.trim(), 0))
                                            tagQuery = ""
                                        },
                                        enabled = canCreate,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(if (canCreate) Color(0xFF34D399) else Color(0x33FFFFFF), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Add,
                                            contentDescription = "Aggiungi tag",
                                            tint = if (canCreate) Color.Black else Color(0x80FFFFFF)
                                        )
                                    }
                                }

                                // Tags Grid
                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.Text("TAGS CORRENTI", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

                                val tagFilter = tagQuery.trim()
                                val recentTagsList = viewModel.getRecentTags()
                                    .filter { tagFilter.isBlank() || it.contains(tagFilter, ignoreCase = true) }
                                val allLibraryTags = viewModel.getAllTags()
                                    .filter { tagFilter.isBlank() || it.contains(tagFilter, ignoreCase = true) }
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                                ) {
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val currentTags = ann.tags?.map { it.tag } ?: emptyList()
                                        if (currentTags.isEmpty()) {
                                            androidx.compose.material3.Text("Nessun tag corrente", color = Color(0x80FFFFFF), style = MaterialTheme.typography.bodySmall)
                                        }
                                        currentTags.forEach { tagStr ->
                                            val tagColor = com.example.zoterohelpernative.ui.components.getTagColor(tagStr)
                                            Surface(
                                                color = tagColor,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.clickable { viewModel.toggleAnnotationTag(ann.key, com.example.zoterohelpernative.data.ZoteroTag(tagStr, 0)) }
                                            ) {
                                                androidx.compose.material3.Text(
                                                    text = tagStr,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    androidx.compose.material3.Text(
                                        text = "Tag Recenti",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF60A5FA),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val currentTags = ann.tags?.map { it.tag } ?: emptyList()
                                        recentTagsList.filter { it !in currentTags }.forEach { tagStr ->
                                            val tagColor = com.example.zoterohelpernative.ui.components.getTagColor(tagStr)
                                            Surface(
                                                color = tagColor,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.clickable { viewModel.toggleAnnotationTag(ann.key, com.example.zoterohelpernative.data.ZoteroTag(tagStr, 0)) }
                                            ) {
                                                androidx.compose.material3.Text(
                                                    text = tagStr,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    androidx.compose.material3.Text(
                                        text = "Tutti i Tag",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val currentTags = ann.tags?.map { it.tag } ?: emptyList()
                                        val remainingTags = allLibraryTags.filter { it !in currentTags && it !in recentTagsList }
                                        if (remainingTags.isEmpty()) {
                                            androidx.compose.material3.Text("Nessun altro tag", color = Color(0x80FFFFFF), style = MaterialTheme.typography.bodySmall)
                                        }
                                        remainingTags.forEach { tagStr ->
                                            val tagColor = com.example.zoterohelpernative.ui.components.getTagColor(tagStr)
                                            Surface(
                                                color = tagColor.copy(alpha = 0.2f),
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.clickable { viewModel.toggleAnnotationTag(ann.key, com.example.zoterohelpernative.data.ZoteroTag(tagStr, 0)) },
                                                border = androidx.compose.foundation.BorderStroke(1.dp, tagColor.copy(alpha = 0.5f))
                                            ) {
                                                androidx.compose.material3.Text(
                                                    text = tagStr,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sidebar Overlay
        ReaderSidebar(
            isOpen = state.readerSidebarOpen,
            onClose = { viewModel.toggleSidebar() },
            annotations = state.annotations,
            getUiColor = { viewModel.getUiColor(it) },
            onDeleteAnnotation = { viewModel.deleteAnnotation(it) },
            chatMessages = state.chatMessages,
            isChatSending = state.isChatSending,
            geminiKeySet = state.geminiKeySet,
            nvidiaKeySet = state.nvidiaKeySet,
            chatProvider = state.chatProvider,
            nvidiaModels = state.nvidiaModels,
            selectedNvidiaModel = state.selectedNvidiaModel,
            isLoadingNvidiaModels = state.isLoadingNvidiaModels,
            onProviderChange = { viewModel.setChatProvider(it) },
            onModelChange = { viewModel.setNvidiaModel(it) },
            onRefreshModels = { viewModel.refreshNvidiaModels() },
            onSendChatMessage = { viewModel.sendChatMessage(it) },
            onClearChat = { viewModel.clearChat() },
            searchResults = state.searchResults,
            isSearching = state.isSearching,
            onSearch = { viewModel.searchInDocument(it) },
            tocEntries = state.tocEntries,
            onGoToPage = { viewModel.goToPage(it) },
            onExportAnnotations = {
                val markdown = viewModel.exportAnnotationsMarkdown()
                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, markdown)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Annotazioni")
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, "Esporta annotazioni"))
            },
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd)
        )

        // Top right sidebar toggle
        IconButton(
            onClick = { viewModel.toggleSidebar() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(androidx.compose.ui.graphics.Color(0xAA000000), CircleShape)
        ) {
            Icon(
                imageVector = com.example.zoterohelpernative.ui.icons.IconResolver.resolve(state.toolIcons["tool_menu"], Icons.Outlined.Menu),
                contentDescription = "Apri Sidebar",
                tint = androidx.compose.ui.graphics.Color.White
            )
        }

        // Paging Controls Overlay (liquid glass pill over the PDF)
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = Color(0x66101623),
            borderColor = Color(0x40FFFFFF),
            blurRadius = 20.dp
        ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.previousPage() },
                enabled = state.currentPage > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Precedente", tint = androidx.compose.ui.graphics.Color.White)
            }
            Text(
                text = "${state.currentPage + 1} / ${state.numPages}",
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            IconButton(
                onClick = { viewModel.nextPage() },
                enabled = state.currentPage < state.numPages - 1
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Successiva", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
        } // close paging GlassSurface

        } // close else

        // Sync error banner (annotation save/delete failures are no longer silent)
        state.syncError?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                GlassSurface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color(0xE67F1D1D),
                    borderColor = Color(0x66FCA5A5)
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
    }
}
