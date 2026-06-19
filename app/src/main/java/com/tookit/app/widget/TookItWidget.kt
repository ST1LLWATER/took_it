package com.tookit.app.widget

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.material3.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tookit.app.MainActivity
import com.tookit.app.R
import com.tookit.app.data.model.Medicine
import com.tookit.app.data.model.TimeSlot
import com.tookit.app.data.repository.MedicineRepository
import com.tookit.app.ui.theme.TookItDarkColorScheme
import com.tookit.app.ui.theme.TookItLightColorScheme
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val SlotKey = ActionParameters.Key<String>("slot")
private val MedicineIdKey = ActionParameters.Key<String>("medicine_id")
private val ActionKey = ActionParameters.Key<String>("action")

/** Brand colors, shared with the app so the widget matches the in-app emerald theme. */
private val TookItGlanceColors = ColorProviders(
    light = TookItLightColorScheme,
    dark = TookItDarkColorScheme
)

class TookItWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val widgetState = rememberWidgetState(prefs)

            GlanceTheme(colors = TookItGlanceColors) {
                WidgetContent(widgetState)
            }
        }
    }

    /**
     * Loads the latest app data and writes it into this widget instance's Glance state,
     * then triggers an update. Call this after every data change.
     */
    /** Red used to tint the "ringing" bell when the slot is delayed. Same in light and dark. */
    private val DelayedBellColor = ColorProvider(androidx.compose.ui.graphics.Color(0xFFE53935))

    suspend fun refresh(context: Context, glanceId: GlanceId) {
        val repo = MedicineRepository(context)
        val config = repo.getConfig()
        val activeSlot = repo.getActiveSlot(config)
        val medicines = config.slots[activeSlot]?.medicines ?: emptyList()
        val takenCount = medicines.count { it.isTaken }
        val skippedCount = medicines.count { it.isSkipped }
        val remaining = medicines.size - takenCount - skippedCount
        val slotStart = config.slots[activeSlot]?.startTime ?: activeSlot.defaultStartTime()
        val now = java.time.LocalTime.now()
        val isDelayed = remaining > 0 && now.isAfter(slotStart)

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetSlotKey] = activeSlot.name
            prefs[WidgetMedicinesKey] = medicinesToJson(medicines).toString()
            prefs[WidgetProgressKey] = "${takenCount}|${skippedCount}|${medicines.size}"
            prefs[WidgetDelayedKey] = isDelayed
            prefs[WidgetLastUpdatedKey] = System.currentTimeMillis()
        }
        update(context, glanceId)
    }

    @Composable
    private fun rememberWidgetState(prefs: Preferences): WidgetState {
        val slotName = prefs[WidgetSlotKey] ?: TimeSlot.MORNING.name
        val slot = try {
            TimeSlot.valueOf(slotName)
        } catch (e: IllegalArgumentException) {
            TimeSlot.MORNING
        }
        val medicines = parseMedicines(prefs[WidgetMedicinesKey])
        val progressParts = (prefs[WidgetProgressKey] ?: "0|0|0").split("|")
        val takenCount = progressParts.getOrNull(0)?.toIntOrNull() ?: 0
        val skippedCount = progressParts.getOrNull(1)?.toIntOrNull() ?: 0
        val totalCount = progressParts.getOrNull(2)?.toIntOrNull() ?: medicines.size
        val isDelayed = prefs[WidgetDelayedKey] ?: false

        return WidgetState(
            slot = slot,
            medicines = medicines,
            takenCount = takenCount,
            skippedCount = skippedCount,
            totalCount = totalCount,
            isDelayed = isDelayed
        )
    }

    @Composable
    private fun WidgetContent(state: WidgetState) {
        val medicines = state.medicines
        val progress = if (state.totalCount > 0) {
            (state.takenCount + state.skippedCount) / state.totalCount.toFloat()
        } else 0f

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(24.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Header(state)

            if (state.totalCount > 0) {
                Spacer(GlanceModifier.height(10.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant
                )
            }

            Spacer(GlanceModifier.height(12.dp))

            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.TopStart
            ) {
                if (medicines.isEmpty()) {
                    Text(
                        text = "No medicines for this slot.",
                        style = TextStyle(
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn {
                        items(medicines) { medicine ->
                            MedicineRow(state.slot, medicine)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(state: WidgetState) {
        val context = LocalContext.current
        val doneCount = state.takenCount + state.skippedCount
        val headerColor = GlanceTheme.colors.onBackground
        val subtextColor = GlanceTheme.colors.onSurfaceVariant

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = state.slot.label,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = headerColor
                    )
                )
                Text(
                    text = when {
                        state.totalCount == 0 -> "Add medicines in the app"
                        state.allDone -> "All done! Nicely managed."
                        else -> "${state.remainingCount} left to take"
                    },
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = subtextColor
                    )
                )
            }
            if (state.totalCount > 0) {
                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(14.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$doneCount/${state.totalCount}",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onPrimaryContainer
                        )
                    )
                }
                Spacer(GlanceModifier.width(10.dp))
            }
            // Delay indicator: a red "ringing" bell when the slot is overdue with pending
            // medicines, otherwise a neutral bell that contrasts with the widget background.
            Image(
                provider = ImageProvider(
                    if (state.isDelayed) R.drawable.ic_bell_ring else R.drawable.ic_bell
                ),
                contentDescription = if (state.isDelayed) "Medicines overdue" else "On schedule",
                modifier = GlanceModifier.size(22.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(
                    if (state.isDelayed) DelayedBellColor else GlanceTheme.colors.onBackground
                )
            )
            Spacer(GlanceModifier.width(10.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_edit),
                contentDescription = "Edit medicines",
                modifier = GlanceModifier
                    .size(26.dp)
                    .clickable(
                        actionStartActivity(
                            ComponentName(context, MainActivity::class.java)
                        )
                    ),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onBackground)
            )
        }
    }

    @Composable
    private fun MedicineRow(slot: TimeSlot, medicine: Medicine) {
        val taken = medicine.isTaken
        val skipped = medicine.isSkipped
        val pending = !taken && !skipped

        val backgroundColor = when {
            taken -> GlanceTheme.colors.primaryContainer
            skipped -> GlanceTheme.colors.tertiaryContainer
            else -> GlanceTheme.colors.surfaceVariant
        }
        val contentColor = when {
            taken -> GlanceTheme.colors.onPrimaryContainer
            skipped -> GlanceTheme.colors.onTertiaryContainer
            else -> GlanceTheme.colors.onSurface
        }
        val iconRes = when {
            taken -> R.drawable.ic_check
            skipped -> R.drawable.ic_skip
            else -> null
        }

        // Outer Box provides spacing between cards; the card itself carries the background.
        Box(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .cornerRadius(16.dp)
                    .clickable(
                        actionRunCallback<ToggleMedicineAction>(
                            actionParametersOf(
                                SlotKey to slot.name,
                                MedicineIdKey to medicine.id,
                                ActionKey to "toggle"
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconRes != null) {
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = null,
                        modifier = GlanceModifier.size(18.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(contentColor)
                    )
                }
                Text(
                    text = medicine.name,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    ),
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(start = if (iconRes != null) 10.dp else 0.dp)
                )
                if (pending) {
                    Box(
                        modifier = GlanceModifier
                            .background(GlanceTheme.colors.tertiaryContainer)
                            .cornerRadius(8.dp)
                            .clickable(
                                actionRunCallback<ToggleMedicineAction>(
                                    actionParametersOf(
                                        SlotKey to slot.name,
                                        MedicineIdKey to medicine.id,
                                        ActionKey to "skip"
                                    )
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Skip",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onTertiaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

class TookItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TookItWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Ensure each widget instance has fresh state when the system updates it.
        kotlinx.coroutines.runBlocking {
            val manager = GlanceAppWidgetManager(context)
            appWidgetIds.forEach { appWidgetId ->
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                TookItWidget().refresh(context, glanceId)
            }
        }
    }
}

class ToggleMedicineAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val slotName = parameters[SlotKey] ?: return
        val medicineId = parameters[MedicineIdKey] ?: return
        val action = parameters[ActionKey] ?: "toggle"
        val slot = try {
            TimeSlot.valueOf(slotName)
        } catch (e: IllegalArgumentException) {
            return
        }

        val repo = MedicineRepository(context)
        when (action) {
            "skip" -> repo.skipMedicine(slot, medicineId)
            else -> repo.toggleMedicine(slot, medicineId)
        }

        // Write the new data into Glance's own state and refresh.
        TookItWidget().refresh(context, glanceId)
        vibrate(context)
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25)
        }
    }
}

private fun medicinesToJson(medicines: List<Medicine>): JSONArray {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    return JSONArray(medicines.map { med ->
        JSONObject().apply {
            put("id", med.id)
            put("name", med.name)
            put("isTaken", med.isTaken)
            put("isSkipped", med.isSkipped)
            med.takenAt?.let { put("takenAt", it.format(formatter)) }
        }
    })
}

private fun parseMedicines(json: String?): List<Medicine> {
    if (json.isNullOrBlank()) return emptyList()
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            try {
                val obj = array.getJSONObject(i)
                val taken = obj.optBoolean("isTaken", false)
                Medicine(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", ""),
                    isTaken = taken,
                    isSkipped = obj.optBoolean("isSkipped", false),
                    takenAt = if (taken) {
                        try {
                            LocalDateTime.parse(obj.optString("takenAt"), formatter)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                )
            } catch (e: Exception) {
                null
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
