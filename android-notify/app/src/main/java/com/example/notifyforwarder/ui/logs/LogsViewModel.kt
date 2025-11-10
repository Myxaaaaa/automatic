package com.example.notifyforwarder.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notifyforwarder.data.LogRepository
import com.example.notifyforwarder.data.NotificationLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel(private val repository: LogRepository) : ViewModel() {
	val logs = repository.getRecentLogs(200).stateIn(
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

