# TookIt - Medicine Reminder Widget Product Specification

> **Goal:** Build a simple, tappable Android home-screen widget that helps you remember which medicines you have already taken for the current time slot (Morning / Evening / Night) and which are still pending.
>
> **Working app name:** *TookIt* (can be finalized later).

---

## 1. Problem Statement

You take multiple medicines, and several of them often need to be taken together (e.g., all morning medicines). Because there are multiple medicines per time slot, it is easy to:

- Forget which medicines you have already taken.
- Lose track of whether the current dose was completed.
- Miss a medicine entirely or accidentally take it twice.

Existing alarm/reminder apps usually only notify you once. They do not give you a persistent, at-a-glance checklist on the home screen that you can tap to mark each medicine as taken.

### User Story

> *As a user, I want a single widget on my home screen that shows the medicines for the current time slot, lets me tap each one to mark it taken, and resets automatically for the next day.*

---

## 2. Widget Overview

| Item | Description |
|------|-------------|
| **Widget Type** | Android home-screen widget (AppWidgetProvider) |
| **Configuration** | One-time setup screen in the app |
| **Time Slots** | Morning, Evening, Night (user can enable/disable each) |
| **Input per Slot** | List of medicine names (free text, no quantity) |
| **Main Interaction** | Tap a pill-shaped button to toggle “taken / not taken” |
| **State Reset** | Automatic daily reset at a configurable time |

---

## 3. Core Features & Completion Checkboxes

### 3.1 Configuration (In-App Setup)

- [ ] **Time Slot Enablement**
    - [ ] User can toggle Morning slot on/off.
    - [ ] User can toggle Evening slot on/off.
    - [ ] User can toggle Night slot on/off.
    - [ ] At least one slot must be enabled before saving.

- [ ] **Medicine List per Slot**
    - [ ] For each enabled slot, user can add one or more medicine names.
    - [ ] Medicine name is plain text (no quantity field required).
    - [ ] User can edit an existing medicine name.
    - [ ] User can delete a medicine from the list.
    - [ ] Empty medicine names are not allowed.

- [ ] **Slot Timing**
    - [ ] Each slot has a default start time (Morning = 06:00, Evening = 16:00, Night = 20:00).
    - [ ] User can customize the start time for each enabled slot.
    - [ ] Widget automatically detects the active slot based on current time.

- [ ] **Daily Reset Time**
    - [ ] User can set a daily reset time (default = 04:00).
    - [ ] All “taken” marks clear automatically after the reset time.

- [ ] **Persistence**
    - [ ] Configuration is saved locally (e.g., SharedPreferences / DataStore).
    - [ ] Configuration survives app updates and device reboots.

---

### 3.2 Widget Display

- [ ] **Active Slot Detection**
    - [ ] Widget shows the medicines for the current active time slot.
    - [ ] If current time is before the first slot, show the first slot.
    - [ ] If current time is after the last slot, show the last slot until daily reset.
    - [ ] Show the slot name clearly (e.g., “Morning Medicines”).

- [ ] **Medicine Buttons**
    - [ ] Each medicine is shown as a rounded pill-shaped button.
    - [ ] Pill displays the medicine name.
    - [ ] Pills wrap to multiple lines if there are many medicines.
    - [ ] Pending pill: neutral background (e.g., light gray / white outline).
    - [ ] Taken pill: filled/colored background (e.g., green or accent color) with a checkmark icon.

- [ ] **Progress Summary**
    - [ ] Widget header shows “X of Y taken” or a small progress bar.
    - [ ] When all medicines in the slot are taken, show a completion message (e.g., “All done! 🎉”).

- [ ] **Empty State**
    - [ ] If no medicines are configured for the active slot, show a friendly empty state like “No medicines for this slot.”

- [ ] **Last Updated / Timestamp**
    - [ ] Optionally show the last marked time for the most recent medicine (e.g., “Taken at 8:15 AM”).

---

### 3.3 Widget Interaction

- [ ] **Tap to Toggle**
    - [ ] Tapping a pending pill marks it as taken.
    - [ ] Tapping a taken pill marks it as not taken (undo).
    - [ ] Widget updates immediately after the tap.

- [ ] **Haptic Feedback**
    - [ ] Short vibration or haptic feedback on toggle.

- [ ] **Open App**
    - [ ] Tapping the widget header (or an overflow icon) opens the app for editing configuration.

- [ ] **Manual Refresh**
    - [ ] Widget refreshes when time slot changes.
    - [ ] Widget refreshes after daily reset.

---

### 3.4 Automatic Behaviors

- [ ] **Daily Reset**
    - [ ] All taken states reset at the configured reset time every day.
    - [ ] Reset uses device time (no internet required).

- [ ] **Slot Transition**
    - [ ] Widget content updates when the current time crosses into a new slot.
    - [ ] A transition notification or subtle animation is optional.

- [ ] **Boot Persistence**
    - [ ] Widget state and reset alarm survive device reboot.

---

