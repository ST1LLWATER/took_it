package com.tookit.app.widget

import com.tookit.app.data.model.Medicine
import com.tookit.app.data.model.TimeSlot

/**
 * Immutable snapshot of what the widget should currently display.
 * Stored in Glance's own Preferences state so the widget reliably refreshes.
 */
data class WidgetState(
    val slot: TimeSlot,
    val medicines: List<Medicine>,
    val takenCount: Int,
    val skippedCount: Int,
    val totalCount: Int,
    val isDelayed: Boolean = false
) {
    val remainingCount: Int = totalCount - takenCount - skippedCount
    val allDone: Boolean = totalCount > 0 && remainingCount == 0
}
