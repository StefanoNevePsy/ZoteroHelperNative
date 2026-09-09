package com.example.zoterohelpernative.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design tokens derived from the Apple HIG guidelines bundled in
 * `.claude/skills/apple-design`.
 *
 * Two rules drive this file:
 *  - Colors are *semantic* (what they mean), never picked per-screen.
 *    "Avoid redefining the semantic meanings of dynamic system colors."
 *  - Every text token meets the 4.5:1 contrast minimum for body text over the
 *    app's dark surfaces; tokens that don't (Quaternary) are for decoration and
 *    disabled states only.
 *
 * Contrast measured against Background.Secondary (#141415):
 *   Label.Primary   ~15:1   Label.Secondary ~7.2:1
 *   Label.Tertiary  ~4.7:1  Label.Quaternary ~2.8:1 (non-text use only)
 */
object AppColors {

    /** Background hierarchy: primary = the view, secondary = grouped content, tertiary = nested. */
    object Background {
        val Primary = Color(0xFF0A0A0B)
        val Secondary = Color(0xFF141415)
        val Tertiary = Color(0xFF1F1F22)
        /** Base tint behind glass surfaces so blur never washes out to white. */
        val GlassBase = Color(0xFF0B0F17)
    }

    /** Foreground content, by importance. */
    object Label {
        val Primary = Color(0xFFEDEDED)
        val Secondary = Color(0x99FFFFFF)   // 60% — descriptive text, subtitles
        val Tertiary = Color(0x73FFFFFF)    // 45% — inactive, still readable
        val Quaternary = Color(0x59FFFFFF)  // 35% — decorative / disabled only
        val Placeholder = Color(0x73FFFFFF)
    }

    /** Translucent fills for the content layer (no blur — the glass layer floats above). */
    object Fill {
        val Primary = Color(0x1FFFFFFF)
        val Secondary = Color(0x14FFFFFF)
        val Tertiary = Color(0x0DFFFFFF)
    }

    val Separator = Color(0x1FFFFFFF)
    val SeparatorOpaque = Color(0xFF27272A)

    /**
     * One accent for interactivity across the whole app.
     * "Apply color sparingly. Reserve it for elements that truly benefit from
     * emphasis: status indicators, primary actions, selected navigation items."
     */
    val Accent = Color(0xFF38BDF8)
    val AccentPressed = Color(0xFF0EA5E9)
    val OnAccent = Color(0xFF00131C)

    /** Status colors — never used for decoration or section headers. */
    object Status {
        val Success = Color(0xFF34D399)
        val Warning = Color(0xFFFACC15)
        val Danger = Color(0xFFFF6B6B)
        val Info = Accent
    }

    /** Glass surfaces: tint only. Blur/opacity live in [GlassVariant]. */
    object Glass {
        val RegularTint = Color(0x8C121722)
        val ClearTint = Color(0x59121722)
        val ChromeTint = Color(0x66101623)
        val Border = Color(0x33FFFFFF)
        val BorderStrong = Color(0x4DFFFFFF)
        /** Dimming layer for clear glass over bright content (HIG: ~35%). */
        val Dim = Color(0x59000000)
    }
}

/**
 * 4pt-based spacing scale. Using named steps keeps rhythm consistent instead of
 * ad-hoc values per screen ("Align components with one another…").
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/** Corner radii: concentric with the platform's rounded chrome. */
object Radius {
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val pill = 999.dp
}

/**
 * Minimum interactive sizes.
 * HIG Accessibility: mobile default control 44x44pt, minimum 28x28pt.
 */
object TouchTarget {
    val min = 44.dp
    val compact = 32.dp
    val icon = 24.dp // the glyph itself, inside a >= min tappable box
}
