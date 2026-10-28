package org.himstedt.immichslideshow.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.himstedt.immichslideshow.ui.theme.Palette

/** Fire TV / Android TV remote key helpers. */
object RemoteKeys {
    fun isConfirm(event: KeyEvent): Boolean =
        event.key == Key.DirectionCenter ||
            event.key == Key.Enter ||
            event.key == Key.NumPadEnter ||
            event.key == Key.Spacebar

    /** A confirmed press: fires once on key up, so held keys don't repeat. */
    fun isConfirmUp(event: KeyEvent): Boolean = event.type == KeyEventType.KeyUp && isConfirm(event)
}

/** Full-screen background shared by every non-slideshow screen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C1018), Color(0xFF07080C), Color(0xFF04050A)),
                ),
            )
            .drawGlow(),
        content = content,
    )
}

private fun Modifier.drawGlow(): Modifier = this.then(
    Modifier.drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x24F2A45C), Color.Transparent),
                center = Offset(size.width * 0.84f, size.height * 0.06f),
                radius = size.width * 0.62f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x141E4B7A), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.95f),
                radius = size.width * 0.55f,
            ),
        )
    },
)

/**
 * The one focusable building block the app uses. It grows slightly, lifts off
 * the background and lights its border when it holds the D-pad focus.
 */
@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = Palette.Panel,
    focusedColor: Color = Palette.PanelHigh,
    borderColor: Color = Palette.Hairline,
    focusedBorderColor: Color = Palette.Amber,
    contentPadding: PaddingValues = PaddingValues(horizontal = 26.dp, vertical = 16.dp),
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "focusScale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 22f else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "focusElevation",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation.dp, shape)
            .clip(shape)
            .background(if (focused) focusedColor else containerColor)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (RemoteKeys.isConfirmUp(event)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(),
    ) {
        Box(Modifier.padding(contentPadding)) { content(focused) }
        // The ring is drawn over the content, not under it. Content that fills
        // the surface - an album cover, a theme preview - otherwise paints the
        // border away, and the focus ring loses whichever edge the content
        // reaches, which for a cover means the top.
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) focusedBorderColor else borderColor,
                    shape = shape,
                ),
        )
    }
}

/** Small rounded action button. */
@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    FocusableSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        shape = RoundedCornerShape(50),
        containerColor = if (accent) Palette.Amber else Palette.Panel,
        focusedColor = if (accent) Palette.AmberSoft else Palette.PanelHigh,
        borderColor = if (accent) Palette.Amber else Palette.Hairline,
        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 14.dp),
    ) { focused ->
        Text(
            text = label,
            color = when {
                !enabled -> Palette.MutedDim
                accent -> Color(0xFF21150A)
                focused -> Palette.Cream
                else -> Palette.Cream.copy(alpha = 0.86f)
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            maxLines = 1,
        )
    }
}

/** Compact selectable chip used for the slideshow options row. */
@Composable
fun TvChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        containerColor = if (selected) Color(0x33F2A45C) else Palette.Panel,
        focusedColor = if (selected) Color(0x55F2A45C) else Palette.PanelHigh,
        borderColor = if (selected) Palette.Amber.copy(alpha = 0.75f) else Palette.Hairline,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) { focused ->
        Text(
            text = label,
            color = when {
                selected -> Palette.AmberSoft
                focused -> Palette.Cream
                else -> Palette.Cream.copy(alpha = 0.78f)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            maxLines = 1,
        )
    }
}

/** A labelled key cap, used in the on-screen control hints. */
@Composable
fun KeyHint(
    keys: String,
    label: String,
    modifier: Modifier = Modifier,
    /** On a light stage the pill and the type both have to go dark to read. */
    light: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (light) Color(0x1A000000) else Color(0x1FFFFFFF))
                .border(
                    1.dp,
                    if (light) Color(0x33000000) else Palette.Hairline,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                text = keys,
                color = if (light) Color(0xFF2A2A2A) else Palette.Cream,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                maxLines = 1,
            )
        }
        Text(
            text = label,
            color = if (light) Color(0xB32A2A2A) else Palette.Muted,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

/** Indeterminate arc spinner (no Material dependency needed). */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = Palette.Amber,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "spinnerAngle",
    )
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Inline error card with an optional retry action. */
@Composable
fun ErrorNotice(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again",
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x1FE8705E))
            .border(1.dp, Palette.Danger.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = message,
            color = Color(0xFFFFC9C0),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onRetry != null) TvButton(label = retryLabel, onClick = onRetry)
    }
}

/** Eyebrow + title block used at the top of the library screens. */
@Composable
fun SectionHeading(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier) {
        Text(
            text = eyebrow.uppercase(),
            color = Palette.Amber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.6.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(text = title, color = Palette.Cream, fontSize = 40.sp, fontWeight = FontWeight.Light)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = Palette.Muted,
                fontSize = 17.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Vertical spacer helper that reads better than Spacer(Modifier.height(..)) chains. */
@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

/** Horizontal spacer helper. */
@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
