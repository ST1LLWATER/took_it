package com.tookit.app.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal val WidgetSlotKey = stringPreferencesKey("tookit_widget_slot")
internal val WidgetMedicinesKey = stringPreferencesKey("tookit_widget_medicines")
internal val WidgetProgressKey = stringPreferencesKey("tookit_widget_progress")
internal val WidgetLastUpdatedKey = longPreferencesKey("tookit_widget_last_updated")
internal val WidgetDelayedKey = booleanPreferencesKey("tookit_widget_is_delayed")
