package com.tookit.app.data.model

import java.time.LocalTime

/**
 * Full user configuration persisted on device.
 */
data class AppConfig(
    val slots: Map<TimeSlot, SlotConfig> = TimeSlot.entries.associateWith { SlotConfig() },
    val dailyResetTime: LocalTime = LocalTime.of(4, 0),
    val accentColorHex: String = "#4CAF50",
    val notificationsEnabled: Boolean = false,
    val pinnedSlot: TimeSlot? = null
)
