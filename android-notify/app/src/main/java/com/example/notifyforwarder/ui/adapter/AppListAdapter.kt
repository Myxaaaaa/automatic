package com.example.notifyforwarder.ui.adapter

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.notifyforwarder.databinding.ItemAppBinding

data class AppInfo(
	val label: String,
	val packageName: String,
	val icon: Drawable
)

class AppListAdapter(
	private val context: Context,
	private val initialSelectedPackages: MutableSet<String>,
	private val onSelectionChanged: (Set<String>) -> Unit
) : RecyclerView.Adapter<AppListAdapter.VH>() {

	private val pm: PackageManager = context.packageManager
	private val selected = initialSelectedPackages
	private val items: MutableList<AppInfo> = mutableListOf()

	class VH(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
		val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return VH(binding)
	}

	override fun getItemCount(): Int = items.size

	override fun onBindViewHolder(holder: VH, position: Int) {
		val app = items[position]
		holder.binding.appIcon.setImageDrawable(app.icon)
		holder.binding.appTitle.text = app.label
		holder.binding.appPackage.text = app.packageName
		holder.binding.appCheck.setOnCheckedChangeListener(null)
		holder.binding.appCheck.isChecked = selected.contains(app.packageName)
		holder.binding.appCheck.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) selected.add(app.packageName) else selected.remove(app.packageName)
			onSelectionChanged(selected)
		}
	}

	fun loadInstalledApps() {
		val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
			.filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
			.map {
				AppInfo(
					label = pm.getApplicationLabel(it).toString(),
					packageName = it.packageName,
					icon = pm.getApplicationIcon(it)
				)
			}
			.sortedBy { it.label.lowercase() }
		items.clear()
		items.addAll(apps)
		notifyDataSetChanged()
	}

	fun selectAll(select: Boolean) {
		if (select) {
			items.forEach { selected.add(it.packageName) }
		} else {
			selected.clear()
		}
		onSelectionChanged(selected)
		notifyDataSetChanged()
	}
}


