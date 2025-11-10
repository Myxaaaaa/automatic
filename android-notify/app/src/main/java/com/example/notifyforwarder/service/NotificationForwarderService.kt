package com.example.notifyforwarder.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.notifyforwarder.prefs.AppPreferences
import com.example.notifyforwarder.net.HttpClient
import com.example.notifyforwarder.data.AppDatabase
import com.example.notifyforwarder.data.NotificationLog
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import java.util.UUID

class NotificationForwarderService : NotificationListenerService() {

	override fun onListenerConnected() {
		Log.i(TAG, "Notification listener connected")
	}

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		val selected = AppPreferences.getSelectedPackages(this)
		if (selected.isNotEmpty() && !selected.contains(sbn.packageName)) {
			return
		}
		val extras = sbn.notification.extras
		val title = extras.getCharSequence("android.title")?.toString().orEmpty()
		val text = extras.getCharSequence("android.text")?.toString().orEmpty()
		forward(sbn.packageName, title, text, sbn.postTime)
	}

	private fun forward(pkg: String, title: String, text: String, postedAtMs: Long) {
		try {
			val amount = extractAmount(text).orElse(null)
			val deviceId = ensureDeviceId(this@NotificationForwarderService)
			
			val body = JSONObject().apply {
				put("package", pkg)
				put("title", title)
				put("text", text)
				put("postedAt", iso8601(postedAtMs))
				if (amount != null) {
					put("amount", amount)
					Log.d(TAG, "Extracted amount: $amount from: $text")
				} else {
					Log.d(TAG, "No amount found in: $text")
				}
				put("deviceId", deviceId)
				// Account label будет отправлен только если он был установлен через QR
				AppPreferences.getAccountLabel(this@NotificationForwarderService)?.let {
					put("account", it)
				}
			}.toString()
			
			val url = AppPreferences.getEndpointUrl(this)
			Log.i(TAG, "Forwarding notification to $url")
			Log.d(TAG, "Body: $body")
			
			// Save log and forward
			val database = AppDatabase.getDatabase(this)
			Thread {
				try {
					val result = HttpClient.postJson(url, body, this)
					Log.d(TAG, "POST result: success=${result.success}, error=${result.errorMessage}")
					CoroutineScope(Dispatchers.IO).launch {
						try {
							val log = NotificationLog(
								packageName = pkg,
								title = title,
								text = text,
								amount = amount,
								postedAt = postedAtMs,
								success = result.success,
								errorMessage = result.errorMessage
							)
							database.notificationLogDao().insert(log)
						} catch (e: Exception) {
							Log.e(TAG, "Error saving log", e)
						}
					}
				} catch (e: Exception) {
					Log.e(TAG, "Error forwarding notification", e)
					CoroutineScope(Dispatchers.IO).launch {
						try {
							val log = NotificationLog(
								packageName = pkg,
								title = title,
								text = text,
								amount = amount,
								postedAt = postedAtMs,
								success = false,
								errorMessage = e.message
							)
							database.notificationLogDao().insert(log)
						} catch (dbError: Exception) {
							Log.e(TAG, "Error saving error log", dbError)
						}
					}
				}
			}.start()
		} catch (e: Exception) {
			Log.e(TAG, "Error in forward()", e)
		}
	}

	private fun extractAmount(text: String): java.util.Optional<String> {
		try {
			// Улучшенный паттерн: поддерживает разные форматы сумм
			// Примеры: "10 сом", "10,00", "10.50", "1 234,56", "1234.56", "10", "10 ₽", "Поступление 10 сом"
			
			// Сначала пытаемся найти числа с десятичными знаками
			val decimalPatterns = listOf(
				// "1 234,56" или "1234,56"
				Pattern.compile("([\\d]{1,3}(?:\\s[\\d]{3})*,[\\d]{1,2})"),
				// "1234.56"
				Pattern.compile("([\\d]{1,3}(?:\\s[\\d]{3})*\\.[\\d]{1,2})"),
				// "10,00" или "10.00"
				Pattern.compile("([\\d]+[,.][\\d]{1,2})")
			)
			
			for (pattern in decimalPatterns) {
				val matcher = pattern.matcher(text)
				if (matcher.find()) {
					val raw = matcher.group(1) ?: continue
					val cleaned = raw.replace("\\s+".toRegex(), "").replace(" ", "")
					if (cleaned.any { it.isDigit() }) {
						val normalized = cleaned.replace(".", ",")
						Log.d(TAG, "Extracted amount (decimal): $normalized from text: $text")
						return java.util.Optional.of(normalized)
					}
				}
			}
			
			// Если не нашли с десятичными, ищем целые числа
			// Ищем числа, которые могут быть суммами (обычно от 1 до 7 цифр)
			val wholeNumberPatterns = listOf(
				// Числа с пробелами: "1 234" или "10 000"
				Pattern.compile("([\\d]{1,3}(?:\\s[\\d]{3})+)"),
				// Просто число от 1 до 7 цифр (чтобы не ловить номера телефонов и т.д.)
				Pattern.compile("\\b([\\d]{1,7})\\b")
			)
			
			for (pattern in wholeNumberPatterns) {
				val matcher = pattern.matcher(text)
				val matches = mutableListOf<Pair<String, Long>>()
				while (matcher.find()) {
					val raw = matcher.group(1) ?: continue
					val cleaned = raw.replace("\\s+".toRegex(), "").replace(" ", "")
					if (cleaned.any { it.isDigit() } && cleaned.length <= 7) {
						val numValue = cleaned.toLongOrNull()
						if (numValue != null && numValue > 0 && numValue <= 9999999) {
							matches.add(Pair(cleaned, numValue))
						}
					}
				}
				
				// Берем самое большое число (скорее всего это сумма)
				if (matches.isNotEmpty()) {
					val amount = matches.maxByOrNull { it.second }?.first ?: matches.first().first
					Log.d(TAG, "Extracted amount (whole): $amount from text: $text")
					return java.util.Optional.of(amount)
				}
			}
			
			Log.d(TAG, "No amount found in text: $text")
		} catch (e: Exception) {
			Log.e(TAG, "Error extracting amount from: $text", e)
		}
		return java.util.Optional.empty()
	}

	companion object {
		private const val TAG = "NotifForwarderService"

		fun hasNotificationAccess(context: Context): Boolean {
			val cn = ComponentName(context, NotificationForwarderService::class.java)
			val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
			return flat?.contains(cn.flattenToString()) == true
		}

		fun enqueueTest(context: Context) {
			ensureTestChannel(context)
			val nm = NotificationManagerCompat.from(context)
			val notif = NotificationCompat.Builder(context, "test")
				.setSmallIcon(android.R.drawable.ic_dialog_info)
				.setContentTitle("Test payment")
				.setContentText("Заказ оплачен 1 234,56 ₽")
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.build()
			// Post a local notification (Note: local notifications from this app may be filtered out by selection)
			nm.notify(1001, notif)
			// Также отправим тест напрямую, чтобы не зависеть от фильтрации приложений
			val database = AppDatabase.getDatabase(context)
			Thread {
				val postedAt = System.currentTimeMillis()
				val body = JSONObject().apply {
					put("package", "com.example.notifyforwarder")
					put("title", "Test payment")
					put("text", "Заказ оплачен 1 234,56 ₽")
					put("postedAt", iso8601(postedAt))
					put("amount", "1234,56")
					put("deviceId", ensureDeviceId(context))
					AppPreferences.getAccountLabel(context)?.let { put("account", it) }
				}.toString()
				val url = AppPreferences.getEndpointUrl(context)
				val result = HttpClient.postJson(url, body, context)
				CoroutineScope(Dispatchers.IO).launch {
					val log = NotificationLog(
						packageName = "com.example.notifyforwarder",
						title = "Test payment",
						text = "Заказ оплачен 1 234,56 ₽",
						amount = "1234,56",
						postedAt = postedAt,
						success = result.success,
						errorMessage = result.errorMessage
					)
					database.notificationLogDao().insert(log)
				}
			}.start()
		}

		private fun iso8601(ms: Long): String {
			return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(ms)
		}

		private fun ensureTestChannel(context: Context) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				val channel = NotificationChannel("test", "Test", NotificationManager.IMPORTANCE_DEFAULT)
				val nm = context.getSystemService(NotificationManager::class.java)
				nm.createNotificationChannel(channel)
			}
		}

		private fun ensureDeviceId(context: Context): String {
			AppPreferences.getDeviceId(context)?.let { return it }
			val generated = UUID.randomUUID().toString()
			AppPreferences.setDeviceId(context, generated)
			return generated
		}
	}
}



