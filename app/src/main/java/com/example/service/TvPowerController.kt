package com.example.service

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

object TvPowerController {

    fun adminComponentName(context: Context): ComponentName {
        return ComponentName(context, KidShieldDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return runCatching { dpm.isAdminActive(adminComponentName(context)) }.getOrDefault(false)
    }

    fun buildEnableDeviceAdminIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Cấp quyền Quản trị thiết bị để phụ huynh có thể tắt màn hình / đưa Xiaomi Google TV về chế độ ngủ ngay từ điện thoại."
            )
        }
    }

    /**
     * Stops active TV audio playback and, if Device Admin is enabled, puts the TV into sleep/standby.
     * Returns true if hardware screen lock/standby via DevicePolicyManager.lockNow() executed.
     */
    fun turnOffTvOrStandby(context: Context): Boolean {
        stopActiveMediaAudio(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (dpm != null && isDeviceAdminActive(context)) {
            val locked = runCatching {
                dpm.lockNow()
                true
            }.getOrDefault(false)
            if (locked) return true
        }
        return false
    }

    private fun stopActiveMediaAudio(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }
}
