package com.example.zoterohelpernative.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design tokens derived from the Apple HIG guidelines bundled in
 * `.claude/skills/apple-design`.
 *
 * Colors are *semantic* (what they mean) and come from the active
 * [AppPalette]: screens read `AppColors.Label.Primary` and get the right value
 * for the current theme, so adding a theme never touches the screens.
 *
 * Every palette must keep these guarantees, measured on all three background
 * levels: Label.Primary/Secondary/Tertiary ≥ 4.5:1 (body text minimum),
 * Accent ≥ 4.5:1 as text, OnAccent ≥ 4.5:1 on Accent. Quaternary/Placeholder
 * are for decoration, placeholders and disabled states only.
 */
@Immutable
data class AppPalette(
    val id: String,
    val isDark: Boolean,
    /** Draw style of the screen backdrop the glass layer refracts. */
    val backdrop: Backdrop,
    /** Editorial typography (serif display titles) instead of the system face. */
    val editorialType: Boolean,

    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val backgroundTertiary: Color,
    val glassBase: Color,

    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelQuaternary: Color,

    val fillPrimary: Color,
    val fillSecondary: Color,
    val fillTertiary: Color,

    val separator: Color,
    val separatorOpaque: Color,

    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,

    val success: Color,
    val warning: Color,
    val danger: Color,

    val glassRegularTint: Color,
    val glassClearTint: Color,
    val glassChromeTint: Color,
    val glassBorder: Color,
    val glassBorderStrong: Color,
    val glassDim: Color,
    /** Near-opaque tints used where the platform can't blur (Android < 12). */
    val glassFallbackRegular: Color,
    val glassFallbackClear: Color,
    val glassFallbackChrome: Color,
    /** Specular highlight painted on glass edges; white reads as "light" on any base. */
    val glassSheen: Color
)

enum class Backdrop {
    /** Dark field with soft colored glows (the original look). */
    Aurora,
    /** Flat warm paper with print grain, as in PsyDiary. */
    Paper
}

object Palettes {

    /** The app's original dark theme. Contrast measured on #141415. */
    val Notte = AppPalette(
        id = "notte",
        isDark = true,
        backdrop = Backdrop.Aurora,
        editorialType = false,
        backgroundPrimary = Color(0xFF0A0A0B),
        backgroundSecondary = Color(0xFF141415),
        backgroundTertiary = Color(0xFF1F1F22),
        glassBase = Color(0xFF0B0F17),
        labelPrimary = Color(0xFFEDEDED),
        labelSecondary = Color(0x99FFFFFF),   // ~7.2:1
        labelTertiary = Color(0x73FFFFFF),    // ~4.7:1
        labelQuaternary = Color(0x59FFFFFF),  // decorative only
        fillPrimary = Color(0x1FFFFFFF),
        fillSecondary = Color(0x14FFFFFF),
        fillTertiary = Color(0x0DFFFFFF),
        separator = Color(0x1FFFFFFF),
        separatorOpaque = Color(0xFF27272A),
        accent = Color(0xFF38BDF8),
        accentPressed = Color(0xFF0EA5E9),
        onAccent = Color(0xFF00131C),
        success = Color(0xFF34D399),
        warning = Color(0xFFFACC15),
        danger = Color(0xFFFF6B6B),
        glassRegularTint = Color(0x8C121722),
        glassClearTint = Color(0x59121722),
        glassChromeTint = Color(0x66101623),
        glassBorder = Color(0x33FFFFFF),
        glassBorderStrong = Color(0x4DFFFFFF),
        glassDim = Color(0x59000000),
        glassFallbackRegular = Color(0xF01A1F2B),
        glassFallbackClear = Color(0xC0141821),
        glassFallbackChrome = Color(0xE0141821),
        glassSheen = Color.White
    )

    /*
     * PsyDiary — "Taccuino" direction, "Registro" palette: warm paper, blue-black
     * ink, a single spot ink (pencil vermilion). Values converted from the
     * OKLCH tokens in PsyDiary/src/styles/tokens.css. Where a token is meant
     * for strokes and failed 4.5:1 as text (inchiostro-3, the green and red
     * families, the plain spot on paper) a slightly deeper tone of the same hue
     * is used so the guarantees above hold.
     */

    /** "Carta" — light. Contrast measured on carta-3 #E9DECE (worst case). */
    val PsyDiaryCarta = AppPalette(
        id = "psydiary_carta",
        isDark = false,
        backdrop = Backdrop.Paper,
        editorialType = true,
        backgroundPrimary = Color(0xFFF6F0E6),   // --carta
        backgroundSecondary = Color(0xFFF1E9DB), // --carta-2: sheets, panels
        backgroundTertiary = Color(0xFFE9DECE),  // --carta-3: hollows, hover
        glassBase = Color(0xFFF6F0E6),
        labelPrimary = Color(0xFF1A2131),        // --inchiostro          12.1:1
        labelSecondary = Color(0xFF4F5563),      // --inchiostro-2         5.6:1
        labelTertiary = Color(0xFF5E636F),       // between -2 and -3      4.5:1
        labelQuaternary = Color(0xFF757A85),     // --inchiostro-3: placeholders
        fillPrimary = Color(0xFFE9DECE),
        fillSecondary = Color(0xFFF1E9DB),
        fillTertiary = Color(0x0D1A2131),
        separator = Color(0x6B656971),           // --matita: pencil lines
        separatorOpaque = Color(0xFFD6CBB9),
        accent = Color(0xFFB23A23),              // --spot-testo           4.5:1
        accentPressed = Color(0xFF9A2F1B),
        onAccent = Color(0xFFF6F0E6),            // paper on vermilion     5.3:1
        success = Color(0xFF206B38),             //                        4.9:1
        warning = Color(0xFF7A5C0E),             // deep ochre             4.7:1
        danger = Color(0xFFB01F1F),              //                        5.2:1
        glassRegularTint = Color(0xB8F6F0E6),
        glassClearTint = Color(0x80F6F0E6),
        glassChromeTint = Color(0xA6F1E9DB),
        glassBorder = Color(0x4D656971),
        glassBorderStrong = Color(0x80656971),
        glassDim = Color(0x14000000),
        glassFallbackRegular = Color(0xF5F1E9DB),
        glassFallbackClear = Color(0xD9F6F0E6),
        glassFallbackChrome = Color(0xEBF1E9DB),
        glassSheen = Color.White
    )

