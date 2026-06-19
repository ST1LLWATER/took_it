package com.tookit.app.ui.log

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tookit.app.data.model.MedicineLogEntry
import com.tookit.app.data.repository.MedicineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicineRepository(application)

    private val _logEntries = MutableStateFlow<List<MedicineLogEntry>>(emptyList())
    val logEntries: StateFlow<List<MedicineLogEntry>> = _logEntries

    fun load() {
        viewModelScope.launch {
            _logEntries.value = repository.getLog()
        }
    }
}
