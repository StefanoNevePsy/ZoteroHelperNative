package com.example.zoterohelpernative.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.Icons

@Composable
fun BackgroundCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        // Base dark background
        drawRect(color = Color(0xFF12121A))
        
        // Blurred blobs
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
