package com.example.notifyforwarder.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notifyforwarder.R
import com.example.notifyforwarder.databinding.ActivityMainBinding
import com.example.notifyforwarder.prefs.AppPreferences
import com.example.notifyforwarder.service.NotificationForwarderService
import com.example.notifyforwarder.ui.adapter.AppInfo
import com.example.notifyforwarder.ui.adapter.AppListAdapter

class MainActivity : ComponentActivity() {
	private lateinit var binding: ActivityMainBinding
	private lateinit var adapter: AppListAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		setActionBar(binding.toolbar)

		binding.appsRecycler.layoutManager = LinearLayoutManager(this)
		adapter = AppListAdapter(
			context = this,
			initialSelectedPackages = AppPreferences.getSelectedPackages(this).toMutableSet()
		) { updatedSelection ->
			AppPreferences.setSelectedPackages(this, updatedSelection)
		}
		binding.appsRecycler.adapter = adapter

		binding.btnOpenNotifAccess.setOnClickListener {
			startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
		}
		binding.btnTestPost.setOnClickListener {
			NotificationForwarderService.enqueueTest(this)
		}

		adapter.loadInstalledApps()
		updateServiceStatus()
	}

	override fun onResume() {
		super.onResume()
		updateServiceStatus()
	}

	private fun updateServiceStatus() {
		val hasAccess = NotificationForwarderService.hasNotificationAccess(this)
		binding.tvStatus.text = if (hasAccess) {
			getString(R.string.status_enabled)
		} else {
			getString(R.string.status_disabled)
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_refresh -> {
				adapter.loadInstalledApps()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}
}


