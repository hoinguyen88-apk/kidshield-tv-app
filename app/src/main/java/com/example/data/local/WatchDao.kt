package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_sessions WHERE startedAtMillis >= :sinceMillis ORDER BY startedAtMillis DESC")
    fun observeSessionsSince(sinceMillis: Long): Flow<List<WatchSessionEntity>>

    @Query("SELECT * FROM watch_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): WatchSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WatchSessionEntity): Long

    @Update
    suspend fun updateSession(session: WatchSessionEntity)

    @Query("UPDATE watch_sessions SET endReason = :reason, endedAtMillis = :endedAt WHERE endReason = 'ACTIVE' AND id != :excludeId")
    suspend fun closeStaleActiveSessions(reason: String, endedAt: Long, excludeId: Long = -1L)

    @Query("DELETE FROM watch_sessions")
    suspend fun deleteAll()
}
