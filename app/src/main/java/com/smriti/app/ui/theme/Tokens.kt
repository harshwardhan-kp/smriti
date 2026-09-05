package com.smriti.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Smriti's design tokens.
 *
 * Every value here is lifted directly from antimattr.one's own published token set — the colour
 * hexes are the site's `--token-*` custom properties verbatim, and the spacing steps are the
 * gaps and section paddings it uses at its mobile breakpoint. Nothing in this file was invented,
 * and nothing in the app should invent a colour or a spacing value that is not in it.
 *
 * The look is a deliberate inversion of what Smriti had: paper, not ink. A near-white ground
 * (#EEEEEE), near-black type, one loud red, and a great deal of air. Colour is scarce on purpose,
 * which is what makes the red mean something when it appears.
 */
object S {

    // ---- ground and surfaces -------------------------------------------------------------

    /** The page. Everything sits on this. */
    val Paper = Color(0xFFEEEEEE)

    /** A surface one step back from the page: cards, wells, inactive fields. */
    val PaperSunk = Color(0xFFE9E9E9)

    val White = Color(0xFFFFFFFF)

    /** Inverted surfaces — the primary button, the capture chrome, the bubble. */
    val Slate = Color(0xFF202020)
    val Graphite = Color(0xFF121212)
    val InkDeep = Color(0xFF050505)

    // ---- type ----------------------------------------------------------------------------

    /** Primary text. Not pure black — #0D0D0D reads softer on a light ground. */
    val Ink = Color(0xFF0D0D0D)

    /** Secondary text: summaries, timestamps, everything that supports the primary line. */
    val Muted = Color(0xFF5E5E5E)

    /** Tertiary text: the quietest thing that is still readable. */
    val MutedSoft = Color(0xFF6A6A6A)

    /** Type on an inverted surface. */
    val OnDark = Color(0xFFFFFFFF)

    // ---- accent --------------------------------------------------------------------------

    /**
     * The accent, and the only saturated colour that appears in ordinary use. Brackets, node
     * squares, active states, the live recording ring. antimattr names this token "Red-primary".
     */
    val Red = Color(0xFFE10909)

    /** Pressed and live states — one step brighter. */
    val RedBright = Color(0xFFE73A3A)

    /** A 25% wash of the accent, for fills behind red type. */
    val RedTint = Color(0x40E10909)

    /** The accent at reading weight on a dark ground. */
    val RedSoft = Color(0xFFF18E8E)

    // ---- semantic ------------------------------------------------------------------------
    //
    // Red is the brand accent, so it can no longer carry "recording" on its own. These three
    // carry state instead, and each appears in exactly one role across the whole app.

    /** Work in progress: enrichment pending or running. */
    val Amber = Color(0xFFFEA710)

    /** Settled, complete, done. */
    val Green = Color(0xFF008628)

    /** Informational only. */
    val Blue = Color(0xFF1083FA)

    // ---- lines ---------------------------------------------------------------------------

    /** Every rule, divider and border in the app. 30% grey — a hairline, never a box. */
    val Hairline = Color(0x4DBABABA)

    /** A hairline on an inverted surface. */
    val HairlineOnDark = Color(0x33FFFFFF)

    // ---- spacing -------------------------------------------------------------------------
    //
    // antimattr's mobile breakpoint: 16px page gutters, 44px section rhythm, and 6/10/16/24
    // gaps inside a block. Use these and nothing else.

    val xs = 4.dp
    val sm = 6.dp
    val md = 10.dp
    val gutter = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val section = 44.dp

    // ---- shape ---------------------------------------------------------------------------
    //
    // The site's dominant radius is 6px. It is small enough to read as "drawn" rather than
    // "rounded", which is the whole point — this is a technical-drawing language, not a
    // soft-app one. Do not reach for 12 or 16.

    val r6 = RoundedCornerShape(6.dp)
    val r8 = RoundedCornerShape(8.dp)
    val r24 = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)

    /** Hairline width. One value, everywhere. */
    val hairlineWidth = 1.dp

    /** Standard control height — the site's buttons are 40px tall inside a 44px tap target. */
    val controlHeight = 44.dp
}
