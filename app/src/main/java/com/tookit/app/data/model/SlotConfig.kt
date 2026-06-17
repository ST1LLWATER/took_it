package com.tookit.app.data.model

import java.time.LocalTime

/**
 * Configuration for one time slot.
 */
data class SlotConfig(
    val enabled: Boolean = true,
    val startTime: LocalTime = TimeSlot.MORNING.defaultStartTime(),
    val medicines: List<Medicine> = emptyList()
)
