package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.KidShieldApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.GuardState
import com.example.data.model.LockReason
import com.example.data.model.WatchMode
import com.example.remote.TvRemoteServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatchGuardForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private var reportWarmJob: Job? = null
    private var lastBlockedState: Boolean? = null
    private var tvRemoteServer: TvRemoteServer? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = (application as KidShieldApp).repository
        startForeground(NOTIF_ID, buildNotification(repo.guardState.value))

        if (tvRemoteServer == null) {
            tvRemoteServer = TvRemoteServer(this, repo)
        }

        if (reportWarmJob == null || reportWarmJob?.isActive != true) {
            reportWarmJob = scope.launch {
                repo.observeWeeklyReport().collect { }
            }
        }

        if (tickerJob == null || tickerJob?.isActive != true) {
            tickerJob = scope.launch {
                var tickCount = 0
                while (isActive) {
                    repo.onTick()
                    val state = repo.guardState.value
                    syncVpnBlockState(state)
                    syncRemoteServerState(state, repo)
                    if (tickCount % 5 == 0 || lastBlockedState != state.isNetworkBlocked) {
                        updateNotification(state)
                    }
                    tickCount += 1
                    delay(1000L)
                }
            }
        }
        return START_STICKY
    }

    private fun syncRemoteServerState(state: GuardState, repo: com.example.data.repository.WatchGuardRepository) {
        val server = tvRemoteServer ?: return
        if (state.tvServerEnabled && !server.isRunning) {
            server.start()
            repo.updateServerPort(server.listeningPort)
        } else if (!state.tvServerEnabled && server.isRunning) {
            server.stop()
        }
    }

    private fun syncVpnBlockState(state: GuardState) {
        val shouldBlock = state.isNetworkBlocked
        if (lastBlockedState != shouldBlock || shouldBlock) {
            if (shouldBlock && state.isVpnPermissionGranted) {
                KidShieldVpnService.startBlock(this)
            } else if (!shouldBlock && lastBlockedState == true) {
                KidShieldVpnService.stopBlock(this)
            }
        }
        lastBlockedState = shouldBlock
    }

    private fun updateNotification(state: GuardState) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(state))
    }

    private fun buildNotification(state: GuardState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (title, body) = when {
            state.activeMode == WatchMode.FAMILY -> {
                getString(R.string.mode_family) to getString(R.string.family_rule_summary)
            }
            state.lockReason == LockReason.BEDTIME_CURFEW -> {
                getString(R.string.lock_curfew_title) to getString(R.string.network_status_blocked)
            }
            state.isKidsLockedOut -> {
                getString(R.string.lock_1h_title) to getString(R.string.network_status_blocked)
            }
            state.isTimerPaused -> {
                getString(R.string.mode_kids) to getString(R.string.network_status_blocked)
            }
            else -> {
                val mins = state.effectiveRemainingSeconds / 60
                val secs = state.effectiveRemainingSeconds % 60
                val formatted = "%02d:%02d".format(mins, secs)
                "${getString(R.string.mode_kids)} • Còn $formatted" to getString(R.string.kids_rule_summary)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.notif_channel_desc)
                    }
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tvRemoteServer?.stop()
        reportWarmJob?.cancel()
        tickerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "kidshield_guard_channel"
        private const val NOTIF_ID = 4201

        fun ensureRunning(context: Context) {
            val intent = Intent(context, WatchGuardForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
