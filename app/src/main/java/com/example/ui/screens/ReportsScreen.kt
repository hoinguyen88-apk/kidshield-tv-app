package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.WatchSessionEntity
import com.example.data.model.GuardState
import com.example.data.model.WatchMode
import com.example.data.model.WeeklyReportUiState
import com.example.ui.components.WeeklyChartCanvas
import com.example.ui.components.tvFocusHighlight
import com.example.ui.theme.LocalKidShieldColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    guardState: GuardState,
    weeklyReport: WeeklyReportUiState,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    onUnlockParent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnlocked = guardState.isParentUnlockedInApp || guardState.activeMode == WatchMode.FAMILY

    if (!isUnlocked) {
        ParentLockedGate(
            title = stringResource(R.string.reports_locked_title),
            description = stringResource(R.string.reports_locked_desc),
            onUnlock = onUnlockParent,
            modifier = modifier
        )
        return
    }

    val semantic = LocalKidShieldColors.current
    val selectedDay = weeklyReport.days.getOrNull(selectedDayIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val isWide = maxWidth >= 680.dp

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(
                    text = stringResource(R.string.reports_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.reports_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // KPI Summary Cards
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        icon = Icons.Default.ChildCare,
                        label = stringResource(R.string.kpi_weekly_kids),
                        value = formatHoursMinutes(weeklyReport.totalKidsSeconds),
                        accent = semantic.kidsSafe,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        icon = Icons.Default.FamilyRestroom,
                        label = stringResource(R.string.kpi_weekly_family),
                        value = formatHoursMinutes(weeklyReport.totalFamilySeconds),
                        accent = semantic.family,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        icon = Icons.Default.Block,
                        label = stringResource(R.string.kpi_blocks_count),
                        value = "${weeklyReport.totalBlockEvents} lần",
                        accent = semantic.locked,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        icon = Icons.Default.TrendingUp,
                        label = stringResource(R.string.kpi_daily_avg),
                        value = "${weeklyReport.avgDailyKidsMinutes} phút",
                        accent = semantic.warning,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        icon = Icons.Default.ChildCare,
                        label = stringResource(R.string.kpi_weekly_kids),
                        value = formatHoursMinutes(weeklyReport.totalKidsSeconds),
                        accent = semantic.kidsSafe,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        icon = Icons.Default.FamilyRestroom,
                        label = stringResource(R.string.kpi_weekly_family),
                        value = formatHoursMinutes(weeklyReport.totalFamilySeconds),
                        accent = semantic.family,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        icon = Icons.Default.Block,
                        label = stringResource(R.string.kpi_blocks_count),
                        value = "${weeklyReport.totalBlockEvents} lần",
                        accent = semantic.locked,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        icon = Icons.Default.TrendingUp,
                        label = stringResource(R.string.kpi_daily_avg),
                        value = "${weeklyReport.avgDailyKidsMinutes} phút",
                        accent = semantic.warning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Weekly Bar Chart Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Biểu đồ 7 ngày gần nhất",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    WeeklyChartCanvas(
                        days = weeklyReport.days,
                        selectedDayIndex = selectedDayIndex,
                        onSelectDay = onSelectDay
                    )

                    if (selectedDay != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedDay.dayLabel} (${selectedDay.fullDateLabel})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Trẻ em: ${selectedDay.kidsMinutes}p • Gia đình: ${selectedDay.familyMinutes}p • Ngắt mạng: ${selectedDay.blockEventsCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Recent Sessions List
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
                    Text(
                        text = "Nhật ký phiên xem thực tế",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val daySessions = selectedDay?.sessions?.takeIf { it.isNotEmpty() }
                        ?: weeklyReport.recentSessions

                    if (daySessions.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_sessions_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.empty_sessions_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        daySessions.forEach { session ->
                            SessionHistoryRow(session = session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParentLockedGate(
    title: String,
    description: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalKidShieldColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(semantic.familyContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = title,
                        tint = semantic.family,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onUnlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .tvFocusHighlight(shape = RoundedCornerShape(26.dp))
                        .testTag("unlock_parent_gate_button")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_unlock_parent))
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionHistoryRow(session: WatchSessionEntity) {
    val semantic = LocalKidShieldColors.current
    val isKids = session.mode == WatchMode.KIDS.name
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val startStr = timeFormatter.format(Date(session.startedAtMillis))
    val endStr = timeFormatter.format(Date(session.endedAtMillis))

    val (reasonLabel, reasonColor) = when (session.endReason) {
        "LIMIT_1H" -> "Ngắt do hết 1h" to semantic.locked
        "CURFEW_21H" -> "Ngắt do quá 21:00" to semantic.locked
        "ACTIVE" -> "Đang xem" to semantic.kidsSafe
        "SWITCHED_MODE" -> "Đổi chế độ" to semantic.family
        "BOOT_LOCKED" -> "Khởi động lại" to semantic.warning
        else -> "Tạm dừng" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isKids) Icons.Default.ChildCare else Icons.Default.FamilyRestroom,
                    contentDescription = null,
                    tint = if (isKids) semantic.kidsSafe else semantic.family
                )
                Column {
                    Text(
                        text = if (isKids) "Chế độ Trẻ em" else "Chế độ Gia đình",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${session.dateKey} • $startStr – $endStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatHoursMinutes(session.durationSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = reasonLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = reasonColor
                )
            }
        }
    }
}

private fun formatHoursMinutes(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val hours = mins / 60
    val remMins = mins % 60
    return when {
        hours > 0 -> "${hours}g ${remMins}p"
        mins > 0 -> "${mins} phút"
        totalSeconds > 0 -> "${totalSeconds} giây"
        else -> "0 phút"
    }
}
