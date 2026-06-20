# TookIt App - Architecture & Module Guide

> This doc explains what each major file/module does, in plain engineering terms. Android-specific details are minimized; data-model and visual-design specifics are skipped.

---

## 1. Big picture

TookIt has three main pieces:

1. **A configuration app** where you set up time slots and medicines.
2. **A home-screen widget** that shows the current slot's medicines and lets you tap to mark them taken or skipped.
3. **A scheduler** that resets everything once a day.

Data lives only on the device. The app and the widget share the same local storage.

---

## 2. Build configuration

### `build.gradle.kts` (project root)
- Declares which Gradle plugins and versions the project uses.
- Think of it as the top-level dependency/tooling manifest.

### `app/build.gradle.kts`
- Declares what libraries the app actually needs: Glance (widget framework), Compose (UI), DataStore (local storage), WorkManager (background jobs).
- Sets the minimum Android version (API 26) and target version (API 34).
- Enables Jetpack Compose.

**Important:** If you add a new library, it usually goes here.

---

## 3. Application entry point

### `TookItApplication.kt`
- The first class that runs when the app process starts.
- Configures WorkManager (the background-job engine) with basic logging.

**Why it exists:** WorkManager needs a central configuration point, and the custom `Application` class provides it.

---

## 4. Main screen & navigation

### `MainActivity.kt`
- The first screen Android opens when you tap the app icon.
- Sets up the daily-reset scheduler.
- Hosts the Compose UI and switches between two screens: **Setup** and **Log**.

**Important:** This is the root of the in-app experience. The widget shortcut also opens this activity.

---

## 5. Data layer

### `data/datastore/MedicineDataStore.kt`
- Reads and writes all user configuration and medicine history to local storage.
- Uses Jetpack DataStore, which is Android's modern replacement for `SharedPreferences`.
- Stores things as JSON strings inside key-value pairs.

**Important to know:**
- The app and the widget both read from this same storage.
- All writes go through DataStore's transaction API so concurrent changes don't corrupt the file.
- Old log entries are automatically removed after 30 days.

### `data/repository/MedicineRepository.kt`
- The middleman between the UI/widget and the raw storage.
- Exposes clean functions like `addMedicine`, `toggleMedicine`, `skipMedicine`, `getActiveSlot`, `getLog`.
- Handles business rules, e.g., "don't save empty names" and "pin the current slot until all medicines are done."

**Important to know:**
- The repository is created with an `Application` context so it survives configuration changes.
- `getActiveSlot` is where the "don't change slots based on time until done" behavior lives.

---

## 6. Home-screen widget

### `widget/TookItWidget.kt`
- Draws the home-screen widget and handles pill taps.
- Uses Jetpack Glance, a framework that lets you build widgets with Compose-like code. Under the hood, Glance converts your code into `RemoteViews` (the native Android widget format) and sends them to the launcher.

**Key parts inside this file:**
- `TookItWidget` class: defines the widget layout and how it loads data.
- `TookItWidgetReceiver`: listens for system events like "widget added" or "widget needs update."
- `ToggleMedicineAction`: the callback that runs when the user taps a pill.
- `refresh(context, glanceId)`: a helper that loads fresh data, writes it into Glance's own state, and asks the widget to redraw.

**Important to know:**
- Widgets cannot read your app's DataStore directly in a reactive way. Glance only re-renders when its **own internal state** changes.
- That is why `refresh()` writes a snapshot of the active slot and medicine list into Glance state before calling `update()`.
- Tapping a pill triggers `ToggleMedicineAction`, which updates the app data, then calls `refresh()` so the widget redraws.

### `widget/WidgetState.kt`
- A plain data holder for everything the widget is currently showing.
- Kept separate so the widget UI has a single, well-defined input.

### `widget/WidgetConstants.kt`
- Keys used to store values inside Glance's internal Preferences state.
- Like column names in a key-value table.

---

## 7. Background scheduling

### `worker/DailyResetWorker.kt`
- A background job that runs once a day at the user's chosen reset time.
- Clears all "taken" and "skipped" marks.
- Refreshes every widget instance so they show the reset state.
- Schedules the next day's reset before finishing.

**Important to know:**
- Uses WorkManager, which is the recommended way to run reliable background tasks on Android.
- The job survives reboots because `BootReceiver` re-schedules it.

### `worker/BootReceiver.kt`
- Listens for the phone finishing a reboot.
- Re-schedules the daily reset worker so it doesn't get lost.

**Important to know:**
- Requires the `RECEIVE_BOOT_COMPLETED` permission in the manifest.

---

## 8. In-app setup UI

### `ui/setup/SetupScreen.kt`
- The main configuration screen.
- Lets you enable/disable time slots, set slot start times, add/edit/delete medicines, and set the daily reset time.
- Shows a "Saved" confirmation after you tap Save.

**Important to know:**
- Medicine add/edit/delete is saved immediately; slot enable/time/reset-time changes are saved when you tap Save.
- Has a "Log" button in the top bar to open the history screen.

### `ui/setup/SetupViewModel.kt`
- Holds the screen's current state and coordinates actions.
- Talks to `MedicineRepository` to save changes.
- Refreshes the home-screen widget whenever you change configuration or medicines.

**Important to know:**
- Uses `viewModelScope` so all operations are coroutine-based and lifecycle-safe.

---

## 9. Medicine log UI

### `ui/log/LogScreen.kt`
- Shows a history of taken/skipped medicines, grouped by date.

### `ui/log/LogViewModel.kt`
- Loads the 30-day log from the repository and exposes it to the screen.

---

## 10. Manifest & resources

### `AndroidManifest.xml`
- Declares everything Android needs to know about the app:
  - The application class.
  - The main activity.
  - The widget receiver.
  - The boot receiver.
  - Required permissions (vibrate, receive boot completed).
  - WorkManager startup provider.

**Important to know:**
- If you add a new component (activity, receiver, service), it must be declared here or Android will ignore it.

### `res/xml/tookit_widget_info.xml`
- Widget metadata: minimum size, resize behavior, preview image, how often the system should auto-update it.

### `res/layout/widget_initial.xml`
- A simple placeholder layout shown while Glance is still loading the real widget content.

### `res/values/strings.xml`
- Central place for user-visible text like the app name and widget description.

---

## 11. Data flow summary

```
User taps pill on widget
        │
        ▼
ToggleMedicineAction
        │
        ├─ updates medicine state in app DataStore
        └─ calls TookItWidget.refresh()
                    │
                    ├─ reads fresh config from DataStore
                    ├─ writes widget snapshot into Glance state
                    └─ calls update() → widget redraws
```

```
User changes settings in app
        │
        ▼
SetupViewModel saves to DataStore
        │
        └─ calls refresh() on every widget instance
                    │
                    └─ each widget redraws with new data
```

```
DailyResetWorker fires
        │
        ├─ resets all taken/skipped states in DataStore
        └─ refreshes every widget instance
```

---

## 12. Key concepts if you're new to Android

- **Activity**: a single screen in an Android app.
- **Compose**: Android's modern UI toolkit; you describe UI with Kotlin functions instead of XML.
- **DataStore**: a key-value local storage system (like a typed file-backed map).
- **Glance**: a widget-building library that looks like Compose but produces widget-specific `RemoteViews`.
- **RemoteViews**: the format Android uses to display a widget inside the launcher app, which runs in a different process from your app.
- **WorkManager**: a scheduler for guaranteed background work, even if the app is closed.
- **Manifest**: the registry of app components and permissions.
- **Coroutines**: Kotlin's lightweight threads; used throughout for non-blocking I/O.
