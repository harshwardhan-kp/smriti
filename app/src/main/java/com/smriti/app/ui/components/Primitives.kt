package com.smriti.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smriti.app.ui.theme.S
import com.smriti.app.ui.theme.SmritiType

/**
 * The vocabulary of the antimattr language, as composables.
 *
 * Screens should reach for these rather than assembling a Surface with a colour and a radius.
 * Every one of them encodes a decision that has already been made, so that no screen has to
 * make it again and no two screens make it differently.
 */

// -----------------------------------------------------------------------------------------
// [bracket labels]
// -----------------------------------------------------------------------------------------

/**
 * `[ label ]` — mono, lowercase, with the brackets in red and the label in a quieter colour.
 *
 * This is the single most recognisable thing on antimattr.one. Every piece of metadata on that
 * site wears it: nav items, investor names, `[shipping starts Q4-2026]`, `unveils in [2026]`.
 * In Smriti it carries timestamps, states, counts and tags — anything the machine knows about
 * a record rather than anything a person wrote.
 *
 * The text is lowercased for you. Do not pass an already-uppercased string expecting it to
 * survive.
 */
@Composable
fun BracketLabel(
    text: String,
    modifier: Modifier = Modifier,
    labelColor: Color = S.Muted,
    bracketColor: Color = S.Red,
    style: androidx.compose.ui.text.TextStyle = SmritiType.Meta
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = bracketColor)) { append("[") }
            withStyle(SpanStyle(color = labelColor)) { append(text.lowercase()) }
            withStyle(SpanStyle(color = bracketColor)) { append("]") }
        },
        style = style,
        modifier = modifier
    )
}

/**
 * A [BracketLabel] that reads as currently-true rather than merely descriptive: the label takes
 * the accent colour too. Use it for live states — recording, thinking — and nowhere else, so it
 * keeps its urgency.
 */
@Composable
fun BracketLabelLive(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = S.Red
) = BracketLabel(
    text = text,
    modifier = modifier,
    labelColor = color,
    bracketColor = color,
    style = SmritiType.MetaMedium
)

// -----------------------------------------------------------------------------------------
// node squares
// -----------------------------------------------------------------------------------------

/**
 * The small red square that anchors every annotation on antimattr.one — a 16px squircle with a
 * 6px radius, filled with Red-primary. Here it is the app's only bullet: it marks an amount, a
 * list item, a point on a rule.
 *
 * Nothing else in the app may be a small filled red rectangle, or this stops meaning anything.
 */
@Composable
fun NodeSquare(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    color: Color = S.Red
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, RoundedCornerShape(size * 0.375f))
    )
}

// -----------------------------------------------------------------------------------------
// rules
// -----------------------------------------------------------------------------------------

/** A hairline. The app's only solid divider. */
@Composable
fun HairlineRule(
    modifier: Modifier = Modifier,
    color: Color = S.Hairline
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(S.hairlineWidth)
            .background(color)
    )
}

/**
 * A dashed hairline — the technical-drawing rule antimattr uses to lead from a node square to
 * its caption. Use it where a separator should feel like an annotation rather than a boundary:
 * above raw machine output, between a record and its evidence.
 */
@Composable
fun DashedRule(
    modifier: Modifier = Modifier,
    color: Color = S.Hairline,
    dash: Dp = 3.dp,
    gap: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(S.hairlineWidth)
            .drawBehind {
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(dash.toPx(), gap.toPx()),
                        0f
                    )
                )
            }
    )
}

// -----------------------------------------------------------------------------------------
// buttons
// -----------------------------------------------------------------------------------------

/**
 * antimattr's button: 44dp tall, a 6dp radius, and a lowercase mono label. Two weights only.
 *
 * [primary] is the inverted one — near-black ground, white mono label — and there is at most
 * one on a screen. Everything else is the secondary: a 7% grey wash with ink type, which is
 * what the site uses for "learn more" beside "reserve early access".
 */
