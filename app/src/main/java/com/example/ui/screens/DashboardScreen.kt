package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GuardState
import com.example.data.model.LockReason
import com.example.data.model.WatchMode
import com.example.ui.components.TimerRingCanvas
import com.example.ui.components.TvPairingQrDialog
import com.example.ui.components.formatMinutesSeconds
import com.example.ui.components.formatPairingCodeDisplay
import com.example.ui.components.tvFocusHighlight
import com.example.ui.theme.LocalKidShieldColors

@Composable
fun DashboardScreen(
    guardState: GuardState,
    onRequestVpnPermission: () -> Unit,
    onRequestSetupPin: () -> Unit,
    onRequestFamilyMode: () -> Unit,
    onSwitchToKidsMode: () -> Unit,
    onRequestGrantExtraHour: () -> Unit,
    onTogglePause: () -> Unit,
    onRegeneratePairingCode: () -> Unit = {},
    onRequestDeviceAdmin: () -> Unit = {},
    onOpenPhoneRemote: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPairingQrDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val isWide = maxWidth >= 680.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeroModeBannerCard(guardState = guardState)
                    ModeSelectorSwitchBar(
                        activeMode = guardState.activeMode,
                        onSelectKids = onSwitchToKidsMode,
                        onSelectFamily = onRequestFamilyMode
                    )
                    TimerRingCard(guardState = guardState)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SetupAndVpnAlerts(
                        guardState = guardState,
                        onRequestSetupPin = onRequestSetupPin,
                        onRequestVpnPermission = onRequestVpnPermission,
                        onRequestDeviceAdmin = onRequestDeviceAdmin,
                        onOpenPhoneRemote = onOpenPhoneRemote
                    )
                    LockoutWarningCard(guardState = guardState)
                    CurfewAndShieldCard(guardState = guardState)
                    TvLanPairingInfoCard(
                        guardState = guardState,
                        onOpenQrDialog = { showPairingQrDialog = true }
                    )
                    ActionControlsCard(
                        guardState = guardState,
                        onRequestFamilyMode = onRequestFamilyMode,
                        onSwitchToKidsMode = onSwitchToKidsMode,
                        onRequestGrantExtraHour = onRequestGrantExtraHour,
                        onTogglePause = onTogglePause
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HeroModeBannerCard(guardState = guardState)
                ModeSelectorSwitchBar(
                    activeMode = guardState.activeMode,
                    onSelectKids = onSwitchToKidsMode,
                    onSelectFamily = onRequestFamilyMode
                )
                SetupAndVpnAlerts(
                    guardState = guardState,
                    onRequestSetupPin = onRequestSetupPin,
                    onRequestVpnPermission = onRequestVpnPermission,
                    onRequestDeviceAdmin = onRequestDeviceAdmin,
                    onOpenPhoneRemote = onOpenPhoneRemote
                )
                TimerRingCard(guardState = guardState)
                LockoutWarningCard(guardState = guardState)
                CurfewAndShieldCard(guardState = guardState)
                TvLanPairingInfoCard(
                    guardState = guardState,
                    onOpenQrDialog = { showPairingQrDialog = true }
                )
                ActionControlsCard(
                    guardState = guardState,
                    onRequestFamilyMode = onRequestFamilyMode,
                    onSwitchToKidsMode = onSwitchToKidsMode,
                    onRequestGrantExtraHour = onRequestGrantExtraHour,
                    onTogglePause = onTogglePause
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showPairingQrDialog) {
        TvPairingQrDialog(
            guardState = guardState,
            onRegenerateCode = onRegeneratePairingCode,
            onDismiss = { showPairingQrDialog = false }
        )
    }
}

@Composable
private fun ModeSelectorSwitchBar(
    activeMode: WatchMode,
    onSelectKids: () -> Unit,
    onSelectFamily: () -> Unit
) {
    val semantic = LocalKidShieldColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChoiceChip(
                title = "Chế độ Trẻ em",
                subtitle = "1h • Khóa 21:00",
                icon = Icons.Default.ChildCare,
                selected = activeMode == WatchMode.KIDS,
                accentColor = semantic.kidsSafe,
                containerColor = semantic.kidsSafeContainer,
                onClick = { if (activeMode != WatchMode.KIDS) onSelectKids() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_selector_kids")
            )
            ModeChoiceChip(
                title = "Chế độ Gia đình",
                subtitle = "PIN 8 số • Tự do",
                icon = Icons.Default.FamilyRestroom,
                selected = activeMode == WatchMode.FAMILY,
                accentColor = semantic.family,
                containerColor = semantic.familyContainer,
                onClick = { if (activeMode != WatchMode.FAMILY) onSelectFamily() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_selector_family")
            )
        }
    }
}

@Composable
private fun ModeChoiceChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .tvFocusHighlight(shape = shape, focusedBorderColor = accentColor)
            .clip(shape)
            .background(if (selected) containerColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TvLanPairingInfoCard(
    guardState: GuardState,
    onOpenQrDialog: () -> Unit
) {
    AnimatedVisibility(visible = guardState.tvServerEnabled) {
        val semantic = LocalKidShieldColors.current
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(semantic.familyContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (guardState.isTvDevice) Icons.Default.Tv else Icons.Default.CastConnected,
                            contentDescription = "Điều khiển từ điện thoại",
                            tint = semantic.family
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ghép nối với Điện thoại cùng Wi-Fi",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mã ghép nối: ${formatPairingCodeDisplay(guardState.pairingCode)}" +
                                if (!guardState.tvLocalIp.isNullOrBlank()) " • IP: ${guardState.tvLocalIp}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = semantic.kidsSafe
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenQrDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusHighlight(shape = RoundedCornerShape(24.dp))
                        .testTag("show_qr_pairing_button")
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hiện mã QR & Mã số ghép nối")
                }
            }
        }
    }
}

