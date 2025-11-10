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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

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
		val amount = extractAmount(text).orElse(null)
		val body = JSONObject().apply {
			put("package", pkg)
			put("title", title)
			put("text", text)
			put("postedAt", iso8601(postedAtMs))
			if (amount != null) put("amount", amount)
		}.toString()
		val url = AppPreferences.getEndpointUrl(this)
		Log.d(TAG, "Forwarding to $url: $body")
		Thread {
			HttpClient.postJson(url, body)
		}.start()
	}

	companion object {
		private const val TAG = "NotifForwarderService"

		fun hasNotificationAccess(context: Context): Boolean {
			val cn = ComponentName(context, NotificationForwarderService::class.java)
			val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
			return flat?.contains(cn.flattenToString()) == true
		}

		fun enqueueTest(context: Context) {
			val nm = NotificationManagerCompat.from(context)
			val notif = NotificationCompat.Builder(context, "test")
				.setSmallIcon(android.R.drawable.ic_dialog_info)
				.setContentTitle("Test payment")
				.setContentText("Заказ оплачен 1 234,56 ₽")
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.build()
			// Post a local notification (Note: local notifications from this app may be filtered out by selection)
			nm.notify(1001, notif)
		}

		private fun iso8601(ms: Long): String {
			return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(ms)
		}
	}
}

private fun extractAmount(text: String): java.util.Optional<String> {
	val matcher = AMOUNT_PATTERN.matcher(text)
	while (matcher.find()) {
		val raw = matcher.group(1) ?: continue
		val cleaned = raw.replace("\\s+".toRegex(), "")
		if (cleaned.any { it.isDigit() }) {
			return java.util.Optional.of(cleaned)
		}
	}
	return java.util.Optional.empty()
}

private val AMOUNT_PATTERN: Pattern =
	Pattern.compile("([\\d\\s]+(?:[\\.,]\\d{2})?)")