@Composable
fun SmritiButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val background = when {
        !enabled -> S.MutedSoft.copy(alpha = 0.07f)
        primary -> S.Slate
        else -> S.MutedSoft.copy(alpha = 0.07f)
    }
    val labelColor = when {
        !enabled -> S.MutedSoft
        primary -> S.OnDark
        else -> S.Ink
    }

    Box(
        modifier = modifier
            .height(S.controlHeight)
            .background(background, S.r6)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = S.gutter),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.lowercase(),
            style = SmritiType.Button,
            color = labelColor
        )
    }
}

/**
 * A button with no fill at all — a hairline outline and mono type. For destructive or
 * secondary-to-secondary actions, where even a grey wash would be too much weight.
 */
@Composable
fun SmritiOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = S.Ink,
    borderColor: Color = S.Hairline
) {
    Box(
        modifier = modifier
            .height(S.controlHeight)
            .border(BorderStroke(S.hairlineWidth, SolidColor(borderColor)), S.r6)
            .clickable(onClick = onClick)
            .padding(horizontal = S.gutter),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label.lowercase(), style = SmritiType.Button, color = color)
    }
}

// -----------------------------------------------------------------------------------------
// chips
// -----------------------------------------------------------------------------------------

/** The three kinds of chip Smriti shows, each with a fixed treatment. */
enum class ChipKind {
    /** A person the model found. Quiet, outlined. */
    Person,

    /** A tag. Accent-tinted — tags are how you navigate, so they carry the accent. */
    Tag,

    /** A money amount. Ink-filled, because a number is a fact. */
    Amount
}

@Composable
fun SmritiChip(
    text: String,
    kind: ChipKind,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val (bg, fg, border) = when (kind) {
        ChipKind.Person -> Triple(Color.Transparent, S.Muted, S.Hairline)
        ChipKind.Tag -> Triple(S.RedTint.copy(alpha = 0.10f), S.Red, S.Red.copy(alpha = 0.35f))
        ChipKind.Amount -> Triple(S.Ink, S.OnDark, Color.Transparent)
    }

    Row(
        modifier = modifier
            .background(bg, S.r6)
            .border(BorderStroke(S.hairlineWidth, SolidColor(border)), S.r6)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.sm)
    ) {
        Text(text = text.lowercase(), style = SmritiType.Meta, color = fg)
        trailing?.invoke()
    }
}

// -----------------------------------------------------------------------------------------
// headings
// -----------------------------------------------------------------------------------------

/**
 * A display heading in Instrument Serif with exactly one word set in italic.
 *
 * "the world is *noisy*, delete the unnecessary" — this one move is what makes antimattr's
 * headlines feel authored rather than typed, and it costs nothing. Pass the word to lean on in
 * [italicWord]; if it does not occur in [text] the line simply renders upright, which is a safe
 * failure.
 */
@Composable
fun DisplayHeading(
    text: String,
    modifier: Modifier = Modifier,
    italicWord: String? = null,
    color: Color = S.Ink,
    style: androidx.compose.ui.text.TextStyle = SmritiType.Display
) {
    val lower = text.lowercase()
    val at = italicWord?.lowercase()?.let { lower.indexOf(it) } ?: -1

    Text(
        text = buildAnnotatedString {
            if (at < 0 || italicWord == null) {
                append(lower)
            } else {
                append(lower.substring(0, at))
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(lower.substring(at, at + italicWord.length))
                }
                append(lower.substring(at + italicWord.length))
            }
        },
        style = style,
        color = color,
        modifier = modifier
    )
}

/**
 * A section heading: a red node square, then a mono lowercase label. Used above every grouped
 * block — "open tasks", "amounts", "what you said".
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = S.Muted
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.md)
    ) {
        NodeSquare(size = 6.dp)
        Text(text = text.lowercase(), style = SmritiType.MetaMedium, color = color)
    }
}
