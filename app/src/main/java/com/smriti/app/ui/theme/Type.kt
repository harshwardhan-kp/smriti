package com.smriti.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.smriti.app.R

/**
 * Smriti's three typefaces, and the only text styles the app is allowed to use.
 *
 * antimattr.one runs on exactly three families and gives each a single, non-overlapping job.
 * That discipline is most of why the site reads as designed rather than assembled, so it is
 * reproduced here literally:
 *
 *   Instrument Serif  display only — the one voice that is allowed to be large
 *   Schibsted Grotesk everything a person reads as prose or taps as a control
 *   Geist Mono        metadata — times, counts, tags, states, buttons, raw machine text
 *
 * Two rules travel with them, and they matter more than the sizes:
 *
 *   1. All UI copy is lowercase. Every string on antimattr.one is lowercase, including its own
 *      name. It is the cheapest and loudest signal in the whole language.
 *   2. Mono metadata is wrapped in square brackets, with the brackets themselves in red.
 *      See [com.smriti.app.ui.components.BracketLabel] — do not hand-roll it.
 *
 * None of these families carries Devanagari. That is deliberate and safe: Android's font
 * fallback substitutes a system face per-glyph, so a Hindi transcript renders correctly inside
 * an otherwise Latin layout without any work here.
 */
object SmritiFonts {

    val Serif = FontFamily(
        Font(R.font.instrument_serif_regular, FontWeight.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
    )

    val Sans = FontFamily(
        Font(R.font.schibsted_grotesk_regular, FontWeight.Normal),
        Font(R.font.schibsted_grotesk_medium, FontWeight.Medium),
        Font(R.font.schibsted_grotesk_bold, FontWeight.Bold)
    )

    val Mono = FontFamily(
        Font(R.font.geist_mono_regular, FontWeight.Normal),
        Font(R.font.geist_mono_medium, FontWeight.Medium)
    )
}

/**
 * Trims the extra leading Compose adds above the first line and below the last, so a heading
 * sits where the spacing says it should rather than floating inside invisible padding. The
 * difference is very visible at display sizes.
 */
private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

/**
 * The type scale. antimattr's desktop scale is 44 / 24 / 18 / 16 / 14 with line heights of
 * 56 / 32 / 26 / 24 / 20; at its mobile breakpoint everything steps down about a quarter.
 * These are that scale, in sp, on a phone.
 *
 * Use these styles. Do not pass fontSize or fontFamily at a call site.
 */
object SmritiType {

    /** Screen-level hero. One per screen at most, and often none. */
    val Display = TextStyle(
        fontFamily = SmritiFonts.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        lineHeightStyle = Trim
    )

    /** Display, italic — for the one emphasised word inside a [Display] line. */
    val DisplayItalic = Display.copy(fontStyle = FontStyle.Italic)

    /** A record's own title, and section heroes below the screen title. */
    val DisplaySmall = TextStyle(
        fontFamily = SmritiFonts.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        lineHeightStyle = Trim
    )

    val DisplaySmallItalic = DisplaySmall.copy(fontStyle = FontStyle.Italic)

    /** The strongest sans line: card titles, top-bar titles. */
    val Title = TextStyle(
        fontFamily = SmritiFonts.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        lineHeightStyle = Trim
    )

    /** Prose. Summaries, explanations, the sentence under a heading. */
    val Body = TextStyle(
        fontFamily = SmritiFonts.Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        lineHeightStyle = Trim
    )

    /** Prose, one step down: a card's supporting line. */
    val BodySmall = TextStyle(
        fontFamily = SmritiFonts.Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        lineHeightStyle = Trim
    )

    /** A sans label on a control. */
    val Label = TextStyle(
        fontFamily = SmritiFonts.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        lineHeightStyle = Trim
    )

    /** Metadata: timestamps, counts, states, tags. Always lowercase, usually bracketed. */
    val Meta = TextStyle(
        fontFamily = SmritiFonts.Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        lineHeightStyle = Trim
    )

    /** Metadata that is currently true of the thing you are looking at. */
    val MetaMedium = Meta.copy(fontWeight = FontWeight.Medium)

    /** Button labels. Mono, lowercase — antimattr's buttons read as commands, not words. */
    val Button = TextStyle(
        fontFamily = SmritiFonts.Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = Trim
    )

    /** Raw machine text: transcripts and OCR output, shown verbatim. */
    val Mono = TextStyle(
        fontFamily = SmritiFonts.Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        lineHeightStyle = Trim
    )
}
