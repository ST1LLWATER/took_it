package com.tookit.app.ui.today

import android.app.Application
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tookit.app.data.model.AppConfig
import com.tookit.app.data.model.TimeSlot
import com.tookit.app.data.repository.MedicineRepository
import com.tookit.app.widget.TookItWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicineRepository(application)

    /** Live config - re-emits whenever the widget or this screen mutates state. */
    val config: StateFlow<AppConfig> = repository.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppConfig())

    /** Slot the user is currently looking at; null means "follow the active slot". */
    private val _selectedSlot = MutableStateFlow<TimeSlot?>(null)
    val selectedSlot: StateFlow<TimeSlot?> = _selectedSlot

    fun selectSlot(slot: TimeSlot) {
        _selectedSlot.value = slot
    }

    fun activeSlot(config: AppConfig): TimeSlot = repository.getActiveSlotSync(config)

    fun toggle(slot: TimeSlot, medicineId: String) {
        viewModelScope.launch {
            repository.toggleMedicine(slot, medicineId)
            refreshWidgets()
        }
    }

    fun skip(slot: TimeSlot, medicineId: String) {
        viewModelScope.launch {
            repository.skipMedicine(slot, medicineId)
            refreshWidgets()
        }
    }

    private suspend fun refreshWidgets() {
        val context = getApplication<Application>()
        GlanceAppWidgetManager(context)
            .getGlanceIds(TookItWidget::class.java)
            .forEach { glanceId -> TookItWidget().update(context, glanceId) }
    }
}
