package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.LockReason
import com.example.data.model.WatchMode
import com.example.ui.PhoneRemoteUiState
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.WeeklyChartCanvas
import com.example.ui.components.formatMinutesSeconds
import com.example.ui.components.tvFocusHighlight
import com.example.ui.theme.LocalKidShieldColors

@Composable
fun PhoneRemoteScreen(
    remoteState: PhoneRemoteUiState,
    onStartDiscovery: (Boolean) -> Unit,
    onStopDiscovery: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onConnectByPairingCode: (String) -> Unit,
    onQrScanned: (String) -> Unit,
    onCameraPermissionDenied: () -> Unit,
    onConnect: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onRemoteSwitchFamily: () -> Unit,
    onRemoteSwitchKids: () -> Unit,
    onRemoteGrantHour: () -> Unit,
    onRemoteFetchReport: () -> Unit,
    onRemotePowerOffTv: () -> Unit,
    onRemoteWakeTv: () -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onStartDiscovery(false)
        onDispose { onStopDiscovery() }
    }

    val context = LocalContext.current
    val semantic = LocalKidShieldColors.current
    var remoteDayIndex by remember { mutableIntStateOf(6) }
    var showManualIp by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            onCameraPermissionDenied()
        }
    }

    val openQrScanner: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showQrScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(semantic.familyContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Điều khiển TV từ điện thoại",
                    tint = semantic.family
                )
            }
            Column {
                Text(
                    text = "Điều khiển Xiaomi TV từ Điện thoại",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tự động tìm và kết nối TV cùng Wi-Fi trong 1 chạm, kể cả khi TV đã ngắt mạng.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick QR & 6-Digit Pairing Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = semantic.family
                    )
                    Text(
                        text = "Ghép nối nhanh bằng QR hoặc Mã 6 số",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Trên màn hình TV, bấm \"Hiện mã QR & Mã số ghép nối\" rồi quét hoặc nhập 6 số dưới đây.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = openQrScanner,
                    enabled = !remoteState.isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.family,
                        contentColor = Color(0xFF0A0F2C)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .tvFocusHighlight(shape = RoundedCornerShape(25.dp))
                        .testTag("scan_qr_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Quét mã QR trên màn hình TV")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = remoteState.pairingCodeInput,
                        onValueChange = onPairingCodeChange,
                        label = { Text("Mã ghép nối 6 số") },
                        placeholder = { Text("539204") },
                        leadingIcon = {
                            Icon(Icons.Default.Pin, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("pairing_code_input")
                    )

                    FilledTonalButton(
                        onClick = { onConnectByPairingCode(remoteState.pairingCodeInput) },
                        enabled = remoteState.pairingCodeInput.length == 6 && !remoteState.isConnecting,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .tvFocusHighlight(shape = RoundedCornerShape(27.dp))
                            .testTag("connect_pairing_code_button")
                    ) {
                        if (remoteState.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Kết nối")
                        }
                    }
                }

                AnimatedVisibility(visible = remoteState.connectionError != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = remoteState.connectionError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Connected TV Live Panel
        val liveStatus = remoteState.remoteStatus
        if (liveStatus != null && remoteState.connectedHost != null) {
            val isFamily = liveStatus.activeMode == WatchMode.FAMILY
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = semantic.kidsSafe
                            )
                            Column {
                                Text(
                                    text = liveStatus.deviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${liveStatus.host} • Giờ TV: ${liveStatus.currentClockFormatted}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (liveStatus.isNetworkBlocked) semantic.lockedContainer else semantic.kidsSafeContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (liveStatus.isNetworkBlocked) Icons.Default.WifiOff else Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (liveStatus.isNetworkBlocked) semantic.locked else semantic.kidsSafe,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (liveStatus.isNetworkBlocked) "NGẮT MẠNG" else "MẠNG MỞ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (liveStatus.isNetworkBlocked) semantic.locked else semantic.kidsSafe
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = if (isFamily) semantic.familyContainer else semantic.kidsSafeContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFamily) "TV đang ở Chế độ Gia đình" else "TV đang ở Chế độ Trẻ em",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = when {
                                        isFamily -> "Mở khóa hoàn toàn cho cả gia đình"
                                        liveStatus.lockReason == LockReason.BEDTIME_CURFEW -> "Đã khóa do quá giờ giới nghiêm 21:00"
                                        liveStatus.isKidsLockedOut -> "Đã hết 1 giờ xem • Mạng TV đã ngắt"
                                        else -> "Tự động ngắt mạng khi hết giờ hoặc sau 21:00"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                            Text(
                                text = if (isFamily) "∞" else formatMinutesSeconds(liveStatus.effectiveRemainingSeconds),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRemoteSwitchKids,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .tvFocusHighlight(shape = RoundedCornerShape(26.dp))
                                .testTag("remote_switch_kids_button")
                        ) {
                            Icon(Icons.Default.ChildCare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chế độ Trẻ em")
                        }

                        Button(
                            onClick = onRemoteSwitchFamily,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = semantic.family,
                                contentColor = Color(0xFF0A0F2C)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .tvFocusHighlight(shape = RoundedCornerShape(26.dp))
                                .testTag("remote_switch_family_button")
                        ) {
                            Icon(Icons.Default.FamilyRestroom, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chế độ Gia đình")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onRemoteGrantHour,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .tvFocusHighlight(shape = RoundedCornerShape(24.dp))
                                .testTag("remote_grant_hour_button")
                        ) {
                            Icon(Icons.Default.AddAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cấp thêm 1h")
                        }

                        FilledTonalButton(
                            onClick = onRemoteFetchReport,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .tvFocusHighlight(shape = RoundedCornerShape(24.dp))
                                .testTag("remote_fetch_report_button")
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Báo cáo TV")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRemotePowerOffTv,
                            enabled = !remoteState.isConnecting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = semantic.locked,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .tvFocusHighlight(shape = RoundedCornerShape(26.dp), focusedBorderColor = Color.White)
                                .testTag("remote_power_off_button")
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tắt TV ngay (1 chạm)")
                        }

                        if (liveStatus.isTvStandbyBlackout) {
                            FilledTonalButton(
                                onClick = onRemoteWakeTv,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .tvFocusHighlight(shape = RoundedCornerShape(26.dp))
                                    .testTag("remote_wake_tv_button")
                            ) {
                                Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mở lại TV")
                            }
                        }
                    }

                    Text(
                        text = if (liveStatus.isDeviceAdminActive) {
                            "Quyền tắt màn hình TV (Device Admin): Đã bật — bấm Tắt TV sẽ đưa màn hình vào chế độ ngủ ngay."
                        } else {
                            "Chưa bật Device Admin trên TV — bấm Tắt TV sẽ ngắt mạng, tắt tiếng và đưa TV về màn hình đen chờ (bật Device Admin trên TV để tắt màn hình cứng)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .tvFocusHighlight(shape = RoundedCornerShape(24.dp))
                            .testTag("remote_disconnect_button")
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ngắt kết nối với TV này")
                    }
                }
            }

            val remoteReport = remoteState.remoteWeeklyReport
            AnimatedVisibility(visible = remoteReport != null) {
                if (remoteReport != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Báo cáo tuần từ ${liveStatus.deviceName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Trẻ em: ${remoteReport.totalKidsSeconds / 60} phút • Gia đình: ${remoteReport.totalFamilySeconds / 60} phút • Ngắt mạng: ${remoteReport.totalBlockEvents} lần",
                                style = MaterialTheme.typography.bodyMedium,
                                color = semantic.kidsSafe
                            )
                            WeeklyChartCanvas(
                                days = remoteReport.days,
                                selectedDayIndex = remoteDayIndex,
                                onSelectDay = { remoteDayIndex = it }
                            )
                        }
                    }
                }
            }
        }

        // Fast 1-Tap Auto Discovery Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "TV trên Wi-Fi nhà",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    FilledTonalButton(
                        onClick = { onStartDiscovery(true) },
                        modifier = Modifier.testTag("remote_rescan_button")
                    ) {
                        if (remoteState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Quét lại")
                    }
                }

                if (remoteState.discoveredTvs.isEmpty()) {
                    Text(
                        text = "Đang tự động quét toàn bộ Wi-Fi nhà để tìm Xiaomi Google TV (mất khoảng 1–2 giây)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    remoteState.discoveredTvs.forEach { tv ->
                        val isCurrentlyConnected = remoteState.connectedHost == tv.host
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusHighlight(shape = RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onConnect(tv.host, tv.port) }
                                .testTag("discovered_tv_${tv.host}"),
                            color = if (isCurrentlyConnected) {
                                semantic.kidsSafeContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CastConnected, contentDescription = null, tint = semantic.kidsSafe)
                                    Column {
                                        Text(
                                            text = tv.deviceLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = tv.host,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = if (isCurrentlyConnected) "Đang kết nối" else "Kết nối ngay",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isCurrentlyConnected) semantic.kidsSafe else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Collapsible Quick IP Fallback
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                TextButton(
                    onClick = { showManualIp = !showManualIp },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_manual_ip_button")
                ) {
                    Text(
                        text = "Không tự thấy TV? Nhập nhanh IP trên màn hình TV",
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showManualIp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }

                AnimatedVisibility(visible = showManualIp) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = remoteState.manualHost,
                                onValueChange = onHostChange,
                                label = { Text("IP của TV") },
                                placeholder = { Text("192.168.1.45") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(2f)
                                    .testTag("remote_host_input")
                            )
                            OutlinedTextField(
                                value = remoteState.manualPort,
                                onValueChange = onPortChange,
                                label = { Text("Cổng") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("remote_port_input")
                            )
                        }

                        if (remoteState.connectionError != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = remoteState.connectionError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val port = remoteState.manualPort.toIntOrNull() ?: 8745
                                onConnect(remoteState.manualHost, port)
                            },
                            enabled = !remoteState.isConnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .tvFocusHighlight(shape = RoundedCornerShape(25.dp))
                                .testTag("remote_connect_button")
                        ) {
                            if (remoteState.isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (remoteState.isConnecting) "Đang kết nối TV..." else "Kết nối Xiaomi TV")
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onQrScanned = { rawText ->
                showQrScanner = false
                onQrScanned(rawText)
            },
            onDismiss = { showQrScanner = false }
        )
    }
}