@Composable
private fun HeroModeBannerCard(guardState: GuardState) {
    val semantic = LocalKidShieldColors.current
    val isFamily = guardState.activeMode == WatchMode.FAMILY
    val bannerRes = if (isFamily) R.drawable.img_family_cinema else R.drawable.img_kids_guardian
    val badgeBg = if (isFamily) semantic.familyContainer else semantic.kidsSafeContainer
    val badgeTextColor = if (isFamily) semantic.family else semantic.kidsSafe

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = bannerRes),
                contentDescription = if (isFamily) "Chế độ Gia đình" else "Chế độ Trẻ em",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xEB080D1A),
                                Color(0xCC080D1A),
                                Color(0x66080D1A)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = badgeBg.copy(alpha = 0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isFamily) Icons.Default.FamilyRestroom else Icons.Default.ChildCare,
                                contentDescription = null,
                                tint = badgeTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (isFamily) R.string.mode_family_badge else R.string.mode_kids_badge
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = badgeTextColor
                            )
                        }
                    }

                    NetworkStatusPill(isBlocked = guardState.isNetworkBlocked)
                }

                Column {
                    Text(
                        text = stringResource(if (isFamily) R.string.mode_family else R.string.mode_kids),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(
                            if (isFamily) R.string.family_rule_summary else R.string.kids_rule_summary
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkStatusPill(isBlocked: Boolean) {
    val semantic = LocalKidShieldColors.current
    val bg = if (isBlocked) semantic.lockedContainer else semantic.kidsSafeContainer
    val fg = if (isBlocked) semantic.locked else semantic.kidsSafe

    Surface(
        shape = RoundedCornerShape(50),
        color = bg.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isBlocked) Icons.Default.WifiOff else Icons.Default.Wifi,
                contentDescription = if (isBlocked) "Đã ngắt mạng" else "Đang kết nối mạng",
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isBlocked) "ĐÃ NGẮT MẠNG" else "MẠNG MỞ",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = fg
            )
        }
    }
}

@Composable
private fun SetupAndVpnAlerts(
    guardState: GuardState,
    onRequestSetupPin: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onOpenPhoneRemote: () -> Unit
) {
    val semantic = LocalKidShieldColors.current

    // On a Parent Phone/Tablet (not Android TV), provide an unmistakable 1-tap shortcut to TV Remote Control
    AnimatedVisibility(visible = !guardState.isTvDevice) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusHighlight(shape = MaterialTheme.shapes.medium, focusedBorderColor = semantic.family)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenPhoneRemote)
                .testTag("phone_role_shortcut_card"),
            shape = MaterialTheme.shapes.medium,
            color = semantic.familyContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(semantic.family.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Chế độ Điện thoại điều khiển TV",
                        tint = semantic.family
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bạn đang dùng trên Điện thoại?",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Chạm để quét QR hoặc nhập mã 6 số kết nối & tắt Xiaomi TV ngay (không cần cài đặt trên ĐT).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Mở điều khiển TV",
                    tint = semantic.family
                )
            }
        }
    }

    val totalSteps = 3
    val doneSteps = listOf(
        guardState.isPinConfigured,
        guardState.isVpnPermissionGranted,
        guardState.isDeviceAdminActive
    ).count { it }
    val needsSetup = doneSteps < totalSteps

    AnimatedVisibility(visible = needsSetup) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (guardState.isTvDevice) "Thiết lập nhanh trên TV" else "Thiết lập nhanh bảo vệ thiết bị",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = semantic.kidsSafeContainer
                    ) {
                        Text(
                            text = "Xong $doneSteps/$totalSteps",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = semantic.kidsSafe,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                SetupStepRow(
                    stepNumber = 1,
                    title = "Tạo mã PIN 8 số Phụ huynh",
                    subtitle = "Bảo vệ Chế độ Gia đình & Báo cáo",
                    isDone = guardState.isPinConfigured,
                    actionLabel = "Tạo PIN",
                    onClick = onRequestSetupPin,
                    testTag = "setup_pin_banner_button"
                )

                SetupStepRow(
                    stepNumber = 2,
                    title = "Bật Lá chắn Ngắt mạng (VPN)",
                    subtitle = "Tự ngắt mạng khi hết 1h hoặc sau 21:00",
                    isDone = guardState.isVpnPermissionGranted,
                    actionLabel = "Bật ngay",
                    onClick = onRequestVpnPermission,
                    testTag = "enable_vpn_button"
                )

                SetupStepRow(
                    stepNumber = 3,
                    title = "Bật quyền Tắt màn hình TV",
                    subtitle = "Cho phép điện thoại tắt TV thành chế độ ngủ",
                    isDone = guardState.isDeviceAdminActive,
                    actionLabel = "Kích hoạt",
                    onClick = onRequestDeviceAdmin,
                    testTag = "enable_device_admin_button"
                )
            }
        }
    }
}

