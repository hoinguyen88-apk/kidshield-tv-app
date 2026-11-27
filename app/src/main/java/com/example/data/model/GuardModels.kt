package com.example.data.model

import com.example.data.local.WatchSessionEntity

enum class WatchMode {
    KIDS,
    FAMILY
}

enum class LockReason {
    NONE,
    ONE_HOUR_LIMIT,
    BEDTIME_CURFEW
}

data class GuardState(
    val activeMode: WatchMode = WatchMode.KIDS,
    val isPinConfigured: Boolean = false,
    val kidsRemainingSeconds: Int = 3600,
    val sessionLimitSeconds: Int = 3600,
    val isKidsLockedOut: Boolean = false,
    val lockReason: LockReason = LockReason.NONE,
    val isTimerPaused: Boolean = false,
    val isNetworkBlocked: Boolean = false,
    val isVpnPermissionGranted: Boolean = false,
    val curfewStartHour: Int = 21,
    val curfewEndHour: Int = 6,
    val shuffledKeypadEnabled: Boolean = true,
    val lockToKidsOnBoot: Boolean = true,
    val currentHour24: Int = 12,
    val currentClockFormatted: String = "12:00",
    val secondsUntilCurfew: Int = 9 * 3600,
    val effectiveRemainingSeconds: Int = 3600,
    val activeSessionDurationSeconds: Int = 0,
    val isParentUnlockedInApp: Boolean = false,
    val pinCooldownRemainingSeconds: Int = 0,
    val isTvDevice: Boolean = false,
    val tvServerEnabled: Boolean = false,
    val tvLocalIp: String? = null,
    val tvServerPort: Int = 8745,
    val pairingCode: String = "000000",
    val isDeviceAdminActive: Boolean = false,
    val isTvStandbyBlackout: Boolean = false,
    val lastRemoteTvHost: String = "",
    val lastRemoteTvPort: Int = 8745
)

data class DailyUsagePoint(
    val dateKey: String,
    val dayLabel: String,
    val fullDateLabel: String,
    val kidsMinutes: Int,
    val familyMinutes: Int,
    val blockEventsCount: Int,
    val sessions: List<WatchSessionEntity>
) {
    val totalMinutes: Int get() = kidsMinutes + familyMinutes
}

data class WeeklyReportUiState(
    val days: List<DailyUsagePoint> = emptyList(),
    val totalKidsSeconds: Int = 0,
    val totalFamilySeconds: Int = 0,
    val totalBlockEvents: Int = 0,
    val avgDailyKidsMinutes: Int = 0,
    val recentSessions: List<WatchSessionEntity> = emptyList()
)

sealed class PinVerifyResult {
    data object Success : PinVerifyResult()
    data class WrongPin(val remainingAttemptsBeforeCooldown: Int) : PinVerifyResult()
    data class Cooldown(val secondsRemaining: Int) : PinVerifyResult()
    data object NotConfigured : PinVerifyResult()
}
