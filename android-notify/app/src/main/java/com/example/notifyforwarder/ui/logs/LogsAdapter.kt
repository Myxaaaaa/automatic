package com.example.notifyforwarder.ui.logs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.notifyforwarder.R
import com.example.notifyforwarder.data.NotificationLog
import java.text.SimpleDateFormat
import java.util.*

class LogsAdapter(private val context: Context) : ListAdapter<NotificationLog, LogsAdapter.LogViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
		return LogViewHolder(view)
	}

	override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val tvPackage: TextView = itemView.findViewById(R.id.tvPackage)
		private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
		private val tvText: TextView = itemView.findViewById(R.id.tvText)
		private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
		private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
		private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

		fun bind(log: NotificationLog) {
			tvPackage.text = log.packageName
			tvTitle.text = log.title
			tvText.text = log.text
			tvAmount.text = log.amount ?: "—"
			tvTime.text = formatTime(log.postedAt)
			
			if (log.success) {
				tvStatus.text = "✓"
				tvStatus.setTextColor(context.getColor(android.R.color.holo_green_dark))
			} else {
				tvStatus.text = "✗"
				tvStatus.setTextColor(context.getColor(android.R.color.holo_red_dark))
				if (log.errorMessage != null) {
					tvText.text = "${log.text}\nОшибка: ${log.errorMessage}"
				}
			}
		}

		private fun formatTime(timestamp: Long): String {
			val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
			return sdf.format(Date(timestamp))
		}
	}

	class DiffCallback : DiffUtil.ItemCallback<NotificationLog>() {
		override fun areItemsTheSame(oldItem: NotificationLog, newItem: NotificationLog): Boolean {
			return oldItem.id == newItem.id
		}

		override fun areContentsTheSame(oldItem: NotificationLog, newItem: NotificationLog): Boolean {
			return oldItem == newItem
		}
	}
}