### 3.5 Settings & Preferences

- [ ] **Notification Reminder (Optional)**
    - [ ] Optional notification when a new slot starts if any medicine is still pending.
    - [ ] User can disable notifications.

- [ ] **Color Theme**
    - [ ] User can choose accent color for “taken” pills.
    - [ ] Light and dark theme support.

- [ ] **Widget Size**
    - [ ] Support at least 4×1 and 4×2 widget sizes.
    - [ ] Pills reflow based on available width.

---

## 4. Non-Functional Requirements

- [ ] **Privacy**
    - [ ] No medicine data leaves the device.
    - [ ] No cloud sync, no analytics, no ads.

- [ ] **Performance**
    - [ ] Widget updates in under 200 ms on tap.
    - [ ] No background service running continuously; use alarms or WorkManager.

- [ ] **Accessibility**
    - [ ] Medicine pills have content descriptions.
    - [ ] Sufficient color contrast for taken/pending states.
    - [ ] Supports system font sizes.

- [ ] **Reliability**
    - [ ] Handles device reboot without losing state.
    - [ ] Handles timezone changes gracefully.

---

## 5. Design Guidelines for UI/AI Designer

### 5.1 Visual Style

| Element | Guideline |
|---------|-----------|
| **Shape** | Pill-shaped buttons with large corner radius (fully rounded caps). |
| **Typography** | Clean, rounded sans-serif font; medicine name centered inside pill. |
| **Pending State** | White or very light gray fill, subtle border, dark text. |
| **Taken State** | Filled with accent color (suggested: soft green #4CAF50 or teal), white text, small checkmark on the left or right. |
| **Background** | Widget background should be a soft card with rounded corners and slight shadow (follow Material You / Android 12 widget guidelines). |
| **Header** | Slot name in bold, progress text smaller beneath it. |
| **Spacing** | Comfortable padding between pills (≥ 8 dp) and around widget edges (≥ 16 dp). |

### 5.2 Layout

- **Header Row:** Slot title on the left, progress / “All done!” on the right.
- **Body:** Flow layout of pills, wrapping horizontally.
- **Footer (optional):** Small “Edit” icon that opens the app.

### 5.3 Interaction Design

- Pills should feel like physical buttons: tap gives immediate color fill.
- Use scale or fade animation on toggle if technically feasible.
- Completion state should feel rewarding: subtle celebration icon or color shift.

### 5.4 Color & Theme

- Follow Material You dynamic colors on Android 12+.
- Provide a fallback static accent color for older devices.
- Ensure taken/pending difference is distinguishable for color-blind users (use checkmark icon + border change, not only color).

### 5.5 Widget Preview

- Provide a widget preview in the system picker showing:
    - One pending pill
    - One taken pill
    - Header with sample slot name

---

## 6. Tech Stack & Technical Notes

### Selected Stack: Native Android (Kotlin)

We are building TookIt as a native Android application so that the home-screen widget is first-class, reliable, and maintainable.

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Language** | Kotlin | Entire app and widget logic. |
| **In-App UI** | Jetpack Compose + Material 3 | Configuration screens, settings, and onboarding. |
| **Home-Screen Widget** | Jetpack Glance (AppWidgetProvider) | Modern, Compose-style widget API. Fallback to `RemoteViews` only if Glance limitations block a required feature. |
| **State / Config Storage** | Jetpack DataStore | Persist medicine lists, slot timings, taken states, and user preferences. |
| **Scheduling** | WorkManager | Daily reset and slot-transition widget updates. |
| **Optional Reminders** | AlarmManager + notifications | Exact-time nudges when a new slot starts. |
| **Theming** | Material You / Dynamic Color | Automatic light/dark palettes and accent colors on Android 12+. |

### Why Not React Native?

- Android home-screen widgets cannot be rendered from JavaScript.
- They must be implemented natively (`RemoteViews` or Glance) and run in the launcher process.
- A React Native app would still require a full native widget module plus a bridge for shared data, adding complexity without benefit for this widget-first project.

### Implementation Notes

- Use Glance `ActionParameters` to identify which medicine pill was tapped.
- Keep widget state in DataStore so the app UI and widget read the same source of truth.
- Avoid a continuously running background service; schedule updates only at slot boundaries and the daily reset time.
- Support at least Android 8 (API 26) and target the latest stable SDK.

---

## 7. Acceptance Criteria

- [ ] User can complete initial setup in under 60 seconds.
- [ ] Widget displays the correct medicines for the current time slot.
- [ ] Tapping a pill immediately toggles its taken state and updates progress.
- [ ] All taken marks reset automatically every day.
- [ ] Configuration persists across reboots.
- [ ] Widget is readable and tappable on common phone screen sizes.

---

## 8. Future Enhancements (Out of Scope for v1)

- Snooze / delayed reminder notifications.
- Multiple widget instances with different configs.
- History/log of taken medicines.
- Cloud backup.
- Wear OS companion tile.

---

*Spec version: 1.0*
