package com.tookit.app.ui.setup

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tookit.app.data.model.Medicine
import com.tookit.app.data.model.SlotConfig
import com.tookit.app.data.model.TimeSlot
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val config by viewModel.config.collectAsState()
    val saved by viewModel.saved.collectAsState()
    var showValidationError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(saved) {
        if (saved) {
            snackbarHostState.showSnackbar("Changes saved")
            viewModel.onSaveAcknowledged()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Setup", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    FilledTonalButton(
                        onClick = {
                            val enabledCount = TimeSlot.entries.count { config.slots[it]?.enabled == true }
                            if (enabledCount == 0) {
                                showValidationError = true
                            } else {
                                scope.launch { viewModel.save() }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Set up your time slots, medicines, and the daily reset.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                DailyResetCard(
                    time = config.dailyResetTime,
                    onTimeSelected = { viewModel.updateResetTime(it) }
                )
            }

            item {
                Text(
                    text = "TIME SLOTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            items(TimeSlot.entries.size) { index ->
                val slot = TimeSlot.entries[index]
                SlotConfigCard(
                    slot = slot,
                    slotConfig = config.slots[slot] ?: SlotConfig(),
                    onEnabledChanged = { viewModel.setSlotEnabled(slot, it) },
                    onTimeChanged = { viewModel.setSlotTime(slot, it) },
                    onAddMedicine = { viewModel.addMedicine(slot, it) },
                    onUpdateMedicine = { id, name -> viewModel.updateMedicine(slot, id, name) },
                    onDeleteMedicine = { viewModel.deleteMedicine(slot, it) }
                )
            }
        }
    }

    if (showValidationError) {
        AlertDialog(
            onDismissRequest = { showValidationError = false },
            icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
            title = { Text("Enable a time slot") },
            text = { Text("Please enable at least one time slot before saving.") },
            confirmButton = {
                TextButton(onClick = { showValidationError = false }) { Text("Got it") }
            }
        )
    }
}

@Composable
private fun DailyResetCard(
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily reset",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "All marks clear at this time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            TimeButton(time = time, onTimeSelected = onTimeSelected)
        }
    }
}

@Composable
private fun SlotConfigCard(
    slot: TimeSlot,
    slotConfig: SlotConfig,
    onEnabledChanged: (Boolean) -> Unit,
    onTimeChanged: (LocalTime) -> Unit,
    onAddMedicine: (String) -> Unit,
    onUpdateMedicine: (String, String) -> Unit,
    onDeleteMedicine: (String) -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (slotConfig.enabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        animationSpec = tween(300),
        label = "slot-container"
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = slot.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (slotConfig.enabled) "${slotConfig.medicines.size} medicine(s)" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = slotConfig.enabled, onCheckedChange = onEnabledChanged)
            }

            AnimatedVisibility(visible = slotConfig.enabled) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Starts at",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TimeButton(time = slotConfig.startTime, onTimeSelected = onTimeChanged)
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))

                    slotConfig.medicines.forEach { medicine ->
                        MedicineRow(
                            medicine = medicine,
                            onUpdate = onUpdateMedicine,
                            onDelete = onDeleteMedicine
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    AddMedicineRow(onAdd = onAddMedicine, slotKey = slot)
                }
            }
        }
    }
}

@Composable
private fun MedicineRow(
    medicine: Medicine,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var isEditing by remember(medicine.id) { mutableStateOf(false) }
    var editedName by remember(medicine.id) { mutableStateOf(medicine.name) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onUpdate(medicine.id, editedName)
                    isEditing = false
                }) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Save name",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    text = medicine.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { isEditing = true }) {
                    Icon(
                        Icons.Filled.EditNote,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(medicine.id) }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMedicineRow(
    onAdd: (String) -> Unit,
    slotKey: TimeSlot
) {
    var newMedicine by remember(slotKey) { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newMedicine,
            onValueChange = { newMedicine = it },
            label = { Text("Add a medicine") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        FilledTonalIconButton(
            onClick = {
                if (newMedicine.isNotBlank()) {
                    onAdd(newMedicine)
                    newMedicine = ""
                }
            },
            modifier = Modifier.size(52.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add medicine")
        }
    }
}

@Composable
private fun TimeButton(
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit
) {
    val context = LocalContext.current
    val formatter = DateTimeFormatter.ofPattern("hh:mm a")
    FilledTonalButton(
        onClick = { showTimePicker(context, time, onTimeSelected) },
        shape = MaterialTheme.shapes.large
    ) {
        Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(time.format(formatter))
    }
}

private fun showTimePicker(
    context: Context,
    current: LocalTime,
    onTimeSelected: (LocalTime) -> Unit
) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onTimeSelected(LocalTime.of(hour, minute)) },
        current.hour,
        current.minute,
        false
    ).show()
}
