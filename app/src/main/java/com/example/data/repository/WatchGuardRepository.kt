package com.example.data.repository

import android.content.Context
import android.net.VpnService
import com.example.data.local.GuardPreferences
import com.example.data.local.WatchDao
import com.example.data.local.WatchSessionEntity
import com.example.data.model.DailyUsagePoint
import com.example.data.model.GuardState
import com.example.data.model.LockReason
import com.example.data.model.PinVerifyResult
import com.example.data.model.WatchMode
import com.example.data.model.WeeklyReportUiState
import com.example.data.security.PinCryptoHelper
import com.example.service.TvPowerController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WatchGuardRepository(
    private val context: Context,
    private val prefs: GuardPreferences,
    private val watchDao: WatchDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var latestWeeklySnapshot: WeeklyReportUiState = WeeklyReportUiState()

    @Volatile
    private var activeServerPort: Int = 8745

    private var activeSessionId: Long = prefs.activeSessionId
    private var activeSessionMode: WatchMode? = null
    private var activeSessionStartMillis: Long = 0L
    private var activeSessionDurationSeconds: Int = 0
    private var isParentUnlockedInApp: Boolean = false

    private val _guardState = MutableStateFlow(buildInitialGuardState())
    val guardState: StateFlow<GuardState> = _guardState.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                restoreOrEvaluateStateLocked(isBoot = false)
            }
        }
    }

    fun observeWeeklyReport(): Flow<WeeklyReportUiState> {
        val sevenDaysAgoMillis = startOfDayMillisOffset(-6)
        return watchDao.observeSessionsSince(sevenDaysAgoMillis).map { sessions ->
            buildWeeklyReport(sessions).also { latestWeeklySnapshot = it }
        }
    }

    fun getWeeklyReportSnapshot(): WeeklyReportUiState = latestWeeklySnapshot

    fun updateServerPort(port: Int) {
        activeServerPort = port
        _guardState.value = _guardState.value.copy(
            tvServerPort = port,
            tvLocalIp = com.example.remote.TvRemoteServer.getLocalIpv4Address()
        )
    }

    fun refreshVpnPermissionState() {
        val granted = VpnService.prepare(context) == null
        _guardState.value = _guardState.value.copy(isVpnPermissionGranted = granted)
    }

    fun refreshDeviceAdminState() {
        val active = TvPowerController.isDeviceAdminActive(context)
        _guardState.value = _guardState.value.copy(isDeviceAdminActive = active)
    }

    suspend fun onTick() {
        mutex.withLock {
            val nowMillis = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val isCurfew = isCurfewHour(hour)

            var mode = prefs.activeMode
            var remaining = prefs.kidsRemainingSeconds
            var lockedOut = prefs.isKidsLockedOut
            var reason = prefs.lockReason
            val paused = prefs.isTimerPaused

            if (mode == WatchMode.KIDS) {
                if (isCurfew) {
                    if (!lockedOut || reason != LockReason.BEDTIME_CURFEW) {
                        lockedOut = true
                        reason = LockReason.BEDTIME_CURFEW
                        prefs.isKidsLockedOut = true
                        prefs.lockReason = LockReason.BEDTIME_CURFEW
                        prefs.isTimerPaused = false
                        closeActiveSessionLocked("CURFEW_21H", nowMillis)
                    }
                } else {
                    // Outside curfew hours: if lockReason was BEDTIME_CURFEW, lift it cleanly if quota > 0
                    if (lockedOut && reason == LockReason.BEDTIME_CURFEW) {
                        if (remaining > 0) {
                            lockedOut = false
                            reason = LockReason.NONE
                            prefs.isKidsLockedOut = false
                            prefs.lockReason = LockReason.NONE
                        } else {
                            reason = LockReason.ONE_HOUR_LIMIT
                            prefs.lockReason = LockReason.ONE_HOUR_LIMIT
                        }
                    }

                    if (!lockedOut) {
                        if (remaining <= 0) {
                            lockedOut = true
                            reason = LockReason.ONE_HOUR_LIMIT
                            prefs.isKidsLockedOut = true
                            prefs.lockReason = LockReason.ONE_HOUR_LIMIT
                            prefs.isTimerPaused = false
                            closeActiveSessionLocked("LIMIT_1H", nowMillis)
                        } else if (!paused) {
                            remaining -= 1
                            prefs.kidsRemainingSeconds = remaining
                            ensureAndIncrementSessionLocked(WatchMode.KIDS, nowMillis)
                            if (remaining <= 0) {
                                lockedOut = true
                                reason = LockReason.ONE_HOUR_LIMIT
                                prefs.isKidsLockedOut = true
                                prefs.lockReason = LockReason.ONE_HOUR_LIMIT
                                closeActiveSessionLocked("LIMIT_1H", nowMillis)
                            }
                        } else {
                            // Paused in Kids Mode -> close active session so paused time isn't logged
                            closeActiveSessionLocked("PAUSED", nowMillis)
                        }
                    }
                }
            } else {
                // FAMILY MODE: unlimited viewing, track Family watch session
                ensureAndIncrementSessionLocked(WatchMode.FAMILY, nowMillis)
            }

            publishStateLocked(nowMillis, cal, mode, remaining, lockedOut, reason, paused)
        }
    }

    suspend fun onBootCompleted() {
        mutex.withLock {
            restoreOrEvaluateStateLocked(isBoot = true)
        }
    }

    private suspend fun restoreOrEvaluateStateLocked(isBoot: Boolean) {
        val nowMillis = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // Close any leftover ACTIVE sessions from before process kill / reboot
        watchDao.closeStaleActiveSessions(
            reason = if (isBoot) "BOOT_LOCKED" else "PAUSED",
            endedAt = nowMillis
        )
        activeSessionId = -1L
        prefs.activeSessionId = -1L
        activeSessionMode = null
        activeSessionDurationSeconds = 0

        if (isBoot) {
            prefs.isTvStandbyBlackout = false
            if (prefs.lockToKidsOnBoot) {
                prefs.activeMode = WatchMode.KIDS
                isParentUnlockedInApp = false
            }
        }

        val mode = prefs.activeMode
        var remaining = prefs.kidsRemainingSeconds
        var lockedOut = prefs.isKidsLockedOut
        var reason = prefs.lockReason

        if (mode == WatchMode.KIDS) {
            if (isCurfewHour(hour)) {
                lockedOut = true
                reason = LockReason.BEDTIME_CURFEW
                prefs.isKidsLockedOut = true
                prefs.lockReason = LockReason.BEDTIME_CURFEW
            } else if (remaining <= 0 || (lockedOut && reason == LockReason.ONE_HOUR_LIMIT)) {
                // Critical anti-reboot guarantee: NEVER reset 1h limit on reboot!
                lockedOut = true
                reason = LockReason.ONE_HOUR_LIMIT
                prefs.isKidsLockedOut = true
                prefs.lockReason = LockReason.ONE_HOUR_LIMIT
            } else if (lockedOut && reason == LockReason.BEDTIME_CURFEW) {
                lockedOut = false
                reason = LockReason.NONE
                prefs.isKidsLockedOut = false
                prefs.lockReason = LockReason.NONE
            }
        }

        publishStateLocked(
            nowMillis = nowMillis,
            cal = cal,
            mode = mode,
            remaining = remaining,
            lockedOut = lockedOut,
            reason = reason,
            paused = prefs.isTimerPaused
        )
    }

    suspend fun setupPin(pin: String): Boolean = mutex.withLock {
        if (!PinCryptoHelper.isValidPinFormat(pin)) return@withLock false
        val (hash, salt) = PinCryptoHelper.hashPin(pin)
        prefs.pinHashBase64 = hash
        prefs.pinSaltBase64 = salt
        prefs.failedPinAttempts = 0
        prefs.pinCooldownUntilMillis = 0L
        isParentUnlockedInApp = true
        refreshSnapshotLocked()
        true
    }

    suspend fun verifyPin(pin: String): PinVerifyResult = mutex.withLock {
        if (!prefs.isPinConfigured) return@withLock PinVerifyResult.NotConfigured
        val now = System.currentTimeMillis()
        val cooldownUntil = prefs.pinCooldownUntilMillis
        if (now < cooldownUntil) {
            val secLeft = ((cooldownUntil - now) / 1000L).toInt().coerceAtLeast(1)
            refreshSnapshotLocked()
            return@withLock PinVerifyResult.Cooldown(secLeft)
        }

        val ok = PinCryptoHelper.verifyPin(pin, prefs.pinHashBase64, prefs.pinSaltBase64)
        if (ok) {
            prefs.failedPinAttempts = 0
            prefs.pinCooldownUntilMillis = 0L
            isParentUnlockedInApp = true
            refreshSnapshotLocked()
            PinVerifyResult.Success
        } else {
            val attempts = prefs.failedPinAttempts + 1
            prefs.failedPinAttempts = attempts
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                prefs.pinCooldownUntilMillis = now + COOLDOWN_MILLIS
                prefs.failedPinAttempts = 0
                refreshSnapshotLocked()
                PinVerifyResult.Cooldown((COOLDOWN_MILLIS / 1000L).toInt())
            } else {
                refreshSnapshotLocked()
                PinVerifyResult.WrongPin(MAX_FAILED_ATTEMPTS - attempts)
            }
        }
    }

    suspend fun switchToFamilyMode() = mutex.withLock {
        val now = System.currentTimeMillis()
        closeActiveSessionLocked("SWITCHED_MODE", now)
        prefs.activeMode = WatchMode.FAMILY
        prefs.isTimerPaused = false
        prefs.isTvStandbyBlackout = false
        isParentUnlockedInApp = true
        refreshSnapshotLocked()
    }

    suspend fun switchToKidsMode() = mutex.withLock {
        val now = System.currentTimeMillis()
        closeActiveSessionLocked("SWITCHED_MODE", now)
        prefs.activeMode = WatchMode.KIDS
        prefs.isTimerPaused = false
        isParentUnlockedInApp = false
        restoreOrEvaluateStateLocked(isBoot = false)
    }

    suspend fun grantNewKidsOneHourSession() = mutex.withLock {
        val now = System.currentTimeMillis()
        closeActiveSessionLocked("SWITCHED_MODE", now)
        prefs.activeMode = WatchMode.KIDS
        prefs.kidsRemainingSeconds = GuardPreferences.DEFAULT_SESSION_LIMIT_SECONDS
        prefs.isKidsLockedOut = false
        prefs.lockReason = LockReason.NONE
        prefs.isTimerPaused = false
        prefs.isTvStandbyBlackout = false
        restoreOrEvaluateStateLocked(isBoot = false)
    }

    suspend fun turnOffTvRemotely(): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        closeActiveSessionLocked("REMOTE_POWER_OFF", now)
        prefs.activeMode = WatchMode.KIDS
        prefs.isTimerPaused = true
        prefs.isTvStandbyBlackout = true
        isParentUnlockedInApp = false
        val hwSleep = TvPowerController.turnOffTvOrStandby(context)
        refreshSnapshotLocked()
        hwSleep
    }

    suspend fun wakeTvFromStandby() = mutex.withLock {
        prefs.isTvStandbyBlackout = false
        prefs.isTimerPaused = false
        restoreOrEvaluateStateLocked(isBoot = false)
    }

    suspend fun toggleKidsTimerPaused() = mutex.withLock {
        if (prefs.activeMode != WatchMode.KIDS || prefs.isKidsLockedOut) return@withLock
        val newPaused = !prefs.isTimerPaused
        prefs.isTimerPaused = newPaused
        if (newPaused) {
            closeActiveSessionLocked("PAUSED", System.currentTimeMillis())
        }
        refreshSnapshotLocked()
    }

    suspend fun setShuffledKeypadEnabled(enabled: Boolean) = mutex.withLock {
        prefs.shuffledKeypadEnabled = enabled
        refreshSnapshotLocked()
    }

    suspend fun setLockToKidsOnBoot(enabled: Boolean) = mutex.withLock {
        prefs.lockToKidsOnBoot = enabled
        refreshSnapshotLocked()
    }

    suspend fun setTvServerEnabled(enabled: Boolean) = mutex.withLock {
        prefs.tvServerEnabled = enabled
        refreshSnapshotLocked()
    }

    fun saveLastRemoteTv(host: String, port: Int) {
        prefs.lastRemoteTvHost = host
        prefs.lastRemoteTvPort = port
        _guardState.value = _guardState.value.copy(
            lastRemoteTvHost = host.trim(),
            lastRemoteTvPort = port
        )
    }

    fun getPairingCode(): String = prefs.pairingCode

    suspend fun regeneratePairingCode() = mutex.withLock {
        prefs.regeneratePairingCode()
        refreshSnapshotLocked()
    }

    suspend fun lockParentPanel() = mutex.withLock {
        isParentUnlockedInApp = false
        refreshSnapshotLocked()
    }

    private suspend fun ensureAndIncrementSessionLocked(mode: WatchMode, nowMillis: Long) {
        if (activeSessionId <= 0L || activeSessionMode != mode) {
            if (activeSessionId > 0L) {
                closeActiveSessionLocked("SWITCHED_MODE", nowMillis)
            }
            val newSession = WatchSessionEntity(
                mode = mode.name,
                dateKey = formatDateKey(nowMillis),
                startedAtMillis = nowMillis,
                endedAtMillis = nowMillis,
                durationSeconds = 1,
                endReason = "ACTIVE"
            )
            val id = watchDao.insertSession(newSession)
            activeSessionId = id
            prefs.activeSessionId = id
            activeSessionMode = mode
            activeSessionStartMillis = nowMillis
            activeSessionDurationSeconds = 1
        } else {
            activeSessionDurationSeconds += 1
            if (activeSessionDurationSeconds % 10 == 0) {
                watchDao.getSessionById(activeSessionId)?.let { existing ->
                    watchDao.updateSession(
                        existing.copy(
                            endedAtMillis = nowMillis,
                            durationSeconds = activeSessionDurationSeconds
                        )
                    )
                }
            }
        }
    }

    private suspend fun closeActiveSessionLocked(reason: String, nowMillis: Long) {
        if (activeSessionId > 0L) {
            watchDao.getSessionById(activeSessionId)?.let { existing ->
                watchDao.updateSession(
                    existing.copy(
                        endedAtMillis = nowMillis,
                        durationSeconds = maxOf(existing.durationSeconds, activeSessionDurationSeconds),
                        endReason = reason
                    )
                )
            }
        }
        activeSessionId = -1L
        prefs.activeSessionId = -1L
        activeSessionMode = null
        activeSessionDurationSeconds = 0
    }

    private fun refreshSnapshotLocked() {
        val nowMillis = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        publishStateLocked(
            nowMillis = nowMillis,
            cal = cal,
            mode = prefs.activeMode,
            remaining = prefs.kidsRemainingSeconds,
            lockedOut = prefs.isKidsLockedOut,
            reason = prefs.lockReason,
            paused = prefs.isTimerPaused
        )
    }

    private fun publishStateLocked(
        nowMillis: Long,
        cal: Calendar,
        mode: WatchMode,
        remaining: Int,
        lockedOut: Boolean,
        reason: LockReason,
        paused: Boolean
    ) {
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val secondsUntilCurfew = computeSecondsUntilCurfew(cal)
        val effectiveRemaining = if (isCurfewHour(hour) || lockedOut) {
            0
        } else {
            minOf(remaining, secondsUntilCurfew)
        }
        val networkBlocked = mode == WatchMode.KIDS && (lockedOut || paused || isCurfewHour(hour))
        val vpnGranted = VpnService.prepare(context) == null
        val cooldownLeft = ((prefs.pinCooldownUntilMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)

        _guardState.value = GuardState(
            activeMode = mode,
            isPinConfigured = prefs.isPinConfigured,
            kidsRemainingSeconds = remaining,
            sessionLimitSeconds = GuardPreferences.DEFAULT_SESSION_LIMIT_SECONDS,
            isKidsLockedOut = lockedOut,
            lockReason = reason,
            isTimerPaused = paused,
            isNetworkBlocked = networkBlocked,
            isVpnPermissionGranted = vpnGranted,
            curfewStartHour = GuardPreferences.CURFEW_START_HOUR,
            curfewEndHour = GuardPreferences.CURFEW_END_HOUR,
            shuffledKeypadEnabled = prefs.shuffledKeypadEnabled,
            lockToKidsOnBoot = prefs.lockToKidsOnBoot,
            currentHour24 = hour,
            currentClockFormatted = formatClock(nowMillis),
            secondsUntilCurfew = secondsUntilCurfew,
            effectiveRemainingSeconds = effectiveRemaining,
            activeSessionDurationSeconds = activeSessionDurationSeconds,
            isParentUnlockedInApp = isParentUnlockedInApp,
            pinCooldownRemainingSeconds = cooldownLeft,
            isTvDevice = prefs.isTvDevice,
            tvServerEnabled = prefs.tvServerEnabled,
            tvLocalIp = com.example.remote.TvRemoteServer.getLocalIpv4Address(),
            tvServerPort = activeServerPort,
            pairingCode = prefs.pairingCode,
            isDeviceAdminActive = TvPowerController.isDeviceAdminActive(context),
            isTvStandbyBlackout = prefs.isTvStandbyBlackout,
            lastRemoteTvHost = prefs.lastRemoteTvHost,
            lastRemoteTvPort = prefs.lastRemoteTvPort
        )
    }

    private fun buildInitialGuardState(): GuardState {
        val nowMillis = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val mode = prefs.activeMode
        val remaining = prefs.kidsRemainingSeconds
        val curfewNow = isCurfewHour(hour)
        val locked = prefs.isKidsLockedOut || (mode == WatchMode.KIDS && (curfewNow || remaining <= 0))
        val reason = when {
            mode == WatchMode.KIDS && curfewNow -> LockReason.BEDTIME_CURFEW
            mode == WatchMode.KIDS && remaining <= 0 -> LockReason.ONE_HOUR_LIMIT
            else -> prefs.lockReason
        }
        val networkBlocked = mode == WatchMode.KIDS && (locked || prefs.isTimerPaused || curfewNow)
        return GuardState(
            activeMode = mode,
            isPinConfigured = prefs.isPinConfigured,
            kidsRemainingSeconds = remaining,
            isKidsLockedOut = locked,
            lockReason = reason,
            isTimerPaused = prefs.isTimerPaused,
            isNetworkBlocked = networkBlocked,
            isVpnPermissionGranted = VpnService.prepare(context) == null,
            shuffledKeypadEnabled = prefs.shuffledKeypadEnabled,
            lockToKidsOnBoot = prefs.lockToKidsOnBoot,
            currentHour24 = hour,
            currentClockFormatted = formatClock(nowMillis),
            secondsUntilCurfew = computeSecondsUntilCurfew(cal),
            effectiveRemainingSeconds = if (locked) 0 else minOf(remaining, computeSecondsUntilCurfew(cal)),
            isTvDevice = prefs.isTvDevice,
            tvServerEnabled = prefs.tvServerEnabled,
            tvLocalIp = com.example.remote.TvRemoteServer.getLocalIpv4Address(),
            tvServerPort = activeServerPort,
            pairingCode = prefs.pairingCode,
            isDeviceAdminActive = TvPowerController.isDeviceAdminActive(context),
            isTvStandbyBlackout = prefs.isTvStandbyBlackout,
            lastRemoteTvHost = prefs.lastRemoteTvHost,
            lastRemoteTvPort = prefs.lastRemoteTvPort
        )
    }

    private fun buildWeeklyReport(sessions: List<WatchSessionEntity>): WeeklyReportUiState {
        val byDate = sessions.groupBy { it.dateKey }
        val days = (-6..0).map { offset ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dateKey = formatDateKey(cal.timeInMillis)
            val daySessions = byDate[dateKey].orEmpty()
            val kidsSec = daySessions.filter { it.mode == WatchMode.KIDS.name }.sumOf { it.durationSeconds }
            val familySec = daySessions.filter { it.mode == WatchMode.FAMILY.name }.sumOf { it.durationSeconds }
            val blocks = daySessions.count { it.endReason == "LIMIT_1H" || it.endReason == "CURFEW_21H" }

            DailyUsagePoint(
                dateKey = dateKey,
                dayLabel = vietnameseShortDay(cal.get(Calendar.DAY_OF_WEEK), offset == 0),
                fullDateLabel = formatShortDate(cal.timeInMillis),
                kidsMinutes = (kidsSec + 59) / 60, // round up partial minute if > 0, 0 -> 0
                familyMinutes = (familySec + 59) / 60,
                blockEventsCount = blocks,
                sessions = daySessions
            )
        }

        val totalKidsSec = sessions.filter { it.mode == WatchMode.KIDS.name }.sumOf { it.durationSeconds }
        val totalFamilySec = sessions.filter { it.mode == WatchMode.FAMILY.name }.sumOf { it.durationSeconds }
        val totalBlocks = sessions.count { it.endReason == "LIMIT_1H" || it.endReason == "CURFEW_21H" }
        val activeDays = days.count { it.kidsMinutes > 0 }.coerceAtLeast(1)
        val avgKidsMin = ((totalKidsSec / 60.0) / activeDays).toInt()

        return WeeklyReportUiState(
            days = days,
            totalKidsSeconds = totalKidsSec,
            totalFamilySeconds = totalFamilySec,
            totalBlockEvents = totalBlocks,
            avgDailyKidsMinutes = if (totalKidsSec == 0) 0 else avgKidsMin,
            recentSessions = sessions.take(25)
        )
    }

    private fun isCurfewHour(hour24: Int): Boolean {
        return hour24 >= GuardPreferences.CURFEW_START_HOUR || hour24 < GuardPreferences.CURFEW_END_HOUR
    }

    private fun computeSecondsUntilCurfew(cal: Calendar): Int {
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (isCurfewHour(hour)) return 0
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val secondsNow = hour * 3600 + minute * 60 + second
        val curfewSec = GuardPreferences.CURFEW_START_HOUR * 3600
        return (curfewSec - secondsNow).coerceAtLeast(0)
    }

    private fun startOfDayMillisOffset(offsetDays: Int): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offsetDays)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun vietnameseShortDay(dayOfWeek: Int, isToday: Boolean): String {
        if (isToday) return "H.nay"
        return when (dayOfWeek) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            Calendar.SUNDAY -> "CN"
            else -> ""
        }
    }

    private fun formatDateKey(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
    }

    private fun formatClock(millis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    private fun formatShortDate(millis: Long): String {
        return SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(millis))
    }

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val COOLDOWN_MILLIS = 30_000L
    }
}
