package org.himstedt.fireside.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.himstedt.fireside.AppContainer
import org.himstedt.fireside.data.ImmichConfig
import org.himstedt.fireside.data.ImmichException
import org.himstedt.fireside.ui.components.AppBackground
import org.himstedt.fireside.ui.components.ErrorNotice
import org.himstedt.fireside.ui.components.KeyHint
import org.himstedt.fireside.ui.components.Spinner
import org.himstedt.fireside.ui.components.TvButton
import org.himstedt.fireside.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * First-run connection screen. The Immich URL and API key are stored locally;
 * "Connect" proves the credentials against `/api/server/ping` before the
 * library is ever opened.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    initial: ImmichConfig?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onConnected: () -> Unit,
    onDemo: () -> Unit,
) {
    var url by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf(initial?.apiKey.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val urlFocus = remember { FocusRequester() }
    val keyFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (initial == null) runCatching { urlFocus.requestFocus() }
    }

    fun connect() {
        val candidateUrl = url.trim()
        val candidateKey = apiKey.trim()
        if (!ImmichConfig.isValid(candidateUrl, candidateKey)) {
            error = "Enter both the server address and an API key."
            return
        }
        busy = true
        error = null
        success = null
        scope.launch {
            var failure: Throwable? = null
            for (candidate in ImmichConfig.addressCandidates(candidateUrl)) {
                val config = ImmichConfig(baseUrl = candidate, apiKey = candidateKey).sanitized()
                val result = container.repository(config).checkConnection()
                result.onSuccess {
                    container.settings.saveConfig(config)
                    busy = false
                    success = "Connected to ${config.displayHost()}"
                    onConnected()
                    return@launch
                }
                val error = result.exceptionOrNull()
                failure = error
                // Nothing answered, so the scheme may be wrong — try the other
                // one. A rejected key or a 404 means the address itself is fine.
                if ((error as? ImmichException)?.networkFailure != true) break
            }
            busy = false
            error = failure?.message ?: "Could not reach Immich."
        }
    }

    fun startDemo() {
        container.settings.saveConfig(ImmichConfig.Demo)
        onDemo()
    }

    AppBackground {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.12f)) {
                FiresideMark()
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "FIRESIDE · FIRE TV",
                    color = Palette.Amber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Your library,\non the big screen.",
                    color = Palette.Cream,
                    fontSize = 40.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Photos stream straight from your own Immich server. " +
                        "Pick an album, then let one of ten photo themes play.",
                    color = Palette.Muted,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
                Spacer(Modifier.height(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyHint(keys = "OK", label = "Edit a field")
                    KeyHint(keys = "BACK", label = if (canGoBack) "Return to albums" else "Exit")
                }
            }

            Spacer(Modifier.width(48.dp))

            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xF2141820))
                        .border(1.dp, Palette.Hairline, RoundedCornerShape(22.dp))
                        .padding(26.dp),
                ) {
                    Text(
                        text = if (initial == null) "Connect your server" else "Server settings",
                        color = Palette.Cream,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Both values are stored on this device only.",
                        color = Palette.MutedDim,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(16.dp))

                    TvTextField(
                        label = "Server address",
                        value = url,
                        onValueChange = { url = it; error = null },
                        placeholder = "http://192.168.1.20:2283",
                        keyboardType = KeyboardType.Uri,
                        focusRequester = urlFocus,
                        onDone = { scope.launch { runCatching { keyFocus.requestFocus() } } },
                    )
                    Spacer(Modifier.height(14.dp))
                    TvTextField(
                        label = "API key",
                        value = apiKey,
                        onValueChange = { apiKey = it; error = null },
                        placeholder = "Paste an Immich API key",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                        focusRequester = keyFocus,
                        onDone = { scope.launch { runCatching { connectFocus.requestFocus() } } },
                    )

                    Spacer(Modifier.height(18.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TvButton(
                            label = if (busy) "Connecting…" else "Connect",
                            onClick = ::connect,
                            modifier = Modifier.focusRequester(connectFocus),
                            accent = true,
                            enabled = !busy,
                        )
                        TvButton(label = "Try demo mode", onClick = ::startDemo, enabled = !busy)
                    }

                    if (error != null) {
                        Spacer(Modifier.height(14.dp))
                        ErrorNotice(message = error.orEmpty())
                    }
                    if (success != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(text = success.orEmpty(), color = Palette.Ok, fontSize = 16.sp)
                    }

                    // The onboarding hint is only worth its height while the form
                    // is still empty; once there is a message the card needs the room.
                    if (error == null && success == null) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "Immich → Account Settings → API Keys → New API Key. " +
                                "Use the address the TV can reach, not localhost.",
                            color = Palette.MutedDim,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                if (canGoBack) {
                    Spacer(Modifier.height(22.dp))
                    TvButton(label = "Back to albums", onClick = onBack)
                }
            }
        }
    }
}

/** A focusable TV text field: label, panel border, placeholder and IME wiring. */
@Composable
private fun TvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)

    // TV pattern: the field is read-only until the viewer presses OK, so simply
    // walking past it with the D-pad does not throw the system keyboard up.
    LaunchedEffect(editing) {
        if (editing) keyboard?.show()
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            color = if (focused) Palette.Amber else Palette.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
        )
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (focused) Palette.PanelHigh else Color(0xFF0F131A))
                .border(if (focused) 2.dp else 1.dp, if (focused) Palette.Amber else Palette.Hairline, shape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) {
                Text(text = placeholder, color = Palette.MutedDim, fontSize = 17.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) editing = false
                    }
                    .onPreviewKeyEvent { event ->
                        if (editing) return@onPreviewKeyEvent false
                        // A read-only text field still eats the D-pad for cursor
                        // moves, so route focus ourselves while the field is idle.
                        val confirm = event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.NumPadEnter
                        when {
                            confirm && event.type == KeyEventType.KeyUp -> {
                                editing = true
                                true
                            }

                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }

                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }

                            else -> false
                        }
                    },
                readOnly = !editing,
                singleLine = true,
                textStyle = TextStyle(color = Palette.Cream, fontSize = 17.sp),
                cursorBrush = SolidColor(Palette.Amber),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        editing = false
                        keyboard?.hide()
                        // Hand focus to the next control the form owns rather than
                        // letting geometry pick a neighbour.
                        if (onDone != null) onDone() else focusManager.moveFocus(FocusDirection.Down)
                    },
                ),
            )
        }
    }
}

/** Simple aperture mark, drawn with vectors so there is no bitmap asset. */
@Composable
private fun FiresideMark() {
    androidx.compose.foundation.Canvas(Modifier.width(64.dp).height(64.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Palette.AmberSoft, Palette.Amber),
                center = center,
                radius = r,
            ),
            radius = r,
        )
        val blades = 6
        for (i in 0 until blades) {
            val angle = i * (360f / blades) * (Math.PI.toFloat() / 180f)
            val next = angle + (360f / blades) * (Math.PI.toFloat() / 180f)
            val inner = r * 0.28f
            drawLine(
                color = Color(0xFF21150A).copy(alpha = 0.55f),
                start = Offset(center.x + kotlin.math.cos(angle) * inner, center.y + kotlin.math.sin(angle) * inner),
                end = Offset(center.x + kotlin.math.cos(next) * r, center.y + kotlin.math.sin(next) * r),
                strokeWidth = 2.5f,
            )
        }
        drawCircle(color = Color(0xFF21150A).copy(alpha = 0.6f), radius = r * 0.2f, center = center)
        drawCircle(
            color = Color(0x33FFFFFF),
            radius = r - 1.5f,
            center = center,
            style = Stroke(width = 2f),
        )
    }
}
