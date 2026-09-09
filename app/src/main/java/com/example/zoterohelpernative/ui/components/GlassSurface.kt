package com.example.zoterohelpernative.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.zoterohelpernative.theme.AppColors
import com.example.zoterohelpernative.theme.Radius
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid Glass — the *functional* layer.
 *
 * Per the HIG (`.claude/skills/apple-design/references/hig/liquid-glass.md`) the
 * interface is split in two planes:
 *   - the content layer (lists, cards, text) → [ContentSurface], no blur;
 *   - the functional layer (bars, sidebars, popovers, floating controls) → this
 *     composable, which blurs and tints whatever scrolls beneath it.
 *
 * Using glass for content is explicitly discouraged: it "creates unnecessary
 * complexity and confusing visual hierarchy" — and costs one blur pass per item.
 */

/**
 * HazeState of the screen: composables marked with Modifier.hazeSource(state)
 * (the animated background, the PDF canvas) are what the glass refracts.
 * When null, glass falls back to an opaque tint so text stays legible.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

enum class GlassVariant {
    /**
     * Blurs and adjusts luminosity to keep text legible: sidebars, popovers,
     * alerts, anything with significant text. Blur 20-40dp, opacity 0.6-0.8.
     */
    Regular,

    /**
     * Highly translucent, for controls floating above media (the PDF page).
     * Blur 10-20dp, opacity 0.3-0.5. Pair with [dimmed] over bright content.
     */
    Clear,

    /** Bars and toolbars: regular legibility with a lighter footprint. */
    Chrome
}

private val GlassVariant.blurRadius: Dp
    get() = when (this) {
        GlassVariant.Regular -> 32.dp
        GlassVariant.Clear -> 16.dp
        GlassVariant.Chrome -> 24.dp
    }

private val GlassVariant.tint: Color
    get() = when (this) {
        GlassVariant.Regular -> AppColors.Glass.RegularTint
        GlassVariant.Clear -> AppColors.Glass.ClearTint
        GlassVariant.Chrome -> AppColors.Glass.ChromeTint
    }

/** Used when the platform can't blur (Android < 12): opaque enough to stay readable. */
private val GlassVariant.fallbackTint: Color
    get() = when (this) {
        GlassVariant.Regular -> Color(0xF01A1F2B)
        GlassVariant.Clear -> Color(0xC0141821)
        GlassVariant.Chrome -> Color(0xE0141821)
    }

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Regular,
    shape: Shape = RoundedCornerShape(Radius.xxl),
    tint: Color? = null,
    borderColor: Color = AppColors.Glass.Border,
    borderWidth: Dp = 1.dp,
    /** Dark scrim under the glass: HIG requires it for clear glass over bright content. */
    dimmed: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = LocalHazeState.current
    val effectiveTint = tint ?: variant.tint

    val surfaceModifier = if (hazeState != null) {
        Modifier.hazeEffect(state = hazeState) {
            this.blurRadius = variant.blurRadius
            backgroundColor = AppColors.Background.GlassBase
            tints = buildList {
                if (dimmed) add(HazeTint(AppColors.Glass.Dim))
                add(HazeTint(effectiveTint))
            }
            this.fallbackTint = HazeTint(variant.fallbackTint)
            noiseFactor = 0.05f
        }
    } else {
        Modifier.background(variant.fallbackTint)
    }

    // Specular edge: brightest where light lands (top-leading), softest opposite.
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
            .then(surfaceModifier)
            .drawBehind {
                // Inner sheen from the top edge + rim light on the bottom lip
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.08f),
                        0.30f to Color.Transparent,
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0.88f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.05f),
                        startY = 0f,
                        endY = size.height
                    )
                )
            }
            .border(borderWidth, borderBrush, shape),
        content = content
    )
}

/**
 * Content layer surface: a standard material (opaque fill + hairline separator),
 * no blur. This is what cards, list rows and settings sections must use.
 */
@Composable
fun ContentSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.l),
    color: Color = AppColors.Fill.Secondary,
    borderColor: Color = AppColors.Separator,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(borderWidth, borderColor, shape),
        content = content
    )
}

/**
 * Scroll edge effect: a progressive blur strip that separates scrolling content
 * from the control area, instead of a hard background band.
 * HIG Layout: "Differentiate controls from content. Instead of a background, use
 * a scroll edge effect to provide a transition between content and the control area."
 */
@Composable
fun ScrollEdge(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    fromTop: Boolean = true
) {
    val hazeState = LocalHazeState.current ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(state = hazeState) {
                blurRadius = 20.dp
                backgroundColor = AppColors.Background.GlassBase
                tints = listOf(HazeTint(Color(0x33101623)))
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = if (fromTop) 1f else 0f,
                    endIntensity = if (fromTop) 0f else 1f
                )
            }
    )
}

/**
 * Backwards-compatible entry point. Existing call sites keep working; the
 * `color` they pass is honored as the glass tint.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.xxl),
    color: Color = AppColors.Glass.RegularTint,
    borderColor: Color = AppColors.Glass.Border,
    borderWidth: Dp = 1.dp,
    blurRadius: Dp = 24.dp,
    variant: GlassVariant = GlassVariant.Regular,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlass(
        modifier = modifier,
        variant = variant,
        shape = shape,
        // A fully opaque tint would hide the blur entirely: cap it.
        tint = if (color.alpha > 0.80f) color.copy(alpha = 0.80f) else color,
        borderColor = borderColor,
        borderWidth = borderWidth,
        content = content
    )
}
