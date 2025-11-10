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
import android.text.TextWatcher
import android.text.Editable

class LogsFragment : Fragment() {
	private var _binding: FragmentLogsBinding? = null
	private val binding get() = _binding!!

	private val viewModel: LogsViewModel by viewModels {
		try {
			val database = AppDatabase.getDatabase(requireContext())
			val repository = LogRepository(database.notificationLogDao())
			LogsViewModelFactory(repository)
		} catch (e: Exception) {
			android.util.Log.e("LogsFragment", "Error creating ViewModel", e)
			// Fallback to empty repository
			val database = AppDatabase.getDatabase(requireContext().applicationContext)
			val repository = LogRepository(database.notificationLogDao())
			LogsViewModelFactory(repository)
		}
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

		try {
			adapter = LogsAdapter(requireContext())
			binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
			binding.recyclerView.adapter = adapter

			// Поиск по логам
			try {
				val searchEditText = binding.root.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
				searchEditText?.addTextChangedListener(object : TextWatcher {
					override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
					override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
					override fun afterTextChanged(s: Editable?) {
						viewModel.setSearchQuery(s?.toString() ?: "")
					}
				})
			} catch (e: Exception) {
				android.util.Log.e("LogsFragment", "Error setting up search", e)
			}

			viewLifecycleOwner.lifecycleScope.launch {
				try {
					viewModel.filteredLogs.collect { logs ->
						try {
							adapter.submitList(logs)
							binding.emptyView.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
						} catch (e: Exception) {
							android.util.Log.e("LogsFragment", "Error updating logs", e)
						}
					}
				} catch (e: Exception) {
					android.util.Log.e("LogsFragment", "Error collecting logs", e)
				}
			}

			viewLifecycleOwner.lifecycleScope.launch {
				try {
					viewModel.successCount.collect { count ->
						binding.tvSuccessCount.text = count.toString()
					}
				} catch (e: Exception) {
					android.util.Log.e("LogsFragment", "Error collecting success count", e)
				}
			}

			viewLifecycleOwner.lifecycleScope.launch {
				try {
					viewModel.errorCount.collect { count ->
						binding.tvErrorCount.text = count.toString()
					}
				} catch (e: Exception) {
					android.util.Log.e("LogsFragment", "Error collecting error count", e)
				}
			}

			binding.btnClearLogs.setOnClickListener {
				try {
					MaterialAlertDialogBuilder(requireContext())
						.setTitle("Очистить логи")
						.setMessage("Удалить все логи?")
						.setPositiveButton("Да") { _, _ -> 
							try {
								viewModel.clearLogs()
							} catch (e: Exception) {
								android.util.Log.e("LogsFragment", "Error clearing logs", e)
							}
						}
						.setNegativeButton("Отмена", null)
						.show()
				} catch (e: Exception) {
					android.util.Log.e("LogsFragment", "Error showing dialog", e)
				}
			}
		} catch (e: Exception) {
			android.util.Log.e("LogsFragment", "Error in onViewCreated", e)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}

