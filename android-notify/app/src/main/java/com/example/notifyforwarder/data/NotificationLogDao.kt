package com.example.notifyforwarder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {
	@Query("SELECT * FROM notification_logs ORDER BY postedAt DESC LIMIT :limit")
	fun getRecentLogs(limit: Int = 100): Flow<List<NotificationLog>>

	@Query("SELECT * FROM notification_logs ORDER BY postedAt DESC")
	fun getAllLogs(): Flow<List<NotificationLog>>

	@Query("SELECT COUNT(*) FROM notification_logs WHERE success = 1")
	fun getSuccessCount(): Flow<Int>

	@Query("SELECT COUNT(*) FROM notification_logs WHERE success = 0")
	fun getErrorCount(): Flow<Int>

	@Insert
	suspend fun insert(log: NotificationLog): Long

	@Query("DELETE FROM notification_logs WHERE id < (SELECT id FROM notification_logs ORDER BY postedAt DESC LIMIT 1 OFFSET :keepCount)")
	suspend fun deleteOldLogs(keepCount: Int = 1000)

	@Query("DELETE FROM notification_logs")
	suspend fun clearAll()
}

