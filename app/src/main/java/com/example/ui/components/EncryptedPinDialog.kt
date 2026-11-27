package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.security.PinCryptoHelper
import com.example.ui.PinAction
import com.example.ui.PinModalState
import com.example.ui.PinStep
import com.example.ui.theme.LocalKidShieldColors

@Composable
fun EncryptedPinDialog(
    state: PinModalState,
    shuffledKeypadEnabled: Boolean,
    cooldownSeconds: Int,
    onDigitPress: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isVisible) return

    val semantic = LocalKidShieldColors.current
    val firstKeyFocusRequester = remember { FocusRequester() }
    val titleRes = when (state.step) {
        PinStep.SETUP_NEW -> R.string.pin_setup_title
        PinStep.CONFIRM_NEW -> R.string.pin_confirm_title
        PinStep.ENTER_CURRENT -> R.string.pin_verify_title
    }
    val titleText = when {
        state.step == PinStep.ENTER_CURRENT && state.action == PinAction.REMOTE_WAKE_TV ->
            "Xác nhận PIN để Mở lại TV"
        state.step == PinStep.ENTER_CURRENT && state.action == PinAction.VERIFY_WAKE_LOCAL_TV ->
            "Nhập PIN để Mở lại màn hình TV"
        else -> stringResource(titleRes)
    }
    val subtitleRes = when (state.step) {
        PinStep.SETUP_NEW, PinStep.CONFIRM_NEW -> R.string.pin_setup_subtitle
        PinStep.ENTER_CURRENT -> R.string.pin_verify_subtitle
    }

    LaunchedEffect(state.isVisible, state.step) {
        runCatching { firstKeyFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val digit = mapKeyToDigit(event.key)
                        if (digit != null) {
                            onDigitPress(digit)
                            return@onPreviewKeyEvent true
                        }
                        if (event.key == Key.Backspace || event.key == Key.Delete) {
                            onBackspace()
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(semantic.familyContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.step == PinStep.ENTER_CURRENT) {
                                    Icons.Default.Lock
                                } else {
                                    Icons.Default.VerifiedUser
                                },
                                contentDescription = stringResource(titleRes),
                                tint = semantic.family,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (state.isRemoteVerification) "ĐIỀU KHIỂN TV • PIN 8 SỐ" else "PIN 8 SỐ BẢO MẬT",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.family
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(shape = CircleShape)
                            .testTag("pin_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.pin_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(PinCryptoHelper.PIN_LENGTH) { index ->
                        val isFilled = index < state.enteredInput.length
                        val scale by animateFloatAsState(
                            targetValue = if (isFilled) 1.1f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "pinDotScale"
                        )
                        val dotBg by animateColorAsState(
                            targetValue = when {
                                state.errorMessage != null -> MaterialTheme.colorScheme.errorContainer
                                isFilled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            label = "pinDotBg"
                        )
                        val borderColor = when {
                            state.errorMessage != null -> MaterialTheme.colorScheme.error
                            isFilled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .scale(scale)
                                .clip(RoundedCornerShape(10.dp))
                                .background(dotBg)
                                .border(1.5.dp, borderColor, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFilled) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EnhancedEncryption,
                            contentDescription = "Mã hóa ký tự",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = state.maskedDigestPreview,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (shuffledKeypadEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = semantic.kidsSafe,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.pin_shuffled_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.kidsSafe
                        )
                    }
                }

                val effectiveCooldown = if (state.isRemoteVerification) 0 else cooldownSeconds
                if (state.errorMessage != null || effectiveCooldown > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val errText = if (effectiveCooldown > 0 && state.step == PinStep.ENTER_CURRENT) {
                        stringResource(R.string.pin_error_cooldown, effectiveCooldown)
                    } else {
                        state.errorMessage.orEmpty()
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = errText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val keypadEnabled = !(effectiveCooldown > 0 && state.step == PinStep.ENTER_CURRENT)
                val digits = state.shuffledDigits.ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0) }
                val rows = listOf(
                    digits.subList(0, 3),
                    digits.subList(3, 6),
                    digits.subList(6, 9)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    rows.forEachIndexed { rowIdx, rowDigits ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowDigits.forEachIndexed { colIdx, digit ->
                                val mod = if (rowIdx == 0 && colIdx == 0) {
                                    Modifier.focusRequester(firstKeyFocusRequester)
                                } else {
                                    Modifier
                                }
                                PinDigitButton(
                                    digit = digit,
                                    enabled = keypadEnabled,
                                    onClick = { onDigitPress(digit) },
                                    modifier = mod
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PinActionButton(
                            onClick = onClear,
                            enabled = keypadEnabled && state.enteredInput.isNotEmpty(),
                            tag = "pin_clear_button"
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Xóa hết",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        PinDigitButton(
                            digit = digits[9],
                            enabled = keypadEnabled,
                            onClick = { onDigitPress(digits[9]) }
                        )

                        PinActionButton(
                            onClick = onBackspace,
                            enabled = keypadEnabled && state.enteredInput.isNotEmpty(),
                            tag = "pin_backspace_button"
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = stringResource(R.string.pin_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Dùng phím ▲▼◄► + OK trên Remote Xiaomi TV hoặc chạm để nhập",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .tvFocusHighlight(shape = RoundedCornerShape(12.dp))
                        .testTag("pin_cancel_button")
                ) {
                    Text(text = stringResource(R.string.pin_cancel))
                }
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    digit: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .size(width = 80.dp, height = 56.dp)
            .tvFocusHighlight(shape = shape)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("pin_key_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    }
}

@Composable
private fun PinActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 56.dp)
            .tvFocusHighlight(shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun mapKeyToDigit(key: Key): Int? {
    return when (key) {
        Key.Zero, Key.NumPad0 -> 0
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8
        Key.Nine, Key.NumPad9 -> 9
        else -> null
    }
}
