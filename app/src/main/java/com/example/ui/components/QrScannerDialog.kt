package com.example.ui.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.LocalKidShieldColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerDialog(
    onQrScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val semantic = LocalKidShieldColors.current
    val latestOnScanned by rememberUpdatedState(onQrScanned)

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val hasDecoded = remember { AtomicBoolean(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null

        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                boundProvider = cameraProvider

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val qrReader = QRCodeReader()
                val decodeHints = mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )

                analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        if (hasDecoded.get()) return@setAnalyzer
                        val decodedText = decodeQrFromImageProxy(imageProxy, qrReader, decodeHints)
                        if (!decodedText.isNullOrBlank() && hasDecoded.compareAndSet(false, true)) {
                            mainExecutor.execute {
                                latestOnScanned(decodedText)
                            }
                        }
                    } finally {
                        imageProxy.close()
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                cameraError = "Không mở được camera trên thiết bị này (${e.localizedMessage ?: "Kiểm tra quyền camera"})"
            }
        }, mainExecutor)

        onDispose {
            runCatching { boundProvider?.unbindAll() }
            runCatching { analysisExecutor.shutdown() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF060A14)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Dark top/bottom scrim + scan reticle
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xCC0B1120)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = semantic.kidsSafe
                            )
                            Text(
                                text = "Đưa camera vào mã QR trên màn hình Xiaomi TV",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }

                    if (cameraError != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = cameraError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(
                                    width = 3.dp,
                                    color = semantic.kidsSafe,
                                    shape = RoundedCornerShape(24.dp)
                                )
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xE61E293B),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("qr_scanner_cancel")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đóng camera")
                    }
                }
            }
        }
    }
}

private fun decodeQrFromImageProxy(
    imageProxy: ImageProxy,
    reader: QRCodeReader,
    hints: Map<DecodeHintType, Any>
): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val width = imageProxy.width
    val height = imageProxy.height
    val rowStride = plane.rowStride

    val yBytes = ByteArray(width * height)
    if (rowStride == width) {
        buffer.get(yBytes)
    } else {
        val rowBuf = ByteArray(rowStride)
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            val remaining = buffer.remaining()
            if (remaining >= rowStride) {
                buffer.get(rowBuf, 0, rowStride)
                System.arraycopy(rowBuf, 0, yBytes, row * width, width)
            } else {
                buffer.get(yBytes, row * width, minOf(width, remaining))
            }
        }
    }

    // Attempt decode in captured orientation first
    tryDecodeYuv(yBytes, width, height, reader, hints)?.let { return it }

    // Also try 90-degree rotated buffer for portrait phone camera sensors
    val rotated = rotateYPlane90Clockwise(yBytes, width, height)
    return tryDecodeYuv(rotated, height, width, reader, hints)
}

private fun tryDecodeYuv(
    data: ByteArray,
    width: Int,
    height: Int,
    reader: QRCodeReader,
    hints: Map<DecodeHintType, Any>
): String? {
    return try {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        reader.decode(bitmap, hints).text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

private fun rotateYPlane90Clockwise(data: ByteArray, width: Int, height: Int): ByteArray {
    val out = ByteArray(data.size)
    var index = 0
    for (x in 0 until width) {
        for (y in height - 1 downTo 0) {
            out[index++] = data[y * width + x]
        }
    }
    return out
}
