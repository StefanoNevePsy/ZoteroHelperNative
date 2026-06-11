package com.example.zoterohelpernative.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.roundToInt
import com.artifex.mupdf.fitz.StructuredText
import com.example.zoterohelpernative.data.ItemData
import com.example.zoterohelpernative.theme.ZoteroYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a single PDF page bitmap and overlays Zotero annotations using a Compose Canvas.
 */
@Composable
fun PdfPageView(
    currentPage: Int,
    pageBitmap: Bitmap?,
    pdfNativeWidth: Float,
    pdfNativeHeight: Float,
    pdfNativeBoundsLeft: Float,
    pdfNativeBoundsTop: Float,
    structuredText: StructuredText?,
    annotations: List<ItemData>,
    getUiColor: (String) -> Color,
    activeUiColor: Color = Color.Yellow,
    snapToWord: Boolean = false,
    penHighlightOnly: Boolean = false,
    fingerSelectionOnly: Boolean = false,
    pdfTheme: String = "light",
    viewportBitmap: android.graphics.Bitmap?,
    viewportRect: androidx.compose.ui.geometry.Rect?,
    onViewportChanged: (androidx.compose.ui.geometry.Rect, Int, Int) -> Unit,
    pan: Offset,
    zoom: Float,
    onPanZoomUpdate: (panChange: Offset, zoomChange: Float, centroid: Offset) -> Unit,
    onAnnotationCreated: (List<androidx.compose.ui.geometry.Rect>, String) -> Unit,
    onAnnotationTapped: (String?, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentAnnotations by rememberUpdatedState(annotations)
    val currentPan by rememberUpdatedState(pan)
    val currentZoom by rememberUpdatedState(zoom)
    
    // We observe the actual display size of the box to calculate the scaling ratio 
    // against the PDF's native size.
    var displaySize by remember { mutableStateOf(IntSize.Zero) }

    // Temporary state for the highlight currently being drawn
    var currentDragRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var currentNativeRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var dragStartPoint by remember { mutableStateOf<Offset?>(null) }
    var dragEndPoint by remember { mutableStateOf<Offset?>(null) }
    var currentExtractedText by remember { mutableStateOf<String?>(null) }
    var lastGestureEndTime by remember { mutableStateOf(0L) }

    val colorFilter = remember(pdfTheme) {
        when (pdfTheme) {
            "dark" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )))
            "sepia" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
            "nordic" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                -0.745f, 0f, 0f, 0f, 236f,
                0f, -0.733f, 0f, 0f, 239f,
                0f, 0f, -0.706f, 0f, 244f,
                0f, 0f, 0f, 1f, 0f
            )))
            "oled" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                -1.2f, 0f, 0f, 0f, 255f,
                0f, -1.2f, 0f, 0f, 255f,
                0f, 0f, -1.2f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )))
            "forest" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                -0.533f, 0f, 0f, 0f, 163f,
                0f, -0.588f, 0f, 0f, 188f,
                0f, 0f, -0.525f, 0f, 166f,
                0f, 0f, 0f, 1f, 0f
            )))
            else -> null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                displaySize = coordinates.size
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val isStylus = down.type == androidx.compose.ui.input.pointer.PointerType.Stylus || down.type == androidx.compose.ui.input.pointer.PointerType.Eraser
                    
                    var toolIsHighlight = !isStylus
                    if (penHighlightOnly) {
                        toolIsHighlight = isStylus
                    }
                    
                    var toolIsPanZoom = isStylus
                    if (fingerSelectionOnly) {
                        toolIsPanZoom = !isStylus
                    }
                    
                    if (toolIsPanZoom) {
                        var startCentroid = Offset.Zero
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes
                            
                            if (pointers.any { it.pressed }) {
                                val centroid = pointers.fold(Offset.Zero) { acc, p -> acc + p.position } / pointers.size.toFloat()
                                if (startCentroid == Offset.Zero) startCentroid = centroid
                                
                                if (pointers.size > 1) {
                                    val dist = (pointers[0].position - pointers[1].position).getDistance()
                                    val prevDist = (pointers[0].previousPosition - pointers[1].previousPosition).getDistance()
                                    val zoomChange = if (prevDist > 0) dist / prevDist else 1f
                                    onPanZoomUpdate(Offset.Zero, zoomChange, centroid)
                                } else {
                                    val panChange = centroid - startCentroid
                                    onPanZoomUpdate(panChange, 1f, centroid)
                                    startCentroid = centroid
                                }
                                pointers.forEach { it.consume() }
                            } else {
                                lastGestureEndTime = System.currentTimeMillis()
                                break
                            }
                        }
                    } else {
                        var isHighlighting = true
                        if (penHighlightOnly && !isStylus) isHighlighting = false
                        
                        if (!isHighlighting) return@awaitEachGesture
                        
                        dragStartPoint = (down.position - currentPan) / currentZoom
                        dragEndPoint = (down.position - currentPan) / currentZoom
                        currentDragRects = emptyList()
                        
                        var isTap = true
                        
                        while (true) {
                            val dragEvent = awaitPointerEvent()
                            if (dragEvent.changes.any { it.pressed }) {
                                if (dragEvent.changes.size > 1) {
                                    currentDragRects = emptyList()
                                    break
                                }
                                
                                val change = dragEvent.changes.first()
                                val diff = change.position - down.position
                                if (Math.hypot(diff.x.toDouble(), diff.y.toDouble()) > 10.0) {
                                    isTap = false
                                }
                                
                                change.consume()
                                dragEndPoint = (change.position - currentPan) / currentZoom
                                val start = dragStartPoint ?: continue
                                val end = (change.position - currentPan) / currentZoom
                                
                                val dragRect = Rect(
                                    left = minOf(start.x, end.x),
                                    top = minOf(start.y, end.y),
                                    right = maxOf(start.x, end.x),
                                    bottom = maxOf(start.y, end.y)
                                )

                                if (structuredText != null && pdfNativeWidth > 0 && displaySize.width > 0) {
                                    val renderScale = minOf(displaySize.width.toFloat() / pdfNativeWidth, displaySize.height.toFloat() / pdfNativeHeight)
                                    val renderedWidth = pdfNativeWidth * renderScale
                                    val renderedHeight = pdfNativeHeight * renderScale
                                    val offsetX = (displaySize.width - renderedWidth) / 2f
                                    val offsetY = (displaySize.height - renderedHeight) / 2f
                                    
                                    val flatChars = mutableListOf<Triple<StructuredText.TextLine, StructuredText.TextChar, Rect>>()
                                    
                                    for (block in structuredText.blocks) {
                                        for (line in block.lines) {
                                            for (char in line.chars) {
                                                val charBox = char.quad.toRect()
                                                val screenCx0 = (charBox.x0 - pdfNativeBoundsLeft) * renderScale + offsetX
                                                val screenCy0 = (charBox.y0 - pdfNativeBoundsTop) * renderScale + offsetY
                                                val screenCx1 = (charBox.x1 - pdfNativeBoundsLeft) * renderScale + offsetX
                                                val screenCy1 = (charBox.y1 - pdfNativeBoundsTop) * renderScale + offsetY
                                                
                                                val screenRect = Rect(screenCx0, screenCy0, screenCx1, screenCy1)
                                                flatChars.add(Triple(line, char, screenRect))
                                            }
                                        }
                                    }
                                    
                                    if (flatChars.isNotEmpty()) {
                                        fun getClosestCharIndex(target: Offset): Int {
                                            val lines = flatChars.groupBy { it.first }
                                            val lineScreenBounds = lines.map { (line, chars) ->
                                                val screenTop = chars.minOf { it.third.top }
                                                val screenBottom = chars.maxOf { it.third.bottom }
                                                val screenLeft = chars.minOf { it.third.left }
                                                val screenRight = chars.maxOf { it.third.right }
                                                line to Rect(screenLeft, screenTop, screenRight, screenBottom)
                                            }
                                            
                                            val verticallyOverlappingLines = lineScreenBounds.filter { target.y in it.second.top..it.second.bottom }
                                            
                                            val targetLine = if (verticallyOverlappingLines.isNotEmpty()) {
                                                verticallyOverlappingLines.minByOrNull {
                                                    if (target.x in it.second.left..it.second.right) 0f
                                                    else minOf(Math.abs(target.x - it.second.left), Math.abs(target.x - it.second.right))
                                                }!!.first
                                            } else {
                                                lineScreenBounds.minByOrNull {
                                                    val dy = if (target.y in it.second.top..it.second.bottom) 0f
                                                             else minOf(Math.abs(target.y - it.second.top), Math.abs(target.y - it.second.bottom))
                                                    val dx = if (target.x in it.second.left..it.second.right) 0f
                                                             else minOf(Math.abs(target.x - it.second.left), Math.abs(target.x - it.second.right))
                                                    dy * 1000f + dx
                                                }!!.first
                                            }
                                            
                                            val targetLineChars = flatChars.withIndex().filter { it.value.first == targetLine }
                                            return targetLineChars.minByOrNull { Math.abs(it.value.third.center.x - target.x) }?.index ?: 0
                                        }
                                        
                                        val startIdx = getClosestCharIndex(start)
                                        val endIdx = getClosestCharIndex(end)
                                        var minIdx = minOf(startIdx, endIdx)
                                        var maxIdx = maxOf(startIdx, endIdx)
                                        
                                        if (snapToWord) {
                                            while (minIdx > 0) {
                                                val charPrev = flatChars[minIdx - 1]
                                                val charCurr = flatChars[minIdx]
                                                if (charPrev.first != charCurr.first) break
                                                if (charCurr.third.left - charPrev.third.right > 2.0f * renderScale) break
                                                val c = charPrev.second.c.toChar()
                                                if (c.isWhitespace() || c == '.' || c == ',' || c == ';' || c == '?' || c == '!' || c == '(' || c == ')' || c == '[' || c == ']') break
                                                minIdx--
                                            }
                                            while (maxIdx < flatChars.size - 1) {
                                                val charNext = flatChars[maxIdx + 1]
                                                val charCurr = flatChars[maxIdx]
                                                if (charNext.first != charCurr.first) break
                                                if (charNext.third.left - charCurr.third.right > 2.0f * renderScale) break
                                                val c = charNext.second.c.toChar()
                                                if (c.isWhitespace() || c == '.' || c == ',' || c == ';' || c == '?' || c == '!' || c == '(' || c == ')' || c == '[' || c == ']') break
                                                maxIdx++
                                            }
                                        }
                                        
                                        val selectedByLine = mutableMapOf<StructuredText.TextLine, MutableList<StructuredText.TextChar>>()
                                        for (i in minIdx..maxIdx) {
                                            val (line, char, _) = flatChars[i]
                                            selectedByLine.getOrPut(line) { mutableListOf() }.add(char)
                                        }
                                        
                                        val snapRects = mutableListOf<Rect>()
                                        val nativeSnapRects = mutableListOf<Rect>()
                                        val sb = java.lang.StringBuilder()
                                        
                                        for ((line, chars) in selectedByLine) {
                                            if (chars.isEmpty()) continue
                                            var nLeft = Float.MAX_VALUE
                                            var nRight = Float.MIN_VALUE
                                            var lastRight = -1f
                                            
                                            for (char in chars) {
                                                val q = char.quad.toRect()
                                                nLeft = minOf(nLeft, q.x0)
                                                nRight = maxOf(nRight, q.x1)
                                                if (lastRight != -1f && q.x0 - lastRight > 2.0f) sb.append(" ")
                                                sb.append(char.c.toChar())
                                                lastRight = q.x1
                                            }
                                            sb.append("\n")
                                            
                                            val lineBbox = line.bbox
                                            val nTop = lineBbox.y0
                                            val nBottom = lineBbox.y1
                                            
                                            nativeSnapRects.add(Rect(nLeft, pdfNativeHeight - nBottom, nRight, pdfNativeHeight - nTop))
                                            
                                            val sLeft = (nLeft - pdfNativeBoundsLeft) * renderScale + offsetX
                                            val sTop = (nTop - pdfNativeBoundsTop) * renderScale + offsetY
                                            val sRight = (nRight - pdfNativeBoundsLeft) * renderScale + offsetX
                                            val sBottom = (nBottom - pdfNativeBoundsTop) * renderScale + offsetY
                                            snapRects.add(Rect(sLeft, sTop, sRight, sBottom))
                                        }
                                        
                                        currentExtractedText = sb.toString().trim()
                                        currentDragRects = snapRects
                                        currentNativeRects = nativeSnapRects
                                    }
                                } else {
                                    currentDragRects = listOf(dragRect)
                                    val fallbackScaleX = pdfNativeWidth / displaySize.width
                                    val fallbackScaleY = pdfNativeHeight / displaySize.height
                                    currentNativeRects = listOf(Rect(
                                        dragRect.left * fallbackScaleX + pdfNativeBoundsLeft,
                                        pdfNativeHeight - (dragRect.bottom * fallbackScaleY + pdfNativeBoundsTop),
                                        dragRect.right * fallbackScaleX + pdfNativeBoundsLeft,
                                        pdfNativeHeight - (dragRect.top * fallbackScaleY + pdfNativeBoundsTop)
                                    ))
                                }
                            } else {
                                if (isTap) {
                                    val localOffset = (down.position - currentPan) / currentZoom
                                    if ((fingerSelectionOnly && !isStylus) || (!fingerSelectionOnly)) {
                                        if (pdfNativeWidth > 0 && displaySize.width > 0) {
                                            val renderScale = minOf(displaySize.width.toFloat() / pdfNativeWidth, displaySize.height.toFloat() / pdfNativeHeight)
                                            val renderedWidth = pdfNativeWidth * renderScale
                                            val renderedHeight = pdfNativeHeight * renderScale
                                            val offsetX = (displaySize.width - renderedWidth) / 2f
                                            val offsetY = (displaySize.height - renderedHeight) / 2f
                                            
                                            val tappedAnn = currentAnnotations.find { ann ->
                                                if (ann.annotationType == "highlight" && ann.annotationPosition != null) {
                                                    val posStr = ann.annotationPosition
                                                    val pageIndexMatch = Regex("""\"pageIndex\"\s*:\s*(\d+)""").find(posStr)
                                                    val annPageIndex = pageIndexMatch?.groupValues?.get(1)?.toIntOrNull()
                                                    if (annPageIndex != null && annPageIndex != currentPage) return@find false
                                                    
                                                    val regex = Regex("""\[\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\s*,\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\s*,\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\s*,\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\s*\]""")
                                                    val matches = regex.findAll(posStr)
                                                    
                                                    var hit = false
                                                    for (match in matches) {
                                                        val (x1, y1, x2, y2) = match.destructured
                                                        val nativeLeft = x1.toFloatOrNull() ?: continue
                                                        val nativeTop = y1.toFloatOrNull() ?: continue
                                                        val nativeRight = x2.toFloatOrNull() ?: continue
                                                        val nativeBottom = y2.toFloatOrNull() ?: continue
                                                        
                                                        val nLeft = minOf(nativeLeft, nativeRight)
                                                        val nRight = maxOf(nativeLeft, nativeRight)
                                                        val yA = pdfNativeHeight - nativeTop
                                                        val yB = pdfNativeHeight - nativeBottom
                                                        val nTop = minOf(yA, yB)
                                                        val nBottom = maxOf(yA, yB)
                                                        
                                                        val sLeft = (nLeft - pdfNativeBoundsLeft) * renderScale + offsetX
                                                        val sTop = (nTop - pdfNativeBoundsTop) * renderScale + offsetY
                                                        val sRight = (nRight - pdfNativeBoundsLeft) * renderScale + offsetX
                                                        val sBottom = (nBottom - pdfNativeBoundsTop) * renderScale + offsetY
                                                        
                                                        val sRect = Rect(sLeft, sTop, sRight, sBottom)
                                                        if (sRect.inflate(20f).contains(localOffset)) {
                                                            hit = true
                                                            break
                                                        }
                                                    }
                                                    hit
                                                } else false
                                            }
                                            if (tappedAnn != null) {
                                                onAnnotationTapped(tappedAnn.key, down.position)
                                            } else {
                                                onAnnotationTapped(null, Offset.Zero)
                                            }
                                        }
                                    }
                                } else {
                                    if (currentNativeRects.isNotEmpty()) {
                                        onAnnotationCreated(currentNativeRects, currentExtractedText ?: "")
                                    }
                                }
                                currentDragRects = emptyList()
                                currentNativeRects = emptyList()
                                dragStartPoint = null
                                dragEndPoint = null
                                break
                            }
                        }
                    }
                }
            }
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = pan.x
                translationY = pan.y
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        ) {
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap.asImageBitmap(),
                    contentDescription = "PDF Page",
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            if (viewportBitmap != null && viewportRect != null && displaySize.width > 0 && displaySize.height > 0) {
                val renderScale = minOf(displaySize.width.toFloat() / pdfNativeWidth, displaySize.height.toFloat() / pdfNativeHeight)
                val renderedWidth = pdfNativeWidth * renderScale
                val renderedHeight = pdfNativeHeight * renderScale
                val offsetX = (displaySize.width - renderedWidth) / 2f
                val offsetY = (displaySize.height - renderedHeight) / 2f

                val left = (viewportRect.left - pdfNativeBoundsLeft) * renderScale + offsetX
                val top = (viewportRect.top - pdfNativeBoundsTop) * renderScale + offsetY
                val width = viewportRect.width * renderScale
                val height = viewportRect.height * renderScale

                val density = androidx.compose.ui.platform.LocalDensity.current
                Image(
                    bitmap = viewportBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = colorFilter,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(left.roundToInt(), top.roundToInt()) }
                        .size(
                            with(density) { width.toDp() },
                            with(density) { height.toDp() }
                        )
                )
            }
        
        LaunchedEffect(pan, zoom, lastGestureEndTime) {
            if (lastGestureEndTime > 0) {
                kotlinx.coroutines.delay(300)
                if (displaySize.width > 0 && displaySize.height > 0) {
                    val renderScale = minOf(displaySize.width.toFloat() / pdfNativeWidth, displaySize.height.toFloat() / pdfNativeHeight)
                    val renderedWidth = pdfNativeWidth * renderScale
                    val renderedHeight = pdfNativeHeight * renderScale
                    val offsetX = (displaySize.width - renderedWidth) / 2f
                    val offsetY = (displaySize.height - renderedHeight) / 2f
                    
                    val pdfScreenLeft = -pan.x / zoom - offsetX
                    val pdfScreenTop = -pan.y / zoom - offsetY
                    val pdfScreenRight = (displaySize.width - pan.x) / zoom - offsetX
                    val pdfScreenBottom = (displaySize.height - pan.y) / zoom - offsetY
                    
                    val nLeft = pdfScreenLeft / renderScale + pdfNativeBoundsLeft
                    val nTop = pdfScreenTop / renderScale + pdfNativeBoundsTop
                    val nRight = pdfScreenRight / renderScale + pdfNativeBoundsLeft
                    val nBottom = pdfScreenBottom / renderScale + pdfNativeBoundsTop
                    
                    val nativeRect = Rect(
                        maxOf(pdfNativeBoundsLeft, nLeft), 
                        maxOf(pdfNativeBoundsTop, nTop), 
                        minOf(pdfNativeBoundsLeft + pdfNativeWidth, nRight), 
                        minOf(pdfNativeBoundsTop + pdfNativeHeight, nBottom)
                    )
                    
                    val targetPixelWidth = ((nativeRect.right - nativeRect.left) * renderScale * zoom).roundToInt()
                    val targetPixelHeight = ((nativeRect.bottom - nativeRect.top) * renderScale * zoom).roundToInt()
                    
                    if (targetPixelWidth > 0 && targetPixelHeight > 0) {
                        onViewportChanged(nativeRect, targetPixelWidth, targetPixelHeight)
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val displayWidth = size.width
            val displayHeight = size.height

            if (displayWidth == 0f || displayHeight == 0f || pdfNativeWidth == 0f) return@Canvas

            val renderScale = minOf(displayWidth / pdfNativeWidth, displayHeight / pdfNativeHeight)
            val renderedWidth = pdfNativeWidth * renderScale
            val renderedHeight = pdfNativeHeight * renderScale
            val offsetX = (displayWidth - renderedWidth) / 2f
            val offsetY = (displayHeight - renderedHeight) / 2f

            val dragPath = Path()
            for (rect in currentDragRects) {
                dragPath.addRect(rect)
            }
            if (currentDragRects.isNotEmpty()) {
                drawPath(
                    path = dragPath,
                    color = activeUiColor.copy(alpha = 0.5f),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Multiply
                )
            }

            for (ann in annotations) {
                if (ann.annotationType == "highlight" && ann.annotationPosition != null) {
                    try {
                        val uiColor = getUiColor(ann.annotationColor ?: "#ffd400")
                        
                        val posStr = ann.annotationPosition
                        
                        val pageIndexMatch = Regex("""\"pageIndex\"\s*:\s*(\d+)""").find(posStr)
                        val annPageIndex = pageIndexMatch?.groupValues?.get(1)?.toIntOrNull()
                        
                        if (annPageIndex != currentPage) {
                            continue
                        }
                        
                        val regex = Regex("""\[\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\s*,\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\s*,\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\s*,\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\s*\]""")
                        val matches = regex.findAll(posStr)
                        
                        val rawRects = mutableListOf<Rect>()
                        for (match in matches) {
                            val (x1, y1, x2, y2) = match.destructured
                            val nativeLeft = x1.toFloatOrNull() ?: continue
                            val nativeTop = y1.toFloatOrNull() ?: continue
                            val nativeRight = x2.toFloatOrNull() ?: continue
                            val nativeBottom = y2.toFloatOrNull() ?: continue
                            
                            val nLeft = minOf(nativeLeft, nativeRight)
                            val nRight = maxOf(nativeLeft, nativeRight)
                            
                            val yA = pdfNativeHeight - nativeTop
                            val yB = pdfNativeHeight - nativeBottom
                            val nTop = minOf(yA, yB)
                            val nBottom = maxOf(yA, yB)
                            
                            val screenLeft = (nLeft - pdfNativeBoundsLeft) * renderScale + offsetX
                            val screenTop = (nTop - pdfNativeBoundsTop) * renderScale + offsetY
                            val screenRight = (nRight - pdfNativeBoundsLeft) * renderScale + offsetX
                            val screenBottom = (nBottom - pdfNativeBoundsTop) * renderScale + offsetY
                            
                            rawRects.add(Rect(screenLeft, screenTop, screenRight, screenBottom))
                        }
                        
                        val mergedRects = mutableListOf<Rect>()
                        val used = BooleanArray(rawRects.size)
                        
                        for (i in rawRects.indices) {
                            if (used[i]) continue
                            var currentRect = rawRects[i]
                            used[i] = true
                            
                            var changed = true
                            while (changed) {
                                changed = false
                                for (j in rawRects.indices) {
                                    if (used[j]) continue
                                    val otherRect = rawRects[j]
                                    
                                    val verticalOverlap = maxOf(0f, minOf(currentRect.bottom, otherRect.bottom) - maxOf(currentRect.top, otherRect.top))
                                    val minHeight = minOf(currentRect.height, otherRect.height)
                                    
                                    if (verticalOverlap > minHeight * 0.5f) {
                                        val horizontalGap = if (otherRect.left > currentRect.right) {
                                            otherRect.left - currentRect.right
                                        } else if (currentRect.left > otherRect.right) {
                                            currentRect.left - otherRect.right
                                        } else {
                                            0f
                                        }
                                        
                                        val maxGap = 20f * renderScale
                                        if (horizontalGap <= maxGap) {
                                            currentRect = Rect(
                                                minOf(currentRect.left, otherRect.left),
                                                minOf(currentRect.top, otherRect.top),
                                                maxOf(currentRect.right, otherRect.right),
                                                maxOf(currentRect.bottom, otherRect.bottom)
                                            )
                                            used[j] = true
                                            changed = true
                                        }
                                    }
                                }
                            }
                            mergedRects.add(currentRect)
                        }
                        
                        val annPath = Path()
                        for (rect in mergedRects) {
                            val paddedRect = rect.inflate(2f)
                            val radius = minOf(paddedRect.height / 2f, 8f * renderScale)
                            annPath.addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                rect = paddedRect,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                            ))
                        }
                        
                        drawPath(
                            path = annPath,
                            color = uiColor,
                            blendMode = androidx.compose.ui.graphics.BlendMode.Multiply
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        }
    }
}