    /** "Inchiostro" — dark. Contrast measured on carta-3 #20252F (worst case). */
    val PsyDiaryInchiostro = AppPalette(
        id = "psydiary_inchiostro",
        isDark = true,
        backdrop = Backdrop.Paper,
        editorialType = true,
        backgroundPrimary = Color(0xFF10141B),
        backgroundSecondary = Color(0xFF181C24),
        backgroundTertiary = Color(0xFF20252F),
        glassBase = Color(0xFF10141B),
        labelPrimary = Color(0xFFEDE7DC),        //                       12.5:1
        labelSecondary = Color(0xFFB7B0A5),      //                        7.1:1
        labelTertiary = Color(0xFF918B81),       //                        4.6:1
        labelQuaternary = Color(0xFF857F76),
        fillPrimary = Color(0x1FEDE7DC),
        fillSecondary = Color(0x14EDE7DC),
        fillTertiary = Color(0x0DEDE7DC),
        separator = Color(0x33E5DDD0),           // --matita
        separatorOpaque = Color(0xFF2A2F39),
        accent = Color(0xFFEF7C59),              // --spot                 5.6:1
        accentPressed = Color(0xFFD96A48),
        onAccent = Color(0xFF10141B),            //                        6.8:1
        success = Color(0xFF6FC884),
        warning = Color(0xFFEAC25A),
        danger = Color(0xFFF97165),
        glassRegularTint = Color(0x8C181C24),
        glassClearTint = Color(0x59181C24),
        glassChromeTint = Color(0x6610141B),
        glassBorder = Color(0x33E5DDD0),
        glassBorderStrong = Color(0x4DE5DDD0),
        glassDim = Color(0x59000000),
        glassFallbackRegular = Color(0xF2181C24),
        glassFallbackClear = Color(0xC010141B),
        glassFallbackChrome = Color(0xE010141B),
        glassSheen = Color.White
    )
}

/** User-selectable appearance. [id] is what gets persisted. */
enum class AppTheme(val id: String, val label: String, val description: String) {
    NOTTE("notte", "Notte", "Il tema scuro originale"),
    PSYDIARY_AUTO("psydiary_auto", "PsyDiary · automatico", "Carta o inchiostro secondo il sistema"),
    PSYDIARY_CARTA("psydiary_carta", "PsyDiary · carta", "Chiaro: carta calda e inchiostro"),
    PSYDIARY_INCHIOSTRO("psydiary_inchiostro", "PsyDiary · inchiostro", "Scuro: inchiostro e carta");

    fun palette(systemDark: Boolean): AppPalette = when (this) {
        NOTTE -> Palettes.Notte
        PSYDIARY_AUTO -> if (systemDark) Palettes.PsyDiaryInchiostro else Palettes.PsyDiaryCarta
        PSYDIARY_CARTA -> Palettes.PsyDiaryCarta
        PSYDIARY_INCHIOSTRO -> Palettes.PsyDiaryInchiostro
    }

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: NOTTE
    }
}

val LocalAppPalette = staticCompositionLocalOf { Palettes.Notte }

/**
 * Semantic color tokens, resolved against the active palette.
 * Read them inside composition (they're `@Composable` getters).
 */
object AppColors {

    /** Background hierarchy: primary = the view, secondary = grouped content, tertiary = nested. */
    object Background {
        val Primary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.backgroundPrimary
        val Secondary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.backgroundSecondary
        val Tertiary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.backgroundTertiary
        /** Base tint behind glass surfaces so blur never washes out. */
        val GlassBase: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.glassBase
    }

    /** Foreground content, by importance. */
    object Label {
        val Primary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.labelPrimary
        val Secondary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.labelSecondary
        val Tertiary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.labelTertiary
        val Quaternary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.labelQuaternary
        val Placeholder: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.labelQuaternary
    }

    /** Fills for the content layer (no blur — the glass layer floats above). */
    object Fill {
        val Primary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.fillPrimary
        val Secondary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.fillSecondary
        val Tertiary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.fillTertiary
    }

    val Separator: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.separator
    val SeparatorOpaque: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.separatorOpaque

    /**
     * One accent for interactivity across the whole app.
     * "Apply color sparingly. Reserve it for status indicators, primary actions,
     * selected navigation items."
     */
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accent
    val AccentPressed: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accentPressed
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onAccent

    /** Status colors — never used for decoration or section headers. */
    object Status {
        val Success: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.success
        val Warning: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.warning
        val Danger: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.danger
        val Info: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accent
    }

    /** Glass surfaces: tint only. Blur/opacity live in GlassVariant. */
    object Glass {
        val RegularTint: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.glassRegularTint
        val ClearTint: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.glassClearTint
        val ChromeTint: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.glassChromeTint
        val Border: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.glassBorder
        val BorderStrong: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.glassBorderStrong
        /** Dimming layer for clear glass over bright content (HIG: ~35% on dark themes). */
        val Dim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.glassDim
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
