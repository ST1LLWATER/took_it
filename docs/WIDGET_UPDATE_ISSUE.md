# TookIt Widget Update Issue - Root Cause & Fix

## Problem

Tapping a medicine pill on the TookIt home-screen widget updated the underlying app data (the medicine became `taken`/`skipped` and the change was visible inside the app), but the widget itself did not refresh. The pill stayed in its previous visual state.

## Root Cause

Jetpack Glance widgets do **not** observe external data sources such as Jetpack DataStore, Room, or in-memory flows. A Glance widget renders from its **own internal state** (`GlanceStateDefinition`), converts that composition into `RemoteViews`, and sends the result to the launcher process.

When the user taps a pill:

1. The `ActionCallback` runs and updates our app DataStore.
2. The callback calls `TookItWidget().update(context, glanceId)`.
3. Glance checks whether its **internal** state changed.
4. If Glance detects no internal state change, it optimizes away the update and `provideGlance` is not re-run.
5. The widget continues to display the old RemoteViews.

This is a documented Glance behavior. Community references confirm that calling `update()` alone is often insufficient:

> *"Updating widgets in Jetpack Glance is a bit more tricky, widgets are only updated when their state changes, therefore simple update will not refresh them. To update them, you have to fake state update."*

## Solution

Store a complete snapshot of the widget's visible data inside Glance's own state (`PreferencesGlanceStateDefinition`) and rewrite that snapshot every time the data changes.

### Architecture

```
App DataStore (source of truth)
        │
        │ on tap / save / reset
        ▼
TookItWidget.refresh(context, glanceId)
        │
        ├─ reads latest AppConfig
        ├─ computes active slot + medicine list + progress
        ├─ writes snapshot into Glance Preferences state
        └─ calls TookItWidget().update(context, glanceId)
                                │
                                ▼
                      provideGlance() runs again
                                │
                                ▼
                      reads Glance state → renders fresh RemoteViews
```

### Implementation Details

#### 1. Widget state definition

The widget declares that it uses Glance Preferences state:

```kotlin
class TookItWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    // ...
}
```

#### 2. Widget state keys

Keys used to store the render snapshot in Glance Preferences:

```kotlin
internal val WidgetSlotKey = stringPreferencesKey("tookit_widget_slot")
internal val WidgetMedicinesKey = stringPreferencesKey("tookit_widget_medicines")
internal val WidgetProgressKey = stringPreferencesKey("tookit_widget_progress")
internal val WidgetLastUpdatedKey = longPreferencesKey("tookit_widget_last_updated")
```

#### 3. Central refresh helper

`TookItWidget.refresh(context, glanceId)` is the single place that:

- Loads the latest configuration from the app DataStore.
- Resolves the active time slot (including pin-slot logic).
- Serializes the visible medicines and progress into Glance Preferences.
- Triggers `update()` so Glance re-runs `provideGlance`.

```kotlin
suspend fun refresh(context: Context, glanceId: GlanceId) {
    val repo = MedicineRepository(context)
    val config = repo.getConfig()
    val activeSlot = repo.getActiveSlot(config)
    val medicines = config.slots[activeSlot]?.medicines ?: emptyList()
    val takenCount = medicines.count { it.isTaken }
    val skippedCount = medicines.count { it.isSkipped }

    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[WidgetSlotKey] = activeSlot.name
        prefs[WidgetMedicinesKey] = medicinesToJson(medicines).toString()
        prefs[WidgetProgressKey] = "${takenCount}|${skippedCount}|${medicines.size}"
        prefs[WidgetLastUpdatedKey] = System.currentTimeMillis()
    }
    update(context, glanceId)
}
```

#### 4. Reading state in the composition

`provideGlance` reads the Glance state and passes the immutable snapshot to the UI composables:

```kotlin
override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
        val prefs = currentState<Preferences>()
        val widgetState = rememberWidgetState(prefs)

        GlanceTheme {
            WidgetContent(widgetState)
        }
    }
}
```

Because `currentState<Preferences>()` is called, Glance registers this composition as a consumer of the widget state. When `updateAppWidgetState` changes any key, Glance detects the state change and re-runs the composition.

#### 5. ActionCallback uses `refresh()`

After toggling a medicine, the callback refreshes the widget from the updated app data:

```kotlin
class ToggleMedicineAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // ... resolve slot & medicine id ...
        val repo = MedicineRepository(context)
        when (action) {
            "skip" -> repo.skipMedicine(slot, medicineId)
            else -> repo.toggleMedicine(slot, medicineId)
        }

        TookItWidget().refresh(context, glanceId)
        vibrate(context)
    }
}
```

#### 6. App changes and scheduled resets also refresh

All other paths that mutate medicine state call the same `refresh()` helper:

- `SetupViewModel` after add / edit / delete / save.
- `DailyResetWorker` after resetting taken/skipped states.
- `TookItWidgetReceiver.onUpdate()` when the system requests a widget update.

## Files Involved

| File | Role |
|------|------|
| `app/src/main/java/com/tookit/app/widget/TookItWidget.kt` | Widget UI, `refresh()` helper, `ActionCallback`, `GlanceAppWidgetReceiver` |
| `app/src/main/java/com/tookit/app/widget/WidgetState.kt` | Immutable snapshot of widget-visible data |
| `app/src/main/java/com/tookit/app/widget/WidgetConstants.kt` | Keys for Glance Preferences state |
| `app/src/main/java/com/tookit/app/ui/setup/SetupViewModel.kt` | Refreshes widgets after configuration changes |
| `app/src/main/java/com/tookit/app/worker/DailyResetWorker.kt` | Refreshes widgets after daily reset |

## Lessons

1. **Don't rely on `GlanceAppWidget.update()` alone** when the data lives outside Glance state. Always pair it with an actual state change.
2. **Glance compositions cannot observe external flows/DataStore**. All data the widget displays should either be loaded inside `provideGlance` from Glance state, or refreshed explicitly by callers.
3. **Keep a single `refresh()` helper** that both writes Glance state and calls `update()`. This avoids duplicating the pattern across callbacks, ViewModels, and workers.
