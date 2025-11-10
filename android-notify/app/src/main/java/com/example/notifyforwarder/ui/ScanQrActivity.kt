package com.example.notifyforwarder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.notifyforwarder.prefs.AppPreferences
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject
import java.util.UUID

class ScanQrActivity : AppCompatActivity() {
	private val requestCamera = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		if (granted) startScan() else {
			Toast.makeText(this, "Камера не разрешена", Toast.LENGTH_SHORT).show()
			finish()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		when {
			ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startScan()
			else -> requestCamera.launch(Manifest.permission.CAMERA)
		}
	}

	private fun startScan() {
		IntentIntegrator(this)
			.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
			.setBeepEnabled(false)
			.setPrompt("Наведите на QR с параметрами платформы")
			.initiateScan()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
		if (result != null && result.contents != null) {
			runCatching {
				val obj = JSONObject(result.contents)
				val endpoint = obj.optString("endpoint")
				val secret = obj.optString("secret", null)
				val deviceId = obj.optString("deviceId", null)
				val account = obj.optString("account", null)
				if (endpoint.isNotBlank()) {
					AppPreferences.setEndpointUrl(this, endpoint)
					AppPreferences.setSecret(this, secret)
					val resolvedDeviceId = if (!deviceId.isNullOrBlank()) deviceId else {
						UUID.randomUUID().toString()
					}
					AppPreferences.setDeviceId(this, resolvedDeviceId)
					AppPreferences.setAccountLabel(this, account)
					Toast.makeText(this, "Платформа подключена", Toast.LENGTH_SHORT).show()
					setResult(RESULT_OK)
				} else {
					Toast.makeText(this, "Некорректный QR", Toast.LENGTH_SHORT).show()
					setResult(RESULT_CANCELED)
				}
			}.onFailure {
				Toast.makeText(this, "Ошибка чтения QR", Toast.LENGTH_SHORT).show()
				setResult(RESULT_CANCELED)
			}
		}
		finish()
	}
}


