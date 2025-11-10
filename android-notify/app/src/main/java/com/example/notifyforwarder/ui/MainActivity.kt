package com.example.notifyforwarder.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.notifyforwarder.R
import com.example.notifyforwarder.databinding.ActivityMainBinding
import com.example.notifyforwarder.ui.logs.LogsFragment
import com.example.notifyforwarder.ui.settings.SettingsFragment
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.bottomNavigation.setOnItemSelectedListener { item ->
			when (item.itemId) {
				R.id.nav_settings -> {
					supportFragmentManager.commit {
						replace(R.id.fragmentContainer, SettingsFragment())
					}
					true
				}
				R.id.nav_logs -> {
					supportFragmentManager.commit {
						replace(R.id.fragmentContainer, LogsFragment())
					}
					true
				}
				else -> false
			}
		}

		// Load default fragment
		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.fragmentContainer, SettingsFragment())
			}
			binding.bottomNavigation.selectedItemId = R.id.nav_settings
		}
	}
}
