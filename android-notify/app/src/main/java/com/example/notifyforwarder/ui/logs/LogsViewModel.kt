package com.example.notifyforwarder.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notifyforwarder.data.LogRepository
import com.example.notifyforwarder.data.NotificationLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogsViewModel(private val repository: LogRepository) : ViewModel() {
	private val _searchQuery = MutableStateFlow("")
	val searchQuery = _searchQuery.asStateFlow()

	val logs = repository.getRecentLogs(500).stateIn(
		viewModelScope,
		SharingStarted.WhileSubscribed(5000),
		emptyList()
	)

	val filteredLogs = kotlinx.coroutines.flow.combine(logs, searchQuery) { allLogs, query ->
		if (query.isBlank()) {
			allLogs
		} else {
			val lowerQuery = query.lowercase()
			allLogs.filter { log ->
				log.packageName.lowercase().contains(lowerQuery) ||
				log.title.lowercase().contains(lowerQuery) ||
				log.text.lowercase().contains(lowerQuery) ||
				(log.amount?.lowercase()?.contains(lowerQuery) == true)
			}
		}
	}.stateIn(
		viewModelScope,
		SharingStarted.WhileSubscribed(5000),
		emptyList()
	)

	val successCount = repository.getSuccessCount().stateIn(
		viewModelScope,
		SharingStarted.WhileSubscribed(5000),
		0
	)

	val errorCount = repository.getErrorCount().stateIn(
		viewModelScope,
		SharingStarted.WhileSubscribed(5000),
		0
	)

	fun setSearchQuery(query: String) {
		_searchQuery.value = query
	}

	fun clearLogs() {
		viewModelScope.launch {
			repository.clearAll()
		}
	}

	fun deleteOldLogs() {
		viewModelScope.launch {
			repository.deleteOldLogs(1000)
		}
	}
}

class LogsViewModelFactory(private val repository: LogRepository) : ViewModelProvider.Factory {
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(LogsViewModel::class.java)) {
			@Suppress("UNCHECKED_CAST")
			return LogsViewModel(repository) as T
		}
		throw IllegalArgumentException("Unknown ViewModel class")
	}
}

