package cn.xfangfang.biliduo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.duoTextFieldNavigation

/** Shared dark palette for secondary pages; mirrors Profile/Home/Console. */
object DuoPalette {
    val Bg = Color(0xFF0F0F0F)
    val Surface = Color(0xFF17171A)
    val SurfaceHigh = Color(0xFF1F1F24)
    val SurfaceHighest = Color(0xFF26262E)
    val Text = Color(0xFFF2F2F4)
    val Secondary = Color(0xFFA6A6AE)
    val Tertiary = Color(0xFF73737F)
    val Pink = Color(0xFFFB7299)
    val OnPink = Color(0xFF1A0710)
    val Outline = Color(0xFF2A2A31)
    val Error = Color(0xFFF2555A)
    val Success = Color(0xFF4CC38A)
    val Scrim = Color.Black.copy(alpha = 0.66f)
}

@Composable
fun DuoPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 56dp leaves 8dp above/below the 40dp button so the 5.75dp
            // focus ring is not clipped by the screen edge or the content.
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DuoIconAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
            modifier = backModifier,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = title,
            color = DuoPalette.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun DuoIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = DuoPalette.Text,
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .duoClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else DuoPalette.Tertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Search field that participates in the bottom-screen focus tree. Center opens
 * the IME; the caller owns show/hide via [onRequestIme].
 */
@Composable
fun DuoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    onSubmit: () -> Unit = {},
    onClear: () -> Unit = {},
    onRequestIme: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(38.dp)
            .focusRequester(focusRequester)
            .duoTextFieldNavigation()
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionCenter) {
                    onRequestIme()
                    true
                } else {
                    false
                }
            }
            .clip(RoundedCornerShape(19.dp))
            .background(DuoPalette.Surface)
            .border(
                width = 1.dp,
                color = if (focused) DuoPalette.Pink else DuoPalette.Outline,
                shape = RoundedCornerShape(19.dp),
            ),
        textStyle = TextStyle(color = DuoPalette.Text, fontSize = 13.sp),
        singleLine = true,
        cursorBrush = SolidColor(DuoPalette.Pink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = DuoPalette.Tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = DuoPalette.Tertiary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .duoFocusRing(CircleShape)
                            .clip(CircleShape)
                            .duoClickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            tint = DuoPalette.Secondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun DuoEmptyState(
    title: String,
    hint: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, color = DuoPalette.Secondary, fontSize = 14.sp)
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(text = hint, color = DuoPalette.Tertiary, fontSize = 11.sp)
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            DuoPillButton(
                text = actionText,
                onClick = onAction,
                focusRequester = focusRequester,
            )
        }
    }
}

@Composable
fun DuoPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) DuoPalette.Pink else DuoPalette.SurfaceHigh)
            .duoClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> DuoPalette.Tertiary
                primary -> DuoPalette.OnPink
                else -> DuoPalette.Text
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Duo-safe confirmation card. Compose Dialog cannot live in a Presentation,
 * so this is an in-tree overlay with its own focus/keys. B (routed through
 * DuoSessionViewModel) cancels it instead of popping the page.
 */
@Composable
fun DuoConfirmOverlay(
    title: String,
    message: String,
    confirmText: String = "删除",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    session: DuoSessionViewModel? = null,
) {
    var confirmSelected by remember { mutableStateOf(!danger) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    DisposableEffect(session) {
        session?.setBackInterceptor {
            onCancel()
            true
        }
        onDispose { session?.setBackInterceptor(null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Scrim)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        confirmSelected = false
                        true
                    }
                    Key.DirectionRight -> {
                        confirmSelected = true
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> true
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (confirmSelected) onConfirm() else onCancel()
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
        )
        Column(
            modifier = Modifier
                .width(340.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .background(DuoPalette.SurfaceHigh)
                .border(1.dp, DuoPalette.Outline, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(20.dp),
        ) {
            Text(
                text = title,
                color = DuoPalette.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = message, color = DuoPalette.Secondary, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConfirmButton(
                    text = "取消",
                    selected = !confirmSelected,
                    danger = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                ConfirmButton(
                    text = confirmText,
                    selected = confirmSelected,
                    danger = danger,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun ConfirmButton(
    text: String,
    selected: Boolean,
    danger: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        danger && selected -> DuoPalette.Error.copy(alpha = 0.18f)
        danger -> DuoPalette.Error.copy(alpha = 0.10f)
        selected -> DuoPalette.Surface
        else -> DuoPalette.Surface.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = when {
                    selected && danger -> DuoPalette.Error
                    selected -> Color.White
                    else -> DuoPalette.Outline
                },
                shape = RoundedCornerShape(8.dp),
            )
            .duoClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (danger) DuoPalette.Error else DuoPalette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
