package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GuardState
import com.example.ui.theme.LocalKidShieldColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

fun formatPairingCodeDisplay(code: String): String {
    val clean = code.filter { it.isDigit() }.take(6)
    return if (clean.length == 6) {
        "${clean.substring(0, 3)} ${clean.substring(3, 6)}"
    } else {
        clean
    }
}

fun buildQrPairingUri(guardState: GuardState): String {
    val host = guardState.tvLocalIp.orEmpty().trim()
    return "kidshield://pair?host=$host&port=${guardState.tvServerPort}&code=${guardState.pairingCode}"
}

@Composable
fun QrCodeCanvas(
    payload: String,
    modifier: Modifier = Modifier
) {
    val bitMatrix = remember(payload) {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        runCatching {
            QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
        }.getOrNull()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val matrix = bitMatrix ?: return@Canvas
            val cols = matrix.width
            val rows = matrix.height
            if (cols <= 0 || rows <= 0) return@Canvas

            val cellW = size.width / cols.toFloat()
            val cellH = size.height / rows.toFloat()
            val moduleColor = Color(0xFF080D1A)

            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    if (matrix.get(x, y)) {
                        drawRect(
                            color = moduleColor,
                            topLeft = Offset(x * cellW, y * cellH),
                            size = Size(cellW + 0.5f, cellH + 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvPairingQrDialog(
    guardState: GuardState,
    onRegenerateCode: () -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalKidShieldColors.current
    val qrPayload = remember(guardState.tvLocalIp, guardState.tvServerPort, guardState.pairingCode) {
        buildQrPairingUri(guardState)
    }
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { closeFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = semantic.family,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Ghép nối TV với Điện thoại",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Mở KidShield TV trên điện thoại cùng Wi-Fi → Tab \"Từ điện thoại\" → Quét mã QR hoặc nhập mã 6 số bên dưới.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                QrCodeCanvas(
                    payload = qrPayload,
                    modifier = Modifier
                        .size(216.dp)
                        .testTag("tv_pairing_qr_canvas")
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = semantic.family.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = semantic.familyContainer.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "MÃ GHÉP NỐI 6 SỐ",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.family
                        )
                        Text(
                            text = formatPairingCodeDisplay(guardState.pairingCode),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.testTag("tv_pairing_code_text")
                        )
                        Text(
                            text = if (!guardState.tvLocalIp.isNullOrBlank()) {
                                "Địa chỉ TV: ${guardState.tvLocalIp}:${guardState.tvServerPort}"
                            } else {
                                "Cổng LAN: ${guardState.tvServerPort}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onRegenerateCode,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .tvFocusHighlight(shape = RoundedCornerShape(25.dp))
                            .testTag("tv_qr_regenerate_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đổi mã mới")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .focusRequester(closeFocusRequester)
                            .tvFocusHighlight(shape = RoundedCornerShape(25.dp), focusedBorderColor = Color.White)
                            .testTag("tv_qr_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đóng")
                    }
                }
            }
        }
    }
}
