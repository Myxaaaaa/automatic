package com.example.notifyforwarder.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notifyforwarder.R
import com.example.notifyforwarder.databinding.FragmentSettingsBinding
import com.example.notifyforwarder.prefs.AppPreferences
import com.example.notifyforwarder.service.NotificationForwarderService
import com.example.notifyforwarder.ui.ScanQrActivity
import com.example.notifyforwarder.ui.adapter.AppListAdapter

class SettingsFragment : Fragment() {
	private var _binding: FragmentSettingsBinding? = null
	private val binding get() = _binding!!
	private lateinit var adapter: AppListAdapter

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FragmentSettingsBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		binding.appsRecycler.layoutManager = LinearLayoutManager(requireContext())
		adapter = AppListAdapter(
			context = requireContext(),
			initialSelectedPackages = AppPreferences.getSelectedPackages(requireContext()).toMutableSet()
		) { updatedSelection ->
			AppPreferences.setSelectedPackages(requireContext(), updatedSelection)
		}
		binding.appsRecycler.adapter = adapter

		binding.switchAllApps.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				AppPreferences.setSelectedPackages(requireContext(), emptySet())
				adapter.selectAll(false)
			}
			binding.appsRecycler.visibility = if (isChecked) View.GONE else View.VISIBLE
		}

		val hasSelection = AppPreferences.getSelectedPackages(requireContext()).isNotEmpty()
		binding.switchAllApps.isChecked = !hasSelection
		binding.appsRecycler.visibility = if (!hasSelection) View.GONE else View.VISIBLE

		binding.btnOpenNotifAccess.setOnClickListener {
			startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
		}

		binding.btnTestPost.setOnClickListener {
			NotificationForwarderService.enqueueTest(requireContext())
		}

		binding.btnScanQr.setOnClickListener {
			startActivity(Intent(requireContext(), ScanQrActivity::class.java))
		}

		adapter.loadInstalledApps()
		updateServiceStatus()
		updateConnectionInfo()
	}

	override fun onResume() {
		super.onResume()
		updateServiceStatus()
		updateConnectionInfo()
	}

	private fun updateServiceStatus() {
		val hasAccess = NotificationForwarderService.hasNotificationAccess(requireContext())
		binding.tvStatus.text = if (hasAccess) {
			getString(R.string.status_enabled)
		} else {
			getString(R.string.status_disabled)
		}
		binding.tvStatus.setTextColor(
			if (hasAccess) {
				requireContext().getColor(android.R.color.holo_green_dark)
			} else {
				requireContext().getColor(android.R.color.holo_red_dark)
			}
		)
	}

	private fun updateConnectionInfo() {
		val endpoint = AppPreferences.getEndpointUrl(requireContext())
		val deviceId = AppPreferences.getDeviceId(requireContext()) ?: "—"
		val account = AppPreferences.getAccountLabel(requireContext()) ?: "—"
		binding.tvConnection.text = getString(R.string.connection_details, endpoint, deviceId, account)
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}

