package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_sessions")
data class WatchSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mode: String, // "KIDS" or "FAMILY"
    val dateKey: String, // "YYYY-MM-DD"
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSeconds: Int,
    val endReason: String // "ACTIVE", "LIMIT_1H", "CURFEW_21H", "SWITCHED_MODE", "PAUSED", "BOOT_LOCKED"
)
