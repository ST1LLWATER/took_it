# TookIt

A lightweight, privacy-first Android medicine reminder built around a tappable home-screen widget. Configure your morning, evening, and night medicines once, then mark each dose as taken directly from the widget - no need to open the app.

## Features

- **Home-screen widget** showing the current time slot's medicines with pill-shaped toggle buttons.
- **Tap to mark taken or skipped** - the widget updates instantly and shows your progress.
- **Three configurable time slots** - Morning, Evening, and Night with custom start times.
- **Daily auto-reset** - all taken/skipped marks clear at a time you choose (default: 04:00).
- **Medicine log** - review the last 30 days of taken and skipped medicines inside the app.
- **Works offline** - all data stays on the device.
- **Survives reboots** - the daily reset is re-scheduled automatically after the device restarts.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI Toolkit | Jetpack Compose + Material 3 |
| Widget | Jetpack Glance (AppWidgetProvider) |
| Local Storage | Jetpack DataStore (Preferences) |
| Background Work | WorkManager |
| Build Tool | Gradle 8.2 + Android Gradle Plugin 8.2.2 |

**Requirements:** Android 8.0+ (API 26), compiled against API 34.

## Getting Started

1. Clone the repository:
   ```bash
   git clone git@github.com:ST1LLWATER/took_it.git
   cd took_it
   ```

2. Open the project in Android Studio (latest stable version recommended).

3. Sync Gradle and run the `app` configuration on an emulator or physical device.

> **Note:** The release build references a local signing keystore at `app/keystore/tookit-release.keystore`, which is excluded from Git. For local debug builds, Android Studio uses its default debug keystore automatically.

## Architecture

The app follows a straightforward layered architecture:

```
UI (Compose Screens + ViewModels)
        │
        ▼
Repository (MedicineRepository)
        │
        ▼
DataStore (MedicineDataStore)
```

- **`ui/`** - Setup screen, log screen, and their ViewModels.
- **`widget/`** - Jetpack Glance widget, widget state, and tap actions.
- **`data/repository/`** - Business logic and clean API for the UI/widget.
- **`data/datastore/`** - JSON-based local persistence using DataStore.
- **`worker/`** - Daily reset worker and boot receiver.

The app and the widget share the same DataStore, so state is always consistent. When a pill is tapped on the widget, the action updates DataStore and then refreshes the widget snapshot.

## Permissions

- `RECEIVE_BOOT_COMPLETED` - re-schedules the daily reset worker after a reboot.
- `VIBRATE` - provides haptic feedback when toggling medicines.

## Privacy

- No internet permission is requested.
- No analytics, ads, or cloud sync.
- All medicine data is stored locally on the device.

## Project Structure

```
TookIT/
├── app/src/main/java/com/tookit/app/
│   ├── data/          # Models, DataStore, and repository
│   ├── ui/            # Compose screens and ViewModels
│   ├── widget/        # Home-screen widget
│   ├── worker/        # Background reset + boot receiver
│   └── MainActivity.kt / TookItApplication.kt
├── docs/              # Specification, architecture guide, and issue notes
└── README.md
```

## Documentation

- [`docs/spec.md`](docs/spec.md) - Original product specification.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - Plain-language guide to every module.
- [`docs/WIDGET_UPDATE_ISSUE.md`](docs/WIDGET_UPDATE_ISSUE.md) - Widget refresh issue analysis and fix.

## License

Free to use, but redistribution or distribution is not permitted without explicit permission.
