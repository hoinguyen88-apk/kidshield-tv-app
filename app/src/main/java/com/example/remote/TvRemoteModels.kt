package com.example.remote

import com.example.data.model.LockReason
import com.example.data.model.WatchMode

enum class RemoteCommandAction {
    SWITCH_FAMILY,
    SWITCH_KIDS,
    GRANT_1H,
    TOGGLE_PAUSE,
    POWER_OFF_TV,
    WAKE_TV
}

data class DiscoveredTvDevice(
    val serviceName: String,
    val host: String,
    val port: Int,
    val deviceLabel: String = serviceName
)

data class RemoteTvStatus(
    val deviceName: String,
    val host: String,
    val port: Int,
    val activeMode: WatchMode,
    val isPinConfigured: Boolean,
    val kidsRemainingSeconds: Int,
    val effectiveRemainingSeconds: Int,
    val isKidsLockedOut: Boolean,
    val lockReason: LockReason,
    val isTimerPaused: Boolean,
    val isNetworkBlocked: Boolean,
    val currentClockFormatted: String,
    val secondsUntilCurfew: Int,
    val weeklyKidsMinutes: Int,
    val weeklyFamilyMinutes: Int,
    val weeklyBlockEvents: Int,
    val isDeviceAdminActive: Boolean = false,
    val isTvStandbyBlackout: Boolean = false
)

sealed class RemoteCommandResult {
    data class Success(val message: String, val updatedStatus: RemoteTvStatus?) : RemoteCommandResult()
    data class Failure(val message: String) : RemoteCommandResult()
}
