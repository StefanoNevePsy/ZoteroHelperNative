package com.example.zoterohelpernative.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.Icons

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = Color(0x33FFFFFF), // Base translucent tint
    borderColor: Color = Color(0x4DFFFFFF),
    borderWidth: Dp = 1.dp,
    blurRadius: Dp = 16.dp, // Kept for compatibility, but unused to prevent content blur
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(borderWidth, borderColor, shape)
    ) {
        content()
    }
}
