package com.example.zoterohelpernative.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.*

object IconResolver {

    private val materialIcons = mapOf(
        "material_edit" to Icons.Outlined.Edit,
        "material_brush" to Icons.Outlined.Brush,
        "material_highlight" to Icons.Outlined.Highlight,
        "material_clear" to Icons.Outlined.Clear,
        "material_undo" to Icons.AutoMirrored.Outlined.Undo,
        "material_settings" to Icons.Outlined.Settings,
        "material_palette" to Icons.Outlined.Palette,
        "material_rectangle" to Icons.Outlined.Rectangle,
        "material_zoom_in" to Icons.Outlined.ZoomIn,
        "material_zoom_out" to Icons.Outlined.ZoomOut,
        "material_fullscreen" to Icons.Outlined.Fullscreen,
        "material_menu" to Icons.Outlined.Menu,
        "material_arrow_back" to Icons.AutoMirrored.Outlined.ArrowBack,
        "material_delete" to Icons.Outlined.Delete
    )

    private val lucideIcons = mapOf(
        "lucide_highlighter" to Lucide.Highlighter,
        "lucide_pen" to Lucide.Pen,
        "lucide_eraser" to Lucide.Eraser,
        "lucide_palette" to Lucide.Palette,
        "lucide_undo" to Lucide.Undo,
        "lucide_square" to Lucide.Square,
        "lucide_settings" to Lucide.Settings,
        "lucide_maximize" to Lucide.Maximize,
        "lucide_zoom_in" to Lucide.ZoomIn,
        "lucide_zoom_out" to Lucide.ZoomOut,
        "lucide_menu" to Lucide.Menu,
        "lucide_chevron_left" to Lucide.ChevronLeft,
        "lucide_trash" to Lucide.Trash
    )

    val allIcons = materialIcons + lucideIcons

    fun resolve(name: String?, fallback: ImageVector): ImageVector {
        if (name.isNullOrEmpty()) return fallback
        return allIcons[name] ?: fallback
    }
}
