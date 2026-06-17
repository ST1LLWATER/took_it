package com.tookit.app.data.model

import java.time.LocalDateTime

enum class LogAction { TAKEN, SKIPPED }

data class MedicineLogEntry(
    val id: String,
    val medicineId: String,
    val medicineName: String,
    val slot: TimeSlot,
    val action: LogAction,
    val timestamp: LocalDateTime
)
