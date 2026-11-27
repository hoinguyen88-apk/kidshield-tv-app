package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GuardState
import com.example.data.model.WatchMode
import com.example.ui.components.tvFocusHighlight
import com.example.ui.theme.LocalKidShieldColors

@Composable
fun SettingsScreen(
    guardState: GuardState,
    onUnlockParent: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    onToggleShuffledKeypad: (Boolean) -> Unit,
    onToggleLockToKidsOnBoot: (Boolean) -> Unit,
    onToggleTvServer: (Boolean) -> Unit,
    onGrantNewKidsHour: () -> Unit,
    onChangePin: () -> Unit,
    onLockParentPanel: () -> Unit,
    onRequestDeviceAdmin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUnlocked = guardState.isParentUnlockedInApp || guardState.activeMode == WatchMode.FAMILY

    if (!isUnlocked) {
        ParentLockedGate(
            title = "Cài đặt dành cho Phụ huynh",
            description = "Nhập mã PIN 8 số để thay đổi cấu hình bảo mật, cấp thêm giờ xem hoặc đổi mã PIN.",
            onUnlock = onUnlockParent,
            modifier = modifier
        )
        return
    }

    val semantic = LocalKidShieldColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // VPN Protection Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (guardState.isVpnPermissionGranted) semantic.kidsSafeContainer
                                else semantic.warningContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnLock,
                            contentDescription = stringResource(R.string.settings_vpn_shield),
                            tint = if (guardState.isVpnPermissionGranted) semantic.kidsSafe else semantic.warning
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_vpn_shield),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                if (guardState.isVpnPermissionGranted) R.string.settings_vpn_active
                                else R.string.settings_vpn_inactive
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (guardState.isVpnPermissionGranted) semantic.kidsSafe else semantic.warning
                        )
                    }
                }
                if (!guardState.isVpnPermissionGranted) {
                    Button(
                        onClick = onRequestVpnPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .tvFocusHighlight(shape = RoundedCornerShape(24.dp))
                            .testTag("settings_enable_vpn_button")
                    ) {
                        Text(stringResource(R.string.btn_enable_vpn))
                    }
                }
            }
        }

        // Device Admin Power-Off Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (guardState.isDeviceAdminActive) semantic.kidsSafeContainer
                                else semantic.familyContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Quyền tắt màn hình TV từ xa",
                            tint = if (guardState.isDeviceAdminActive) semantic.kidsSafe else semantic.family
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quyền tắt màn hình TV từ xa (Device Admin)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (guardState.isDeviceAdminActive) {
                                "Đã kích hoạt — Điện thoại có thể đưa TV vào chế độ ngủ ngay lập tức"
                            } else {
                                "Chưa bật — Nên bật trên Xiaomi TV để tắt hẳn màn hình khi ra lệnh từ điện thoại"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (guardState.isDeviceAdminActive) semantic.kidsSafe else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!guardState.isDeviceAdminActive) {
                    FilledTonalButton(
                        onClick = onRequestDeviceAdmin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .tvFocusHighlight(shape = RoundedCornerShape(24.dp))
                            .testTag("settings_enable_device_admin_button")
                    ) {
                        Text("Kích hoạt quyền tắt màn hình TV")
                    }
                }
            }
        }

        // Security Toggles
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingToggleRow(
                    icon = Icons.Default.CastConnected,
                    title = "Cho phép Điện thoại điều khiển TV qua Wi-Fi",
                    subtitle = if (guardState.tvServerEnabled && !guardState.tvLocalIp.isNullOrBlank()) {
                        "Đang mở tại ${guardState.tvLocalIp}:${guardState.tvServerPort} (hoạt động cả khi TV ngắt mạng)"
                    } else {
                        "Bật trên Xiaomi Google TV để đổi chế độ & xem báo cáo từ điện thoại cùng Wi-Fi"
                    },
                    checked = guardState.tvServerEnabled,
                    onCheckedChange = onToggleTvServer,
                    testTag = "toggle_tv_server"
                )

                SettingToggleRow(
                    icon = Icons.Default.Shuffle,
                    title = stringResource(R.string.settings_anti_peek),
                    subtitle = stringResource(R.string.settings_anti_peek_desc),
                    checked = guardState.shuffledKeypadEnabled,
                    onCheckedChange = onToggleShuffledKeypad,
                    testTag = "toggle_shuffled_keypad"
                )

                SettingToggleRow(
                    icon = Icons.Default.RestartAlt,
                    title = stringResource(R.string.settings_boot_kids),
                    subtitle = stringResource(R.string.settings_boot_kids_desc),
                    checked = guardState.lockToKidsOnBoot,
                    onCheckedChange = onToggleLockToKidsOnBoot,
                    testTag = "toggle_boot_kids"
                )
            }
        }

        // Policy Overview
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quy tắc bảo vệ đang áp dụng",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                PolicyRow(
                    icon = Icons.Default.ChildCare,
                    text = "Mặc định Chế độ Trẻ em: tối đa 60 phút mỗi phiên, tự ngắt mạng khi hết giờ."
                )
                PolicyRow(
                    icon = Icons.Default.NightlightRound,
                    text = "Khung giờ giới nghiêm: khóa hoàn toàn Chế độ Trẻ em từ 21:00 đến 06:00."
                )
                PolicyRow(
                    icon = Icons.Default.EnhancedEncryption,
                    text = "Mã PIN 8 số băm PBKDF2-SHA256, che giấu tức thì khi nhập và chống tắt mở lại."
                )
            }
        }

        // Parent Actions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onGrantNewKidsHour,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .tvFocusHighlight(shape = RoundedCornerShape(26.dp))
                    .testTag("settings_grant_hour_button")
            ) {
                Icon(Icons.Default.AddAlarm, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cấp lại phiên 1 giờ cho Trẻ em")
            }

            OutlinedButton(
                onClick = onChangePin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .tvFocusHighlight(shape = RoundedCornerShape(26.dp))
                    .testTag("settings_change_pin_button")
            ) {
                Icon(Icons.Default.Pin, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_change_pin))
            }

            if (guardState.activeMode == WatchMode.KIDS) {
                OutlinedButton(
                    onClick = onLockParentPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .tvFocusHighlight(shape = RoundedCornerShape(25.dp))
                        .testTag("settings_lock_parent_button")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Khóa lại khu vực Phụ huynh")
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .tvFocusHighlight(shape = RoundedCornerShape(16.dp))
                .testTag(testTag)
        )
    }
}

@Composable
private fun PolicyRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
