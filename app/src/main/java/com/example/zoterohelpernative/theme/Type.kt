package com.example.zoterohelpernative.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import com.example.zoterohelpernative.R

/**
 * Complete type scale modelled on the HIG text styles
 * (`.claude/skills/apple-design/references/hig/typography.md`).
 *
 * Rules encoded here:
 *  - Body is 17sp: "Platform | Default size | Minimum size — Mobile | 17 pt | 11 pt".
 *    Nothing in the app goes below 11sp.
 *  - No Light/Thin weights: "avoid light font weights… prefer Regular, Medium,
 *    Semibold, or Bold".
 *  - Every level is a distinct size+weight pair so hierarchy survives when the
 *    user scales text up.
 *
 * Screens should use these styles instead of inline `fontSize =` values, so the
 * hierarchy stays consistent and scales as one system.
 */
private val Sans = FontFamily.Default

private val SystemTypography = Typography(
    // Hero headings (rare — empty states, onboarding)
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.03).sp,
        lineBreak = LineBreak.Heading
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.02).sp,
        lineBreak = LineBreak.Heading
    ),

    // Screen and section titles
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.01).sp,
        lineBreak = LineBreak.Heading
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),

    // Reading text — 17sp is the platform default body size
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    // Footnotes and captions — never smaller than this
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),

    // Controls
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)

/*
 * PsyDiary typography: Gloock (display serif) carries the character on titles
 * only; Archivo is the working face for controls and reading text. PsyDiary's
 * rule: "personality lives in the surfaces, never in the controls or in the
 * text people read". Fonts bundled under res/font (SIL OFL, see assets/licenses).
 */
private val Gloock = FontFamily(Font(R.font.gloock_regular, FontWeight.Normal))
private val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold)
)

private fun TextStyle.display() = copy(fontFamily = Gloock, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).sp)
private fun TextStyle.text() = copy(fontFamily = Archivo)

private val EditorialTypography = SystemTypography.copy(
    // Gloock ships one weight: hierarchy comes from size, as in PsyDiary
    displayLarge = SystemTypography.displayLarge.display(),
    displayMedium = SystemTypography.displayMedium.display(),
    headlineSmall = SystemTypography.headlineSmall.display(),
    titleLarge = SystemTypography.titleLarge.display().copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = SystemTypography.titleMedium.text(),
    titleSmall = SystemTypography.titleSmall.text(),
    bodyLarge = SystemTypography.bodyLarge.text(),
    bodyMedium = SystemTypography.bodyMedium.text(),
    bodySmall = SystemTypography.bodySmall.text(),
    labelLarge = SystemTypography.labelLarge.text(),
    labelMedium = SystemTypography.labelMedium.text(),
    labelSmall = SystemTypography.labelSmall.text()
)

fun appTypography(editorial: Boolean): Typography = if (editorial) EditorialTypography else SystemTypography
