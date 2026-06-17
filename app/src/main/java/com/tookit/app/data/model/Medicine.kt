package com.tookit.app.data.model

import java.time.LocalDateTime

/**
 * Represents a single medicine configured for a time slot.
 */
data class Medicine(
    val id: String,
    val name: String,
    val isTaken: Boolean = false,
    val isSkipped: Boolean = false,
    val takenAt: LocalDateTime? = null
)
