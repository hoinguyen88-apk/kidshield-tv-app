package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.example.data.model.LockReason
import com.example.data.model.WatchMode

class GuardPreferences(context: Context) {

    private val storageContext: Context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val deviceCtx = context.createDeviceProtectedStorageContext()
        runCatching { deviceCtx.moveSharedPreferencesFrom(context, PREFS_NAME) }
        deviceCtx
    } else {
        context.applicationContext
    }

    private val prefs: SharedPreferences =
        storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var activeMode: WatchMode
        get() = runCatching {
            WatchMode.valueOf(prefs.getString(KEY_ACTIVE_MODE, WatchMode.KIDS.name) ?: WatchMode.KIDS.name)
        }.getOrDefault(WatchMode.KIDS)
        set(value) {
            prefs.edit().putString(KEY_ACTIVE_MODE, value.name).commit()
        }

    var kidsRemainingSeconds: Int
        get() = prefs.getInt(KEY_KIDS_REMAINING_SECONDS, DEFAULT_SESSION_LIMIT_SECONDS)
        set(value) {
            prefs.edit().putInt(KEY_KIDS_REMAINING_SECONDS, value.coerceAtLeast(0)).commit()
        }

    var isKidsLockedOut: Boolean
        get() = prefs.getBoolean(KEY_IS_KIDS_LOCKED_OUT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_KIDS_LOCKED_OUT, value).commit()
        }

    var lockReason: LockReason
        get() = runCatching {
            LockReason.valueOf(prefs.getString(KEY_LOCK_REASON, LockReason.NONE.name) ?: LockReason.NONE.name)
        }.getOrDefault(LockReason.NONE)
        set(value) {
            prefs.edit().putString(KEY_LOCK_REASON, value.name).commit()
        }

    var isTimerPaused: Boolean
        get() = prefs.getBoolean(KEY_IS_TIMER_PAUSED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_TIMER_PAUSED, value).commit()
        }

    var shuffledKeypadEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLED_KEYPAD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHUFFLED_KEYPAD, value).commit()
        }

    var lockToKidsOnBoot: Boolean
        get() = prefs.getBoolean(KEY_LOCK_TO_KIDS_ON_BOOT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LOCK_TO_KIDS_ON_BOOT, value).commit()
        }

    var pinHashBase64: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) {
            prefs.edit().putString(KEY_PIN_HASH, value).commit()
        }

    var pinSaltBase64: String?
        get() = prefs.getString(KEY_PIN_SALT, null)
        set(value) {
            prefs.edit().putString(KEY_PIN_SALT, value).commit()
        }

    val isPinConfigured: Boolean
        get() = !pinHashBase64.isNullOrBlank() && !pinSaltBase64.isNullOrBlank()

    var activeSessionId: Long
        get() = prefs.getLong(KEY_ACTIVE_SESSION_ID, -1L)
        set(value) {
            prefs.edit().putLong(KEY_ACTIVE_SESSION_ID, value).commit()
        }

    var failedPinAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        set(value) {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, value).commit()
        }

    var pinCooldownUntilMillis: Long
        get() = prefs.getLong(KEY_PIN_COOLDOWN_UNTIL, 0L)
        set(value) {
            prefs.edit().putLong(KEY_PIN_COOLDOWN_UNTIL, value).commit()
        }

    var tvServerEnabled: Boolean
        get() = prefs.getBoolean(KEY_TV_SERVER_ENABLED, isTvDevice)
        set(value) {
            prefs.edit().putBoolean(KEY_TV_SERVER_ENABLED, value).commit()
        }

    var lastRemoteTvHost: String
        get() = prefs.getString(KEY_LAST_REMOTE_HOST, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_REMOTE_HOST, value.trim()).commit()
        }

    var lastRemoteTvPort: Int
        get() = prefs.getInt(KEY_LAST_REMOTE_PORT, 8745)
        set(value) {
            prefs.edit().putInt(KEY_LAST_REMOTE_PORT, value).commit()
        }

    var pairingCode: String
        get() {
            val existing = prefs.getString(KEY_PAIRING_CODE, null)
            if (!existing.isNullOrBlank() && existing.length == 6) return existing
            val generated = generatePairingCode()
            prefs.edit().putString(KEY_PAIRING_CODE, generated).commit()
            return generated
        }
        set(value) {
            prefs.edit().putString(KEY_PAIRING_CODE, value).commit()
        }

    fun regeneratePairingCode(): String {
        val fresh = generatePairingCode()
        pairingCode = fresh
        return fresh
    }

    var isTvStandbyBlackout: Boolean
        get() = prefs.getBoolean(KEY_TV_STANDBY_BLACKOUT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TV_STANDBY_BLACKOUT, value).commit()
        }

    val isTvDevice: Boolean = detectTvDevice(storageContext)

    companion object {
        private const val PREFS_NAME = "kidshield_guard_prefs"
        const val DEFAULT_SESSION_LIMIT_SECONDS = 3600
        const val CURFEW_START_HOUR = 21
        const val CURFEW_END_HOUR = 6

        private const val KEY_ACTIVE_MODE = "active_mode"
        private const val KEY_KIDS_REMAINING_SECONDS = "kids_remaining_seconds"
        private const val KEY_IS_KIDS_LOCKED_OUT = "is_kids_locked_out"
        private const val KEY_LOCK_REASON = "lock_reason"
        private const val KEY_IS_TIMER_PAUSED = "is_timer_paused"
        private const val KEY_SHUFFLED_KEYPAD = "shuffled_keypad"
        private const val KEY_LOCK_TO_KIDS_ON_BOOT = "lock_to_kids_on_boot"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_PIN_COOLDOWN_UNTIL = "pin_cooldown_until"
        private const val KEY_TV_SERVER_ENABLED = "tv_server_enabled"
        private const val KEY_LAST_REMOTE_HOST = "last_remote_host"
        private const val KEY_LAST_REMOTE_PORT = "last_remote_port"
        private const val KEY_PAIRING_CODE = "tv_pairing_code"
        private const val KEY_TV_STANDBY_BLACKOUT = "tv_standby_blackout"

        private fun generatePairingCode(): String {
            val num = java.security.SecureRandom().nextInt(900_000) + 100_000
            return num.toString()
        }

        private fun detectTvDevice(context: Context): Boolean {
            val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
            return uiMode?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        }
    }
}
