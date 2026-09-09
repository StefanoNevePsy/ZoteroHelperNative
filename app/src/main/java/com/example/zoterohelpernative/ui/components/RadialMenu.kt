package com.example.zoterohelpernative.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.data.MappedColor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import com.composables.icons.lucide.*
import com.example.zoterohelpernative.ui.icons.IconResolver
import com.example.zoterohelpernative.theme.AppColors

fun safeParseColor(hex: String, defaultColor: Color = Color.White): Color {
    return try {
        Color(android.graphics.Color.parseColor(if (!hex.startsWith("#")) "#$hex" else hex))
    } catch (e: Exception) {
        defaultColor
    }
}

@Composable
fun RadialMenuCapacitor(
    visible: Boolean,
    position: Offset,
    activeToolId: String,
    activeColor: Color,
    level: String,
    activePaletteColors: List<MappedColor>,
    squaredHighlighter: Boolean,
    snapToWord: Boolean,
    isFullscreen: Boolean,
    toolIcons: Map<String, String> = emptyMap(),
    onLevelChange: (String) -> Unit,
    onToolSelect: (String) -> Unit,
    onColorSelect: (MappedColor) -> Unit,
    onCyclePalette: () -> Unit,
    onUndo: () -> Unit,
    onToggleSquared: () -> Unit,
    onToggleSnap: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitPage: () -> Unit,
    onSetPdfTheme: (String) -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "radial_base_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .scale(scale)
        ) {
            // Backdrop blur circle (w-64 h-64 in Tailwind = 256px)
            Box(
                modifier = Modifier
                    .offset(x = (-128).dp, y = (-128).dp)
                    .size(256.dp)
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = AppColors.Glass.Dim.copy(alpha = 0.20f),
                    borderColor = Color.Transparent,
                    borderWidth = 0.dp,
                    blurRadius = 8.dp // backdrop-blur-sm
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }

            // Central Button (w-16 h-16 = 64px)
            Box(
                modifier = Modifier
                    .offset(x = (-32).dp, y = (-32).dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.Background.GlassBase) // bg-gray-900
                    .border(2.dp, AppColors.Glass.Border, CircleShape) // border-white/20
                    .clickable {
                        if (level == "root") onClose() else onLevelChange("root")
                    },
                contentAlignment = Alignment.Center
            ) {
                if (level == "root") {
                    // Pulsing blue dot
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(AppColors.Accent, CircleShape) // bg-blue-500
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Items Radius
            val RADIUS = 100f
            val itemScale by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                label = "item_scale"
            )

            if (level == "root") {
                val rootItems = listOf(
                    Triple("colors", IconResolver.resolve(toolIcons["tool_palette"], Icons.Outlined.Palette), 270f),
                    Triple("tools", IconResolver.resolve(toolIcons["tool_highlighter"], Lucide.Highlighter), 30f),
                    Triple("view", Icons.Outlined.Visibility, 150f)
                )

                rootItems.forEach { (id, icon, angleDeg) ->
                    val angleRad = angleDeg * (Math.PI / 180)
                    val x = cos(angleRad) * RADIUS
                    val y = sin(angleRad) * RADIUS

                    Box(
                        modifier = Modifier
                            .offset(x = x.dp - 24.dp, y = y.dp - 24.dp) // w-12 h-12 = 48px
                            .size(48.dp)
                            .scale(itemScale)
                            .clip(CircleShape)
                            .background(AppColors.Glass.ChromeTint) // bg-black/80
                            .border(1.dp, AppColors.Separator, CircleShape) // border-white/10
                            .clickable { onLevelChange(id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = id,
                            tint = AppColors.Label.Secondary, // text-gray-300
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else if (level == "colors") {
                val startAngle = 180f
                val angleStep = if (activePaletteColors.size > 1) 180f / (activePaletteColors.size - 1) else 0f

                activePaletteColors.forEachIndexed { index, color ->
                    val angleRad = (startAngle + index * angleStep) * (Math.PI / 180)
                    val x = cos(angleRad) * RADIUS
                    val y = sin(angleRad) * RADIUS
                    val parsedColor = safeParseColor(color.uiHex)
                    val isActiveColor = activeColor == parsedColor

                    Box(
                        modifier = Modifier
                            .offset(x = x.dp - 28.dp, y = y.dp - 28.dp) // w-14 h-14 = 56px
                            .size(56.dp)
                            .scale(if (isActiveColor) itemScale * 1.1f else itemScale)
                            .clip(CircleShape)
                            .background(parsedColor)
                            .border(if (isActiveColor) 4.dp else 0.dp, if (isActiveColor) AppColors.Label.Tertiary else Color.Transparent, CircleShape)
                            .clickable {
                                onColorSelect(color)
                                onToolSelect("HIGHLIGHTER")
                                onClose()
                            }
                    )
                }

                // Cycle Palette Button (120 deg)
                val cycleAngleRad = 120f * (Math.PI / 180)
                Box(
                    modifier = Modifier
                        .offset(x = (cos(cycleAngleRad) * RADIUS).dp - 20.dp, y = (sin(cycleAngleRad) * RADIUS).dp - 20.dp)
                        .size(40.dp)
                        .scale(itemScale)
                        .clip(CircleShape)
                        .background(AppColors.Glass.ChromeTint)
                        .border(1.dp, AppColors.Separator, CircleShape)
                        .clickable { onCyclePalette() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Cycle Palette", tint = AppColors.Label.Secondary, modifier = Modifier.size(16.dp))
                }

                // Undo Button (150 deg)
                val undoAngleRad = 150f * (Math.PI / 180)
                Box(
                    modifier = Modifier
                        .offset(x = (cos(undoAngleRad) * RADIUS).dp - 20.dp, y = (sin(undoAngleRad) * RADIUS).dp - 20.dp)
                        .size(40.dp)
                        .scale(itemScale)
                        .clip(CircleShape)
                        .background(AppColors.Glass.ChromeTint)
                        .border(1.dp, AppColors.Separator, CircleShape)
                        .clickable { onUndo(); onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IconResolver.resolve(toolIcons["tool_undo"], Lucide.Undo), contentDescription = "Undo", tint = AppColors.Label.Secondary, modifier = Modifier.size(16.dp))
                }
                
                // Erase Button (30 deg)
                val eraseAngleRad = 30f * (Math.PI / 180)
                val isEraser = activeToolId == "ERASER"
                Box(
                    modifier = Modifier
                        .offset(x = (cos(eraseAngleRad) * RADIUS).dp - 20.dp, y = (sin(eraseAngleRad) * RADIUS).dp - 20.dp)
                        .size(40.dp)
                        .scale(itemScale)
                        .clip(CircleShape)
                        .background(AppColors.Glass.ChromeTint)
                        .border(if (isEraser) 2.dp else 1.dp, if (isEraser) AppColors.Status.Danger.copy(alpha = 0.5f) else AppColors.Separator, CircleShape)
                        .clickable {
                            onToolSelect(if (isEraser) "HIGHLIGHTER" else "ERASER")
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IconResolver.resolve(toolIcons["tool_eraser"], Lucide.Eraser), contentDescription = "Eraser", tint = if (isEraser) AppColors.Status.Danger else AppColors.Label.Secondary, modifier = Modifier.size(16.dp))
                }
                
                // Squared Highlight (90 deg)
                val sqAngleRad = 90f * (Math.PI / 180)
                Box(
                    modifier = Modifier
                        .offset(x = (cos(sqAngleRad) * RADIUS).dp - 20.dp, y = (sin(sqAngleRad) * RADIUS).dp - 20.dp)
                        .size(40.dp)
                        .scale(itemScale)
                        .clip(CircleShape)
                        .background(AppColors.Glass.ChromeTint)
                        .border(if (squaredHighlighter) 2.dp else 1.dp, if (squaredHighlighter) AppColors.Accent.copy(alpha = 0.5f) else AppColors.Separator, CircleShape)
                        .clickable { onToggleSquared() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IconResolver.resolve(toolIcons["tool_square"], Lucide.Square), contentDescription = "Squared", tint = if (squaredHighlighter) AppColors.Accent else AppColors.Label.Secondary, modifier = Modifier.size(16.dp))
                }
                
                // Snap to Word (60 deg)
                val snapAngleRad = 60f * (Math.PI / 180)
                Box(
                    modifier = Modifier
                        .offset(x = (cos(snapAngleRad) * RADIUS).dp - 20.dp, y = (sin(snapAngleRad) * RADIUS).dp - 20.dp)
                        .size(40.dp)
                        .scale(itemScale)
                        .clip(CircleShape)
                        .background(AppColors.Glass.ChromeTint)
                        .border(if (snapToWord) 2.dp else 1.dp, if (snapToWord) AppColors.Accent.copy(alpha = 0.5f) else AppColors.Separator, CircleShape)
                        .clickable { onToggleSnap() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.FormatSize, contentDescription = "Snap to Word", tint = if (snapToWord) AppColors.Accent else AppColors.Label.Secondary, modifier = Modifier.size(16.dp))
                }
                
            } else if (level == "tools") {
                val tools = listOf(
                    Triple("HIGHLIGHTER", IconResolver.resolve(toolIcons["tool_highlighter"], Lucide.Highlighter), 225f),
                    Triple("UNDERLINE", IconResolver.resolve(toolIcons["tool_underline"], Lucide.Underline), 90f),
                    Triple("ERASER", IconResolver.resolve(toolIcons["tool_eraser"], Lucide.Eraser), 315f),
                    Triple("SHAPE", IconResolver.resolve(toolIcons["tool_square"], Lucide.Square), 0f),
                    Triple("UNDO", IconResolver.resolve(toolIcons["tool_undo"], Lucide.Undo), 135f)
                )

                tools.forEach { (id, icon, angleDeg) ->
                    val angleRad = angleDeg * (Math.PI / 180)
                    val x = cos(angleRad) * RADIUS
                    val y = sin(angleRad) * RADIUS
                    val isActive = activeToolId == id

                    Box(
                        modifier = Modifier
                            .offset(x = x.dp - 28.dp, y = y.dp - 28.dp) // w-14 h-14 = 56px
                            .size(56.dp)
                            .scale(itemScale)
                            .clip(CircleShape)
                            .background(if (isActive) AppColors.Accent else AppColors.Glass.ChromeTint) // bg-blue-500 or bg-black/80
                            .border(1.dp, if (isActive) AppColors.Accent else AppColors.Separator, CircleShape)
                            .clickable {
                                if (id == "UNDO") {
                                    onUndo()
                                    onClose()
                                } else {
                                    onToolSelect(id)
                                    onClose()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = id,
                            tint = if (isActive) Color.White else AppColors.Label.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
             } else if (level == "view") {
                 val tools = listOf(
                    Triple("ZOOM_IN", IconResolver.resolve(toolIcons["tool_zoom_in"], Lucide.ZoomIn), 315f),
                    Triple("ZOOM_OUT", IconResolver.resolve(toolIcons["tool_zoom_out"], Lucide.ZoomOut), 45f),
                    Triple("FIT_PAGE", Lucide.Maximize, 0f),
                    Triple("TOGGLE_SIDEBAR", IconResolver.resolve(toolIcons["tool_menu"], Lucide.Menu), 225f),
                    Triple("PAN", IconResolver.resolve(toolIcons["tool_hand"], Lucide.Hand), 270f),
                    Triple("UNDO", IconResolver.resolve(toolIcons["tool_undo"], Lucide.Undo), 135f)
                )

                tools.forEach { (id, icon, angleDeg) ->
                    val angleRad = angleDeg * (Math.PI / 180)
                    val x = cos(angleRad) * RADIUS
                    val y = sin(angleRad) * RADIUS

                    Box(
                        modifier = Modifier
                            .offset(x = x.dp - 28.dp, y = y.dp - 28.dp)
                            .size(56.dp)
                            .scale(itemScale)
                            .clip(CircleShape)
                            .background(AppColors.Glass.ChromeTint)
                            .border(1.dp, if (id == "FULLSCREEN" && isFullscreen) AppColors.Accent else AppColors.Separator, CircleShape)
                            .clickable {
                                if (id == "UNDO") {
                                    onUndo()
                                    onClose()
                                } else if (id == "FULLSCREEN") {
                                    onToggleFullscreen()
                                    onClose()
                                } else if (id == "ZOOM_IN") {
                                    onZoomIn()
                                } else if (id == "ZOOM_OUT") {
                                    onZoomOut()
                                } else if (id == "FIT_PAGE") {
                                    onFitPage()
                                    onClose()
                                } else if (id == "THEMES") {
                                    onLevelChange("themes")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = id,
                            tint = AppColors.Label.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
             } else if (level == "themes") {
                 val themes = listOf(
                     Triple("light", Icons.Outlined.WbSunny, 270f),
                     Triple("dark", Icons.Outlined.DarkMode, 330f),
                     Triple("sepia", Icons.Outlined.AutoStories, 30f),
                     Triple("nordic", Icons.Outlined.AcUnit, 90f),
                     Triple("oled", Icons.Outlined.Brightness3, 150f),
                     Triple("forest", Icons.Outlined.Nature, 210f)
                 )

                 themes.forEach { (id, icon, angleDeg) ->
                     val angleRad = angleDeg * (Math.PI / 180)
                     val x = cos(angleRad) * RADIUS
                     val y = sin(angleRad) * RADIUS

                     Box(
                         modifier = Modifier
                             .offset(x = x.dp - 28.dp, y = y.dp - 28.dp)
                             .size(56.dp)
                             .scale(itemScale)
                             .clip(CircleShape)
                             .background(AppColors.Glass.ChromeTint)
                             .border(1.dp, AppColors.Separator, CircleShape)
                             .clickable {
                                 onSetPdfTheme(id)
                                 onLevelChange("view")
                             },
                         contentAlignment = Alignment.Center
                     ) {
                         Icon(
                             imageVector = icon,
                             contentDescription = id,
                             tint = AppColors.Label.Secondary,
                             modifier = Modifier.size(20.dp)
                         )
                     }
                 }
            }
        }
    }
}
