package com.example.zoterohelpernative.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import com.example.zoterohelpernative.theme.Backdrop
import com.example.zoterohelpernative.theme.LocalAppPalette
import kotlin.random.Random

/**
 * The content-plane backdrop the glass layer refracts. Its look follows the
 * active palette: soft glows for Notte, warm paper with print grain for
 * PsyDiary ("texture tattili, da carta e da stampa").
 */
@Composable
fun BackgroundCanvas(modifier: Modifier = Modifier) {
    val palette = LocalAppPalette.current
    when (palette.backdrop) {
        Backdrop.Aurora -> AuroraBackdrop(modifier)
        Backdrop.Paper -> PaperBackdrop(
            paper = palette.backgroundPrimary,
            isDark = palette.isDark,
            modifier = modifier
        )
    }
}

@Composable
private fun AuroraBackdrop(modifier: Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = Color(0xFF12121A))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF60A5FA).copy(alpha = 0.3f), Color.Transparent),
                center = Offset(w * 0.2f, h * 0.3f),
                radius = w * 0.6f
            ),
            center = Offset(w * 0.2f, h * 0.3f),
            radius = w * 0.6f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFC084FC).copy(alpha = 0.2f), Color.Transparent),
                center = Offset(w * 0.8f, h * 0.7f),
                radius = w * 0.5f
            ),
            center = Offset(w * 0.8f, h * 0.7f),
            radius = w * 0.5f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF34D399).copy(alpha = 0.15f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.9f),
                radius = w * 0.4f
            ),
            center = Offset(w * 0.5f, h * 0.9f),
            radius = w * 0.4f
        )
    }
}

@Composable
private fun PaperBackdrop(paper: Color, isDark: Boolean, modifier: Modifier) {
    // Grain tile built once per appearance, then repeated by the shader
    val grain = remember(isDark) { grainTile(isDark) }
    val grainBrush = remember(grain) { ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)) }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = paper)
        // Faint vignette: the sheet is lit from the top
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (isDark) 0.02f else 0.18f),
                0.6f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (isDark) 0.18f else 0.04f)
            )
        )
        drawRect(brush = grainBrush)
    }
}

/**
 * Print grain: sparse specks of ink on paper (dark specks on light, light on
 * dark), mirroring PsyDiary's multiply/screen grain at low opacity. Kept
 * subtle enough not to cost legibility.
 */
private fun grainTile(isDark: Boolean, size: Int = 160): ImageBitmap {
    val random = Random(42) // stable pattern: no shimmer between recompositions
    val pixels = IntArray(size * size) { 0 }
    val (r, g, b) = if (isDark) Triple(0xE5, 0xDD, 0xD0) else Triple(0x1A, 0x21, 0x31)
    val maxAlpha = if (isDark) 22 else 30
    for (i in pixels.indices) {
        if (random.nextFloat() < 0.38f) {
            val a = random.nextInt(maxAlpha)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}
