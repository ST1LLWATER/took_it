package com.tookit.app.data.model

import java.time.LocalTime

enum class TimeSlot(val label: String, val defaultHour: Int, val defaultMinute: Int) {
    MORNING("Morning", 6, 0),
    EVENING("Evening", 16, 0),
    NIGHT("Night", 20, 0);

    fun defaultStartTime(): LocalTime = LocalTime.of(defaultHour, defaultMinute)
}
