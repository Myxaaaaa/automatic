package com.example.notifyforwarder.prefs

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

object AppPreferences {
	private const val PREFS = "notify_forwarder_prefs"
	private const val KEY_SELECTED_PACKAGES = "selected_packages"
	private const val KEY_ENDPOINT_URL = "endpoint_url"
	private const val KEY_SECRET = "endpoint_secret"

	private const val DEFAULT_ENDPOINT = "https://httpbin.org/post"

	fun getSelectedPackages(context: Context): Set<String> {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		return prefs.getStringSet(KEY_SELECTED_PACKAGES, emptySet()) ?: emptySet()
	}

	fun setSelectedPackages(context: Context, packages: Set<String>) {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		prefs.edit {
			putStringSet(KEY_SELECTED_PACKAGES, packages.toSet())
		}
	}

	fun getEndpointUrl(context: Context): String {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val url = prefs.getString(KEY_ENDPOINT_URL, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
		return if (runCatching { Uri.parse(url).scheme }.getOrNull() != null) url else DEFAULT_ENDPOINT
	}

	fun setEndpointUrl(context: Context, url: String) {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		prefs.edit { putString(KEY_ENDPOINT_URL, url) }
	}

	fun getSecret(context: Context): String? {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		return prefs.getString(KEY_SECRET, null)
	}

	fun setSecret(context: Context, secret: String?) {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		prefs.edit {
			if (secret.isNullOrBlank()) remove(KEY_SECRET) else putString(KEY_SECRET, secret)
		}
	}
}


