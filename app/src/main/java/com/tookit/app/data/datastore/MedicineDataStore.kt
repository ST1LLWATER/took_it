package com.tookit.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tookit.app.data.model.AppConfig
import com.tookit.app.data.model.LogAction
import com.tookit.app.data.model.Medicine
import com.tookit.app.data.model.MedicineLogEntry
import com.tookit.app.data.model.SlotConfig
import com.tookit.app.data.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tookit_prefs")

class MedicineDataStore(private val context: Context) {

    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    companion object {
        private val SLOTS_KEY = stringPreferencesKey("slots_config")
        private val RESET_TIME_KEY = stringPreferencesKey("daily_reset_time")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
        private val PINNED_SLOT_KEY = stringPreferencesKey("pinned_slot")
        private val LOG_KEY = stringPreferencesKey("medicine_log")
    }

    val configFlow: Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            slots = parseSlots(prefs[SLOTS_KEY]),
            dailyResetTime = parseTime(prefs[RESET_TIME_KEY]) ?: LocalTime.of(4, 0),
            accentColorHex = prefs[ACCENT_COLOR_KEY] ?: "#4CAF50",
            notificationsEnabled = prefs[NOTIFICATIONS_KEY] ?: false,
            pinnedSlot = prefs[PINNED_SLOT_KEY]?.let { parseSlot(it) }
        )
    }

    suspend fun saveConfig(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[SLOTS_KEY] = slotsToJson(config.slots).toString()
            prefs[RESET_TIME_KEY] = config.dailyResetTime.format(timeFormatter)
            prefs[ACCENT_COLOR_KEY] = config.accentColorHex
            prefs[NOTIFICATIONS_KEY] = config.notificationsEnabled
            config.pinnedSlot?.name?.let { prefs[PINNED_SLOT_KEY] = it } ?: prefs.remove(PINNED_SLOT_KEY)
        }
    }

    suspend fun updateMedicineState(
        slot: TimeSlot,
        medicineId: String,
        isTaken: Boolean,
        isSkipped: Boolean
    ) {
        context.dataStore.edit { prefs ->
            val current = parseSlots(prefs[SLOTS_KEY]).toMutableMap()
            val slotConfig = current[slot] ?: SlotConfig()
            val updatedMedicines = slotConfig.medicines.map { med ->
                if (med.id == medicineId) {
                    med.copy(
                        isTaken = isTaken,
                        isSkipped = isSkipped,
                        takenAt = if (isTaken) LocalDateTime.now() else null
                    )
                } else med
            }
            current[slot] = slotConfig.copy(medicines = updatedMedicines)
            prefs[SLOTS_KEY] = slotsToJson(current).toString()
        }
    }

    /**
     * Targeted write of only the pinned-slot preference. Unlike [saveConfig] this does NOT
     * re-serialize the slots/medicines blob, so it cannot clobber a concurrent
     * [updateMedicineState] write (e.g. a widget toggle in flight).
     */
    suspend fun updatePinnedSlot(slot: TimeSlot?) {
        context.dataStore.edit { prefs ->
            slot?.name?.let { prefs[PINNED_SLOT_KEY] = it } ?: prefs.remove(PINNED_SLOT_KEY)
        }
    }

    suspend fun resetAllTakenStates() {
        context.dataStore.edit { prefs ->
            val current = parseSlots(prefs[SLOTS_KEY]).toMutableMap()
            val reset = current.mapValues { (_, slotConfig) ->
                slotConfig.copy(
                    medicines = slotConfig.medicines.map { it.copy(isTaken = false, isSkipped = false, takenAt = null) }
                )
            }
            prefs[SLOTS_KEY] = slotsToJson(reset).toString()
            prefs.remove(PINNED_SLOT_KEY)
        }
    }

    /**
     * Records a single log entry for a medicine on a given day/slot.
     * If an entry already exists for the same medicine+slot+day, it is replaced
     * (so toggling repeatedly does not pollute the log).
     */
    suspend fun logEntry(entry: MedicineLogEntry) {
        context.dataStore.edit { prefs ->
            val log = parseLog(prefs[LOG_KEY]).toMutableList()
            val day = entry.timestamp.toLocalDate()
            val existingIndex = log.indexOfFirst {
                it.medicineId == entry.medicineId &&
                        it.slot == entry.slot &&
                        it.timestamp.toLocalDate() == day
            }
            if (existingIndex >= 0) {
                log[existingIndex] = entry
            } else {
                log.add(entry)
            }
            // Keep only last 30 days
            val cutoff = LocalDateTime.now().minusDays(30)
            val filtered = log.filter { !it.timestamp.isBefore(cutoff) }
            prefs[LOG_KEY] = logToJson(filtered).toString()
        }
    }

    /**
     * Removes the log entry for a medicine on a given day/slot.
     * Called when the user un-marks a medicine (back to pending).
     */
    suspend fun removeLogEntry(medicineId: String, slot: TimeSlot, day: java.time.LocalDate) {
        context.dataStore.edit { prefs ->
            val log = parseLog(prefs[LOG_KEY]).toMutableList()
            log.removeAll {
                it.medicineId == medicineId &&
                        it.slot == slot &&
                        it.timestamp.toLocalDate() == day
            }
            prefs[LOG_KEY] = logToJson(log).toString()
        }
    }

    suspend fun getLog(): List<MedicineLogEntry> {
        return parseLog(context.dataStore.data.first()[LOG_KEY])
    }

    private fun parseSlots(json: String?): Map<TimeSlot, SlotConfig> {
        if (json.isNullOrBlank()) return TimeSlot.entries.associateWith { SlotConfig() }
        return try {
            val obj = JSONObject(json)
            TimeSlot.entries.associateWith { slot ->
                val slotObj = obj.optJSONObject(slot.name) ?: return@associateWith SlotConfig()
                SlotConfig(
                    enabled = slotObj.optBoolean("enabled", true),
                    startTime = parseTime(slotObj.optString("startTime")) ?: slot.defaultStartTime(),
                    medicines = parseMedicines(slotObj.optJSONArray("medicines"))
                )
            }
        } catch (e: Exception) {
            TimeSlot.entries.associateWith { SlotConfig() }
        }
    }

    private fun parseMedicines(array: JSONArray?): List<Medicine> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            try {
                val obj = array.getJSONObject(i)
                val taken = obj.optBoolean("isTaken", false)
                val skipped = obj.optBoolean("isSkipped", false)
                Medicine(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "").trim(),
                    isTaken = taken,
                    isSkipped = skipped,
                    takenAt = if (taken) parseDateTime(obj.optString("takenAt")) else null
                )
            } catch (e: Exception) {
                null
            }
        }.filter { it.name.isNotBlank() }
    }

    private fun parseLog(json: String?): List<MedicineLogEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                try {
                    val obj = array.getJSONObject(i)
                    MedicineLogEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        medicineId = obj.optString("medicineId", ""),
                        medicineName = obj.optString("medicineName", ""),
                        slot = parseSlot(obj.optString("slot")) ?: return@mapNotNull null,
                        action = parseLogAction(obj.optString("action")) ?: return@mapNotNull null,
                        timestamp = parseDateTime(obj.optString("timestamp")) ?: return@mapNotNull null
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun slotsToJson(slots: Map<TimeSlot, SlotConfig>): JSONObject {
        val root = JSONObject()
        slots.forEach { (slot, config) ->
            root.put(slot.name, slotConfigToJson(config))
        }
        return root
    }

    private fun slotConfigToJson(config: SlotConfig): JSONObject {
        val obj = JSONObject()
        obj.put("enabled", config.enabled)
        obj.put("startTime", config.startTime.format(timeFormatter))
        obj.put("medicines", JSONArray(config.medicines.map { medicineToJson(it) }))
        return obj
    }

    private fun medicineToJson(medicine: Medicine): JSONObject {
        val obj = JSONObject()
        obj.put("id", medicine.id)
        obj.put("name", medicine.name)
        obj.put("isTaken", medicine.isTaken)
        obj.put("isSkipped", medicine.isSkipped)
        medicine.takenAt?.let { obj.put("takenAt", it.format(dateTimeFormatter)) }
        return obj
    }

    private fun logToJson(log: List<MedicineLogEntry>): JSONArray {
        return JSONArray(log.map { entryToJson(it) })
    }

    private fun entryToJson(entry: MedicineLogEntry): JSONObject {
        val obj = JSONObject()
        obj.put("id", entry.id)
        obj.put("medicineId", entry.medicineId)
        obj.put("medicineName", entry.medicineName)
        obj.put("slot", entry.slot.name)
        obj.put("action", entry.action.name)
        obj.put("timestamp", entry.timestamp.format(dateTimeFormatter))
        return obj
    }

    private fun parseTime(value: String?): LocalTime? {
        return try { value?.let { LocalTime.parse(it, timeFormatter) } } catch (e: Exception) { null }
    }

    private fun parseDateTime(value: String?): LocalDateTime? {
        return try { value?.let { LocalDateTime.parse(it, dateTimeFormatter) } } catch (e: Exception) { null }
    }

    private fun parseSlot(value: String?): TimeSlot? {
        return try { value?.let { TimeSlot.valueOf(it) } } catch (e: Exception) { null }
    }

    private fun parseLogAction(value: String?): LogAction? {
        return try { value?.let { LogAction.valueOf(it) } } catch (e: Exception) { null }
    }
}
