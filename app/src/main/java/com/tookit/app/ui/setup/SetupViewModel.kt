package com.tookit.app.ui.setup

import android.app.Application
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tookit.app.data.model.AppConfig
import com.tookit.app.data.model.TimeSlot
import com.tookit.app.data.repository.MedicineRepository
import com.tookit.app.widget.TookItWidget
import com.tookit.app.worker.DailyResetWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicineRepository(application)

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    fun load() {
        viewModelScope.launch {
            repository.configFlow.collect { _config.value = it }
        }
    }

    fun save() {
        viewModelScope.launch {
            repository.saveConfig(_config.value)
            DailyResetWorker.schedule(getApplication(), _config.value.dailyResetTime)
            refreshWidgets()
            _saved.value = true
        }
    }

    fun onSaveAcknowledged() {
        _saved.value = false
    }

    fun updateResetTime(time: LocalTime) {
        _config.value = _config.value.copy(dailyResetTime = time)
    }

    fun setSlotEnabled(slot: TimeSlot, enabled: Boolean) {
        val slots = _config.value.slots.toMutableMap()
        val current = slots[slot] ?: return
        slots[slot] = current.copy(enabled = enabled)
        _config.value = _config.value.copy(slots = slots)
    }

    fun setSlotTime(slot: TimeSlot, time: LocalTime) {
        val slots = _config.value.slots.toMutableMap()
        val current = slots[slot] ?: return
        slots[slot] = current.copy(startTime = time)
        _config.value = _config.value.copy(slots = slots)
    }

    fun addMedicine(slot: TimeSlot, name: String) {
        viewModelScope.launch {
            repository.addMedicine(slot, name)
            _config.value = repository.getConfig()
            refreshWidgets()
        }
    }

    fun updateMedicine(slot: TimeSlot, medicineId: String, name: String) {
        viewModelScope.launch {
            repository.updateMedicine(slot, medicineId, name)
            _config.value = repository.getConfig()
            refreshWidgets()
        }
    }

    fun deleteMedicine(slot: TimeSlot, medicineId: String) {
        viewModelScope.launch {
            repository.deleteMedicine(slot, medicineId)
            _config.value = repository.getConfig()
            refreshWidgets()
        }
    }

    private suspend fun refreshWidgets() {
        val context = getApplication<Application>()
        GlanceAppWidgetManager(context)
            .getGlanceIds(TookItWidget::class.java)
            .forEach { glanceId ->
                TookItWidget().refresh(context, glanceId)
            }
    }
}
