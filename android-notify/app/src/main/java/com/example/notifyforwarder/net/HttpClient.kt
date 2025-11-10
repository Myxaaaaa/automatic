package com.example.notifyforwarder.net

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.example.notifyforwarder.prefs.AppPreferences

object HttpClient {
	private val client: OkHttpClient by lazy {
		OkHttpClient.Builder()
			.callTimeout(15, TimeUnit.SECONDS)
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.writeTimeout(15, TimeUnit.SECONDS)
			.build()
	}

	private val JSON = "application/json; charset=utf-8".toMediaType()

	data class PostResult(val success: Boolean, val errorMessage: String?)

	fun postJson(url: String, jsonBody: String, context: android.content.Context? = null): PostResult {
		val builder = Request.Builder()
			.url(url)
			.post(jsonBody.toRequestBody(JSON))
		context?.let {
			AppPreferences.getSecret(it)?.let { secret ->
				if (secret.isNotBlank()) builder.addHeader("Authorization", "Bearer $secret")
			}
		}
		val request = builder.build()
		return runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					Log.w("HttpClient", "Non-200: ${response.code} ${response.message}")
					PostResult(false, "HTTP ${response.code}: ${response.message}")
				} else {
					Log.d("HttpClient", "POST ok: ${response.code}")
					PostResult(true, null)
				}
			}
		}.getOrElse {
			Log.e("HttpClient", "POST failed", it)
			PostResult(false, it.message ?: "Unknown error")
		}
	}
}


