package com.example.notifyforwarder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_logs")
data class NotificationLog(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val packageName: String,
	val title: String,
	val text: String,
	val amount: String?,
	val postedAt: Long,
	val forwardedAt: Long = System.currentTimeMillis(),
	val success: Boolean = true,
	val errorMessage: String? = null
)

