package com.example.zoterohelpernative.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.zoterohelpernative.data.MappedColor
import com.example.zoterohelpernative.data.Palette

// Impeccable Dark Theme Palette (Stylus-first, Dark Mode focused)
val BackgroundDark = Color(0xFF0A0A0B)
val SurfaceDark = Color(0xFF141415)
val SurfaceVariantDark = Color(0xFF1F1F22)

val TextPrimaryDark = Color(0xFFEDEDED)
val TextSecondaryDark = Color(0xFFA1A1AA)

// Accent Colors (Zotero-compatible map)
val ZoteroYellow = Color(0xFFFFD400)
val ZoteroRed = Color(0xFFFF6666)
val ZoteroGreen = Color(0xFF5FB236)
val ZoteroBlue = Color(0xFF2EA8E5)
val ZoteroPurple = Color(0xFFA28AE5)
val ZoteroMagenta = Color(0xFFE56EEE)
val ZoteroOrange = Color(0xFFF19837)
val ZoteroGray = Color(0xFFAAAAAA)

val AccentPrimary = ZoteroYellow
val AccentSecondary = Color(0xFF38BDF8) // Electric Blue for UI active states

// Neutral outlines and dividers
val OutlineDark = Color(0xFF27272A)

// Extension to map a Compose Color back to exactly what Zotero expects for highlights
fun Color.toZoteroHex(): String {
    return when (this) {
        ZoteroYellow -> "#ffd400"
        ZoteroRed -> "#ff6666"
        ZoteroGreen -> "#5fb236"
        ZoteroBlue -> "#2ea8e5"
        ZoteroPurple -> "#a28ae5"
        ZoteroMagenta -> "#e56eee"
        ZoteroOrange -> "#f19837"
        ZoteroGray -> "#aaaaaa"
        else -> String.format("#%06x", (0xFFFFFF and this.toArgb())).lowercase()
    }
}

val DEFAULT_PALETTES = mapOf(
    "zotero" to Palette(
        id = "zotero",
        name = "Zotero",
        colors = listOf(
            MappedColor("#ffd400", "#ffd400", "Zotero Yellow"),
            MappedColor("#ff6666", "#ff6666", "Zotero Red"),
            MappedColor("#5fb236", "#5fb236", "Zotero Green"),
            MappedColor("#2ea8e5", "#2ea8e5", "Zotero Blue"),
            MappedColor("#a28ae5", "#a28ae5", "Zotero Purple")
        )
    ),
    "stabilo" to Palette(
        id = "stabilo",
        name = "Pastel",
        colors = listOf(
            MappedColor("#ffd400", "#FDFFB4", "Pastel Yellow"),
            MappedColor("#ff6666", "#F7C2D6", "Pastel Pink"),
            MappedColor("#5fb236", "#A7E8C8", "Pastel Green"),
            MappedColor("#2ea8e5", "#C3EFFC", "Pastel Blue"),
            MappedColor("#a28ae5", "#C3BBEC", "Pastel Lilac")
        )
    ),
    "neon" to Palette(
        id = "neon",
        name = "Neon",
        colors = listOf(
            MappedColor("#ffd400", "#F4EA2A", "Neon Yellow"),
            MappedColor("#ff6666", "#FF3366", "Neon Pink"),
            MappedColor("#5fb236", "#39FF14", "Neon Green"),
            MappedColor("#2ea8e5", "#00FFFF", "Cyan"),
            MappedColor("#a28ae5", "#B026FF", "Neon Purple")
        )
    ),
    "earthy" to Palette(
        id = "earthy",
        name = "Earthy",
        colors = listOf(
            MappedColor("#ffd400", "#E1C699", "Ochre"),
            MappedColor("#ff6666", "#C47C76", "Terracotta"),
            MappedColor("#5fb236", "#8F9779", "Sage"),
            MappedColor("#2ea8e5", "#7D9FAD", "Slate"),
            MappedColor("#a28ae5", "#8E7D8A", "Dusty Plum")
        )
    )
)
