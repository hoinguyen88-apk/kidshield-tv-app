package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class KidShieldVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var drainJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_BLOCK -> {
                stopVpnBlocking()
                return START_NOT_STICKY
            }
            ACTION_START_BLOCK, null -> {
                startForeground(VPN_NOTIF_ID, buildBlockedNotification())
                if (prepare(this) == null) {
                    startVpnBlocking()
                } else {
                    stopSelf()
                }
                return START_STICKY
            }
        }
        return START_STICKY
    }

    private fun startVpnBlocking() {
        if (vpnInterface != null) return
        try {
            val builder = Builder()
                .setSession("KidShield TV Network Lock")
                .setMtu(1500)
                .addAddress("10.88.0.1", 32)
                .addAddress("fd00:88::1", 128)
                .addRoute("::", 0)
                .addDnsServer("10.88.0.2")
                .setBlocking(true)

            // Keep KidShield TV app reachable on home Wi-Fi LAN so parents can still unlock via phone
            runCatching { builder.addDisallowedApplication(packageName) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.addRoute("0.0.0.0", 0)
                listOf(
                    "10.0.0.0" to 8,
                    "127.0.0.0" to 8,
                    "169.254.0.0" to 16,
                    "172.16.0.0" to 12,
                    "192.168.0.0" to 16,
                    "224.0.0.0" to 4,
                    "240.0.0.0" to 4
                ).forEach { (addr, prefix) ->
                    runCatching {
                        builder.excludeRoute(
                            android.net.IpPrefix(java.net.InetAddress.getByName(addr), prefix)
                        )
                    }
                }
            } else {
                // Public IPv4 routes excluding RFC1918 private LAN & multicast
                PUBLIC_IPV4_ROUTES.forEach { (addr, prefix) ->
                    builder.addRoute(addr, prefix)
                }
            }

            val pfd = builder.establish() ?: return
            vpnInterface = pfd
            drainJob?.cancel()
            drainJob = scope.launch {
                val input = FileInputStream(pfd.fileDescriptor)
                val buffer = ByteBuffer.allocate(32767)
                try {
                    while (isActive && vpnInterface != null) {
                        val read = input.read(buffer.array())
                        if (read < 0) break
                        buffer.clear()
                    }
                } catch (_: Exception) {
                    // Interface closed when stopping VPN block
                }
            }
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun stopVpnBlocking() {
        drainJob?.cancel()
        drainJob = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onRevoke() {
        stopVpnBlocking()
        super.onRevoke()
    }

    override fun onDestroy() {
        drainJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        scope.cancel()
        super.onDestroy()
    }

    private fun buildBlockedNotification(): Notification {
        ensureChannel()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.network_status_blocked))
            .setContentText(getString(R.string.lock_1h_subtitle))
            .setOngoing(true)
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

    companion object {
        const val ACTION_START_BLOCK = "com.example.action.START_VPN_BLOCK"
        const val ACTION_STOP_BLOCK = "com.example.action.STOP_VPN_BLOCK"
        private const val CHANNEL_ID = "kidshield_vpn_channel"
        private const val VPN_NOTIF_ID = 4202

        private val PUBLIC_IPV4_ROUTES = listOf(
            "1.0.0.0" to 8,
            "2.0.0.0" to 7,
            "4.0.0.0" to 6,
            "8.0.0.0" to 7,
            "11.0.0.0" to 8,
            "12.0.0.0" to 6,
            "16.0.0.0" to 4,
            "32.0.0.0" to 3,
            "64.0.0.0" to 3,
            "96.0.0.0" to 4,
            "112.0.0.0" to 5,
            "120.0.0.0" to 6,
            "124.0.0.0" to 7,
            "126.0.0.0" to 8,
            "128.0.0.0" to 3,
            "160.0.0.0" to 5,
            "168.0.0.0" to 8,
            "169.0.0.0" to 9,
            "169.128.0.0" to 10,
            "169.192.0.0" to 11,
            "169.224.0.0" to 12,
            "169.240.0.0" to 13,
            "169.248.0.0" to 14,
            "169.252.0.0" to 15,
            "169.255.0.0" to 16,
            "170.0.0.0" to 7,
            "172.0.0.0" to 12,
            "172.32.0.0" to 11,
            "172.64.0.0" to 10,
            "172.128.0.0" to 9,
            "173.0.0.0" to 8,
            "174.0.0.0" to 7,
            "176.0.0.0" to 4,
            "192.0.0.0" to 9,
            "192.128.0.0" to 11,
            "192.160.0.0" to 13,
            "192.169.0.0" to 16,
            "192.170.0.0" to 15,
            "192.172.0.0" to 14,
            "192.176.0.0" to 12,
            "192.192.0.0" to 10,
            "193.0.0.0" to 8,
            "194.0.0.0" to 7,
            "196.0.0.0" to 6,
            "200.0.0.0" to 5,
            "208.0.0.0" to 4
        )

        fun startBlock(context: Context) {
            if (prepare(context) != null) return
            val intent = Intent(context, KidShieldVpnService::class.java).apply {
                action = ACTION_START_BLOCK
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopBlock(context: Context) {
            val intent = Intent(context, KidShieldVpnService::class.java).apply {
                action = ACTION_STOP_BLOCK
            }
            context.startService(intent)
        }
    }
}