@Composable
private fun SetupStepRow(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isDone: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    testTag: String
) {
    val semantic = LocalKidShieldColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDone) semantic.kidsSafeContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isDone) "Đã hoàn thành" else "Chưa thiết lập",
            tint = if (isDone) semantic.kidsSafe else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$stepNumber. $title",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isDone) "Đã thiết lập xong" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDone) semantic.kidsSafe else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isDone) {
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier
                    .height(48.dp)
                    .tvFocusHighlight(shape = RoundedCornerShape(24.dp))
                    .testTag(testTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun TimerRingCard(guardState: GuardState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (guardState.activeMode == WatchMode.FAMILY) {
                    stringResource(R.string.family_unlimited_title)
                } else {
                    stringResource(R.string.kids_timer_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            TimerRingCanvas(guardState = guardState)

            Spacer(modifier = Modifier.height(8.dp))

            if (guardState.activeMode == WatchMode.KIDS) {
                Text(
                    text = "Giờ hiện tại: ${guardState.currentClockFormatted} • Giới nghiêm: 21:00",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Phiên Gia đình đang chạy: ${formatMinutesSeconds(guardState.activeSessionDurationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LockoutWarningCard(guardState: GuardState) {
    val semantic = LocalKidShieldColors.current
    val isLocked = guardState.activeMode == WatchMode.KIDS && guardState.isKidsLockedOut

    AnimatedVisibility(visible = isLocked) {
        val isCurfew = guardState.lockReason == LockReason.BEDTIME_CURFEW
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, semantic.locked.copy(alpha = 0.5f), MaterialTheme.shapes.medium),
            shape = MaterialTheme.shapes.medium,
            color = semantic.lockedContainer
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isCurfew) Icons.Default.NightlightRound else Icons.Default.Lock,
                        contentDescription = "Trạng thái khóa TV",
                        tint = semantic.locked,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = stringResource(
                            if (isCurfew) R.string.lock_curfew_title else R.string.lock_1h_title
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = stringResource(
                        if (isCurfew) R.string.lock_curfew_subtitle else R.string.lock_1h_subtitle
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.25f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = semantic.locked,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.anti_reboot_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurfewAndShieldCard(guardState: GuardState) {
    val semantic = LocalKidShieldColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NightlightRound,
                        contentDescription = stringResource(R.string.curfew_label),
                        tint = semantic.warning
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.curfew_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (guardState.secondsUntilCurfew > 0) {
                            val h = guardState.secondsUntilCurfew / 3600
                            val m = (guardState.secondsUntilCurfew % 3600) / 60
                            "Còn ${h}g ${m}p trước giờ khóa 21:00"
                        } else {
                            "Đang trong khung giờ khóa đêm (21:00 – 06:00)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = stringResource(R.string.curfew_value),
                    style = MaterialTheme.typography.labelMedium,
                    color = semantic.warning,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionControlsCard(
    guardState: GuardState,
    onRequestFamilyMode: () -> Unit,
    onSwitchToKidsMode: () -> Unit,
    onRequestGrantExtraHour: () -> Unit,
    onTogglePause: () -> Unit
) {
    val semantic = LocalKidShieldColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (guardState.activeMode == WatchMode.KIDS) {
            Button(
                onClick = onRequestFamilyMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = semantic.family,
                    contentColor = Color(0xFF0A0F2C)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .tvFocusHighlight(shape = RoundedCornerShape(27.dp), focusedBorderColor = Color.White)
                    .testTag("switch_family_button")
            ) {
                Icon(Icons.Default.FamilyRestroom, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_switch_family),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (guardState.isKidsLockedOut) {
                FilledTonalButton(
                    onClick = onRequestGrantExtraHour,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .tvFocusHighlight(shape = RoundedCornerShape(26.dp))
                        .testTag("grant_extra_hour_button")
                ) {
                    Icon(Icons.Default.AddAlarm, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_grant_extra_hour))
                }
            } else {
                OutlinedButton(
                    onClick = onTogglePause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .tvFocusHighlight(shape = RoundedCornerShape(25.dp))
                        .testTag("toggle_pause_button")
                ) {
                    Icon(
                        imageVector = if (guardState.isTimerPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (guardState.isTimerPaused) {
                            "Tiếp tục xem & Mở mạng"
                        } else {
                            "Tạm nghỉ & Ngắt mạng tạm thời"
                        }
                    )
                }
            }
        } else {
            Button(
                onClick = onSwitchToKidsMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .tvFocusHighlight(shape = RoundedCornerShape(27.dp), focusedBorderColor = Color.White)
                    .testTag("switch_kids_button")
            ) {
                Icon(Icons.Default.ChildCare, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_switch_kids),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
