package com.example.notifyforwarder.ui.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notifyforwarder.data.AppDatabase
import com.example.notifyforwarder.data.LogRepository
import com.example.notifyforwarder.databinding.FragmentLogsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LogsFragment : Fragment() {
	private var _binding: FragmentLogsBinding? = null
	private val binding get() = _binding!!

	private val viewModel: LogsViewModel by viewModels {
		val database = AppDatabase.getDatabase(requireContext())
		val repository = LogRepository(database.notificationLogDao())
		LogsViewModelFactory(repository)
	}

	private lateinit var adapter: LogsAdapter

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FragmentLogsBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		adapter = LogsAdapter(requireContext())
		binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
		binding.recyclerView.adapter = adapter

		viewLifecycleOwner.lifecycleScope.launch {
			viewModel.logs.collect { logs ->
				adapter.submitList(logs)
				binding.emptyView.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
			}
		}

		viewLifecycleOwner.lifecycleScope.launch {
			viewModel.successCount.collect { count ->
				binding.tvSuccessCount.text = count.toString()
			}
		}

		viewLifecycleOwner.lifecycleScope.launch {
			viewModel.errorCount.collect { count ->
				binding.tvErrorCount.text = count.toString()
			}
		}

		binding.btnClearLogs.setOnClickListener {
			MaterialAlertDialogBuilder(requireContext())
				.setTitle("Очистить логи")
				.setMessage("Удалить все логи?")
				.setPositiveButton("Да") { _, _ -> viewModel.clearLogs() }
				.setNegativeButton("Отмена", null)
				.show()
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}

