package com.example.notifyforwarder.data

import kotlinx.coroutines.flow.Flow

class LogRepository(private val logDao: NotificationLogDao) {
	fun getRecentLogs(limit: Int = 100): Flow<List<NotificationLog>> = logDao.getRecentLogs(limit)
	fun getAllLogs(): Flow<List<NotificationLog>> = logDao.getAllLogs()
	fun getSuccessCount(): Flow<Int> = logDao.getSuccessCount()
	fun getErrorCount(): Flow<Int> = logDao.getErrorCount()

	suspend fun insertLog(log: NotificationLog): Long = logDao.insert(log)
	suspend fun deleteOldLogs(keepCount: Int = 1000) = logDao.deleteOldLogs(keepCount)
	suspend fun clearAll() = logDao.clearAll()
}

