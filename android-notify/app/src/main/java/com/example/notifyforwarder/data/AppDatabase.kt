package com.example.notifyforwarder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NotificationLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
	abstract fun notificationLogDao(): NotificationLogDao

	companion object {
		@Volatile
		private var INSTANCE: AppDatabase? = null

		fun getDatabase(context: Context): AppDatabase {
			return INSTANCE ?: synchronized(this) {
				val instance = Room.databaseBuilder(
					context.applicationContext,
					AppDatabase::class.java,
					"notify_forwarder_db"
				)
				.fallbackToDestructiveMigration() // Для разработки - позволяет пересоздавать БД при изменении схемы
				.build()
				INSTANCE = instance
				instance
			}
		}
	}
}

