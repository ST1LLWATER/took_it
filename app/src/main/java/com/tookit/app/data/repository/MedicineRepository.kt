package com.tookit.app.data.repository

import android.content.Context
import com.tookit.app.data.datastore.MedicineDataStore
import com.tookit.app.data.model.AppConfig
import com.tookit.app.data.model.LogAction
import com.tookit.app.data.model.Medicine
import com.tookit.app.data.model.MedicineLogEntry
import com.tookit.app.data.model.SlotConfig
import com.tookit.app.data.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicineRepository(context: Context) {

    private val dataStore = MedicineDataStore(context.applicationContext)

    val configFlow: Flow<AppConfig> = dataStore.configFlow

    suspend fun getConfig(): AppConfig = dataStore.configFlow.first()

    suspend fun saveConfig(config: AppConfig) = dataStore.saveConfig(config)

    suspend fun addMedicine(slot: TimeSlot, name: String) {
        val config = getConfig()
        val slotConfig = config.slots[slot] ?: SlotConfig()
        if (name.isBlank()) return
        val updated = slotConfig.copy(
            medicines = slotConfig.medicines + Medicine(
                id = UUID.randomUUID().toString(),
                name = name.trim()
            )
        )
        val newSlots = config.slots.toMutableMap().apply { put(slot, updated) }
        saveConfig(config.copy(slots = newSlots))
    }

    suspend fun updateMedicine(slot: TimeSlot, medicineId: String, newName: String) {
        if (newName.isBlank()) return
        val config = getConfig()
        val slotConfig = config.slots[slot] ?: return
        val updated = slotConfig.copy(
            medicines = slotConfig.medicines.map {
                if (it.id == medicineId) it.copy(name = newName.trim()) else it
            }
        )
        val newSlots = config.slots.toMutableMap().apply { put(slot, updated) }
        saveConfig(config.copy(slots = newSlots))
    }

    suspend fun deleteMedicine(slot: TimeSlot, medicineId: String) {
        val config = getConfig()
        val slotConfig = config.slots[slot] ?: return
        val updated = slotConfig.copy(
            medicines = slotConfig.medicines.filter { it.id != medicineId }
        )
        val newSlots = config.slots.toMutableMap().apply { put(slot, updated) }
        saveConfig(config.copy(slots = newSlots))
    }

    suspend fun toggleMedicine(slot: TimeSlot, medicineId: String) {
        val config = getConfig()
        val medicine = config.slots[slot]?.medicines?.find { it.id == medicineId } ?: return
        val newTaken = !medicine.isTaken
        val newSkipped = if (newTaken) false else medicine.isSkipped
        dataStore.updateMedicineState(slot, medicineId, newTaken, newSkipped)

        val today = LocalDateTime.now().toLocalDate()
        if (newTaken) {
            dataStore.logEntry(
                MedicineLogEntry(
                    id = UUID.randomUUID().toString(),
                    medicineId = medicineId,
                    medicineName = medicine.name,
                    slot = slot,
                    action = LogAction.TAKEN,
                    timestamp = LocalDateTime.now()
                )
            )
        } else if (!newSkipped) {
            // Back to pending - remove any log entry for today.
            dataStore.removeLogEntry(medicineId, slot, today)
        }
    }

    suspend fun skipMedicine(slot: TimeSlot, medicineId: String) {
        val config = getConfig()
        val medicine = config.slots[slot]?.medicines?.find { it.id == medicineId } ?: return
        dataStore.updateMedicineState(slot, medicineId, false, true)
        dataStore.logEntry(
            MedicineLogEntry(
                id = UUID.randomUUID().toString(),
                medicineId = medicineId,
                medicineName = medicine.name,
                slot = slot,
                action = LogAction.SKIPPED,
                timestamp = LocalDateTime.now()
            )
        )
    }

    suspend fun setSlotEnabled(slot: TimeSlot, enabled: Boolean) {
        val config = getConfig()
        val slotConfig = config.slots[slot] ?: SlotConfig()
        val newSlots = config.slots.toMutableMap().apply { put(slot, slotConfig.copy(enabled = enabled)) }
        saveConfig(config.copy(slots = newSlots))
    }

    suspend fun setSlotTime(slot: TimeSlot, time: LocalTime) {
        val config = getConfig()
        val slotConfig = config.slots[slot] ?: SlotConfig()
        val newSlots = config.slots.toMutableMap().apply { put(slot, slotConfig.copy(startTime = time)) }
        saveConfig(config.copy(slots = newSlots))
    }

    suspend fun setResetTime(time: LocalTime) {
        val config = getConfig()
        saveConfig(config.copy(dailyResetTime = time))
    }

    suspend fun resetAllTakenStates() = dataStore.resetAllTakenStates()

    suspend fun getLog(): List<MedicineLogEntry> = dataStore.getLog()

    /**
     * Returns the active time slot. Once a slot is pinned, it stays pinned until all its
     * medicines are taken or skipped. Then it unpins and the next time-based slot is chosen.
     */
    suspend fun getActiveSlot(config: AppConfig, now: LocalDateTime = LocalDateTime.now()): TimeSlot {
        val pinned = config.pinnedSlot
        if (pinned != null) {
            val meds = config.slots[pinned]?.medicines ?: emptyList()
            val remaining = meds.count { !it.isTaken && !it.isSkipped }
            if (remaining > 0) return pinned
        }

        val currentTime = now.toLocalTime()
        val enabledSlots = TimeSlot.entries.filter { config.slots[it]?.enabled == true }
        if (enabledSlots.isEmpty()) return TimeSlot.MORNING

        var active = enabledSlots.first()
        for (slot in enabledSlots) {
            val start = config.slots[slot]?.startTime ?: slot.defaultStartTime()
            if (!currentTime.isBefore(start)) {
                active = slot
            }
        }

        // Pin the newly selected slot. Use a targeted write so we never re-serialize the
        // whole slots blob from this (possibly stale) snapshot - doing so would race with
        // and revert in-flight toggleMedicine/skipMedicine writes from the widget.
        if (active != pinned) {
            dataStore.updatePinnedSlot(active)
        }
        return active
    }

    /**
     * Non-suspending version for composables that already have a fresh config.
     * Does NOT pin; just computes what would be shown based on pinned slot or time.
     */
    fun getActiveSlotSync(config: AppConfig, now: LocalDateTime = LocalDateTime.now()): TimeSlot {
        val pinned = config.pinnedSlot
        if (pinned != null) {
            val meds = config.slots[pinned]?.medicines ?: emptyList()
            val remaining = meds.count { !it.isTaken && !it.isSkipped }
            if (remaining > 0) return pinned
        }

        val currentTime = now.toLocalTime()
        val enabledSlots = TimeSlot.entries.filter { config.slots[it]?.enabled == true }
        if (enabledSlots.isEmpty()) return TimeSlot.MORNING

        var active = enabledSlots.first()
        for (slot in enabledSlots) {
            val start = config.slots[slot]?.startTime ?: slot.defaultStartTime()
            if (!currentTime.isBefore(start)) {
                active = slot
            }
        }
        return active
    }

    fun getProgress(slot: TimeSlot, config: AppConfig): Pair<Int, Int> {
        val medicines = config.slots[slot]?.medicines ?: emptyList()
        val taken = medicines.count { it.isTaken }
        return taken to medicines.size
    }

    fun getSkipProgress(slot: TimeSlot, config: AppConfig): Pair<Int, Int> {
        val medicines = config.slots[slot]?.medicines ?: emptyList()
        val skipped = medicines.count { it.isSkipped }
        return skipped to medicines.size
    }
}
