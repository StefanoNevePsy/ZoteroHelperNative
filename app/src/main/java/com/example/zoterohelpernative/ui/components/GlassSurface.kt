package com.example.zoterohelpernative.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * HazeState of the screen: composables marked with Modifier.hazeSource(state)
 * (the animated background, the PDF canvas) are what GlassSurface blurs.
 * When null, GlassSurface falls back to a flat translucent look.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Liquid-glass surface: real backdrop blur of what lies behind (frost), a tint,
 * film grain, a specular top-left edge light and a soft inner sheen — adapted
 * from the layering used by samasante/liquid-glass (frost + tint + sheen + rim).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = Color(0x33FFFFFF), // Base translucent tint
    borderColor: Color = Color(0x4DFFFFFF),
    borderWidth: Dp = 1.dp,
    blurRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = LocalHazeState.current

    val backgroundModifier = if (hazeState != null) {
        // A fully opaque tint would hide the blur entirely: cap the alpha and
        // let the frosted backdrop provide the remaining contrast.
        val tint = if (color.alpha > 0.75f) color.copy(alpha = 0.75f) else color
        Modifier.hazeEffect(state = hazeState) {
            this.blurRadius = blurRadius.coerceAtLeast(12.dp)
            backgroundColor = Color(0xFF0B0F17)
            tints = listOf(HazeTint(tint))
            noiseFactor = 0.06f
        }
    } else {
        Modifier.background(color)
    }

    // Specular edge light: bright where the light hits (top-left), faint in the
    // middle, softly lit again on the opposite rim.
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            lerp(borderColor, Color.White, 0.45f).copy(alpha = (borderColor.alpha * 1.9f).coerceAtMost(0.9f)),
            borderColor.copy(alpha = borderColor.alpha * 0.35f),
            borderColor.copy(alpha = (borderColor.alpha * 1.2f).coerceAtMost(0.7f))
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    Box(
        modifier = modifier
            .clip(shape)
            .then(backgroundModifier)
            .drawBehind {
                // Inner sheen: light falling from the top edge
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.09f),
                        0.30f to Color.Transparent,
                        startY = 0f,
                        endY = size.height
                    )
                )
                // Rim light on the bottom lip
                drawRect(
                    brush = Brush.verticalGradient(
                        0.88f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.05f),
                        startY = 0f,
                        endY = size.height
                    )
                )
            }
            .border(borderWidth, borderBrush, shape)
    ) {
        content()
    }
}
