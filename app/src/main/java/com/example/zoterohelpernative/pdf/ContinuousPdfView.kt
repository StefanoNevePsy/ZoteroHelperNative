package com.example.zoterohelpernative.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.annotations.AnnotationOps
import com.example.zoterohelpernative.data.ItemData
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Pages in a vertical scrolling column, for reading. Each page is drawn when
 * it comes near the screen (through the reader's page cache, so it is shared
 * with the single-page view) and its saved highlights are painted on top.
 *
 * This mode is for reading and navigating: zoom and annotating stay in the
 * single-page reader, opened on a page with a double tap.
 */
@Composable
fun ContinuousPdfView(
    pageCount: Int,
    startPage: Int,
    annotations: List<ItemData>,
    pdfTheme: String,
    appIsDark: Boolean,
    getUiColor: (String) -> Color,
    pageSizeOf: suspend (Int) -> Pair<Float, Float>?,
    renderPage: suspend (pageIndex: Int, columnWidthPx: Int) -> PageRenderResult?,
    onVisiblePageChanged: (Int) -> Unit,
    onOpenPage: (Int) -> Unit,
    /** A page to scroll to (from the index, search, arrows or "go to page"). */
    jumpTo: Int? = null,
    onJumpHandled: () -> Unit = {},
    /** The pen asks for the radial menu (long press or side button), at this position. */
    onStylusMenu: (Offset) -> Unit = {},
    /** Text of a page, for snapping pen selections to words. */
    pageText: suspend (pageIndex: Int, columnWidthPx: Int) -> com.artifex.mupdf.fitz.StructuredText? = { _, _ -> null },
    activeUiColor: Color = Color.Yellow,
    snapToWord: Boolean = false,
    /** A pen stroke on a page: rects in Zotero coords, extracted text. */
    onAnnotationCreated: (pageIndex: Int, pageHeight: Float, rects: List<androidx.compose.ui.geometry.Rect>, text: String) -> Unit = { _, _, _, _ -> },
    /** Tap on a highlight (id or null), at a position in root coordinates. */
    onAnnotationTapped: (String?, Offset) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    val currentOnVisible by rememberUpdatedState(onVisiblePageChanged)

    // The page that occupies the middle of the screen counts as "current"
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.firstOrNull { it.offset <= center && it.offset + it.size >= center }?.index
                ?: listState.firstVisibleItemIndex
        }.distinctUntilChanged().collect { currentOnVisible(it) }
    }

    LaunchedEffect(jumpTo) {
        val target = jumpTo ?: return@LaunchedEffect
        listState.animateScrollToItem(target.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
        onJumpHandled()
    }

    val theme = PdfThemes.resolve(pdfTheme, appIsDark)
    val currentOnStylusMenu by rememberUpdatedState(onStylusMenu)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Scrolling is the finger's job: each page consumes the pen's strokes
            // to highlight, so the list never sees them. This only watches the
            // pen for a long press or the side button, to open the radial menu.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val isPen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    if (!isPen) return@awaitEachGesture
                    var lastEventTime = down.uptimeMillis
                    var menuShown = false
                    var moved = false
                    var position = down.position
                    val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                    while (true) {
                        // A pen held still sends no events: wake up at the long-press deadline
                        val waitMs = if (menuShown || moved) Long.MAX_VALUE else (deadline - lastEventTime).coerceAtLeast(1)
                        val event = if (waitMs == Long.MAX_VALUE) awaitPointerEvent(PointerEventPass.Initial)
                            else withTimeoutOrNull(waitMs) { awaitPointerEvent(PointerEventPass.Initial) }
                        if (event == null) {
                            menuShown = true
                            currentOnStylusMenu(position)
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        lastEventTime = change.uptimeMillis
                        position = change.position
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        val sideButton = (event.buttons.hashCode() and 34) != 0
                        if (!menuShown && sideButton) {
                            menuShown = true
                            currentOnStylusMenu(position)
                        }
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        // As wide as the screen's shorter side, centered: on a landscape tablet a
        // full-width page would be enormous and cost memory for nothing
        val columnWidth = minOf(maxWidth, maxHeight)
        val columnWidthPx = with(density) { columnWidth.roundToPx() }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 72.dp, bottom = 120.dp)
        ) {
            items(count = pageCount, key = { it }) { pageIndex ->
                val pageSize by produceState<Pair<Float, Float>?>(null, pageIndex) { value = pageSizeOf(pageIndex) }
                val page by produceState<PageRenderResult?>(null, pageIndex, columnWidthPx) {
                    value = renderPage(pageIndex, columnWidthPx)
                }
                val aspect = pageSize?.let { (w, h) -> if (h > 0f) w / h else null } ?: (595f / 842f)

                val text by produceState<com.artifex.mupdf.fitz.StructuredText?>(null, pageIndex, page) {
                    if (page != null) value = pageText(pageIndex, columnWidthPx)
                }
                var pageOrigin by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .widthIn(max = columnWidth)
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .background(Color(0xFFFFFFFF.toInt() or theme.background))
                        .onGloballyPositioned { pageOrigin = it.positionInRoot() }
                        .pointerInput(pageIndex) {
                            detectTapGestures(onDoubleTap = { onOpenPage(pageIndex) })
                        }
                ) {
                    val rendered = page
                    if (rendered != null) {
                        // Same view, selection and drawing as the single-page reader
                        PdfPageView(
                            currentPage = pageIndex,
                            pageBitmap = rendered.bitmap,
                            pdfNativeWidth = rendered.nativeWidth,
                            pdfNativeHeight = rendered.nativeHeight,
                            pdfNativeBoundsLeft = rendered.nativeBoundsLeft,
                            pdfNativeBoundsTop = rendered.nativeBoundsTop,
                            structuredText = text,
                            annotations = annotations,
                            getUiColor = getUiColor,
                            activeUiColor = activeUiColor,
                            snapToWord = snapToWord,
                            pdfTheme = pdfTheme,
                            viewportBitmap = null,
                            viewportRect = null,
                            onViewportChanged = { _, _, _ -> },
                            pan = Offset.Zero,
                            zoom = 1f,
                            onPanZoomUpdate = { _, _, _ -> },
                            onAnnotationCreated = { rects, t -> onAnnotationCreated(pageIndex, rendered.nativeHeight, rects, t) },
                            onAnnotationTapped = { id, offset -> onAnnotationTapped(id, offset + pageOrigin) },
                            inScrollingColumn = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
