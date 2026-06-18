package com.tookit.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tookit.app.data.model.Medicine
import com.tookit.app.data.model.TimeSlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onNavigateToSetup: () -> Unit,
    viewModel: TodayViewModel = viewModel()
) {
    val config by viewModel.config.collectAsState()
    val selected by viewModel.selectedSlot.collectAsState()

    val enabledSlots = TimeSlot.entries.filter { config.slots[it]?.enabled == true }
    val displaySlot = selected?.takeIf { it in enabledSlots } ?: viewModel.activeSlot(config)
    val medicines = config.slots[displaySlot]?.medicines ?: emptyList()

    val taken = medicines.count { it.isTaken }
    val skipped = medicines.count { it.isSkipped }
    val total = medicines.size
    val done = taken + skipped
    val remaining = total - done
    val progress = if (total > 0) done / total.toFloat() else 0f
    val allDone = total > 0 && remaining == 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Today", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (enabledSlots.isEmpty()) {
            EmptyToday(
                modifier = Modifier.fillMaxSize().padding(padding),
                title = "No time slots yet",
                message = "Enable a morning, evening, or night slot and add your medicines to get started.",
                onSetup = onNavigateToSetup
            )
            return@Scaffold
        }

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
            if (total > 0) {
                item {
                    ProgressHero(
                        slot = displaySlot,
                        taken = taken,
                        skipped = skipped,
                        remaining = remaining,
                        total = total,
                        progress = progress,
                        allDone = allDone
                    )
                }
            }

            if (enabledSlots.size > 1) {
                item {
                    SlotSelector(
                        slots = enabledSlots,
                        selected = displaySlot,
                        onSelect = viewModel::selectSlot
                    )
                }
            }

            if (medicines.isEmpty()) {
                item {
                    EmptyToday(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        title = "Nothing scheduled here",
                        message = "Add medicines for ${displaySlot.label.lowercase()} in Setup.",
                        onSetup = onNavigateToSetup
                    )
                }
            } else {
                item {
                    Text(
                        text = "MEDICINES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                items(medicines, key = { it.id }) { medicine ->
                    MedicineCard(
                        medicine = medicine,
                        onToggle = { viewModel.toggle(displaySlot, medicine.id) },
                        onSkip = { viewModel.skip(displaySlot, medicine.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressHero(
    slot: TimeSlot,
    taken: Int,
    skipped: Int,
    remaining: Int,
    total: Int,
    progress: Float,
    allDone: Boolean
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = slot.label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(18.dp))
            ProgressRing(
                progress = progress,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                progressColor = MaterialTheme.colorScheme.primary
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$taken",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "of $total taken",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = allDone,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "hero-status"
            ) { complete ->
                if (complete) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "All done! Nicely managed.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    Text(
                        text = buildString {
                            append("$remaining left")
                            if (skipped > 0) append(" · $skipped skipped")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    ringSize: Dp = 150.dp,
    stroke: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "progress-ring"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotSelector(
    slots: List<TimeSlot>,
    selected: TimeSlot,
    onSelect: (TimeSlot) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        slots.forEach { slot ->
            val isSelected = slot == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(slot) },
                label = { Text(slot.label) },
                shape = MaterialTheme.shapes.large,
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineCard(
    medicine: Medicine,
    onToggle: () -> Unit,
    onSkip: () -> Unit
) {
    val taken = medicine.isTaken
    val skipped = medicine.isSkipped
    val pending = !taken && !skipped

    val container by animateColorAsState(
        targetValue = when {
            taken -> MaterialTheme.colorScheme.primaryContainer
            skipped -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(300),
        label = "card-container"
    )
    val onContainer by animateColorAsState(
        targetValue = when {
            taken -> MaterialTheme.colorScheme.onPrimaryContainer
            skipped -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(300),
        label = "card-content"
    )

    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIndicator(taken = taken, skipped = skipped, outlineColor = onContainer)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicine.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                Text(
                    text = when {
                        taken -> "Taken"
                        skipped -> "Skipped"
                        else -> "Tap to mark as taken"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.7f)
                )
            }
            if (pending) {
                TextButton(onClick = onSkip) {
                    Text("Skip", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(taken: Boolean, skipped: Boolean, outlineColor: Color) {
    val fill = when {
        taken -> MaterialTheme.colorScheme.primary
        skipped -> MaterialTheme.colorScheme.tertiary
        else -> Color.Transparent
    }
    val box = Modifier
        .size(30.dp)
        .clip(CircleShape)
        .background(fill)
        .let {
            if (taken || skipped) it
            else it.border(2.dp, outlineColor.copy(alpha = 0.4f), CircleShape)
        }

    Box(modifier = box, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = when {
                taken -> 1
                skipped -> 2
                else -> 0
            },
            transitionSpec = { (fadeIn(tween(200)) + scaleIn(tween(200))) togetherWith fadeOut(tween(120)) },
            label = "status-icon"
        ) { state ->
            when (state) {
                1 -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Taken",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                2 -> Icon(
                    Icons.Filled.Close,
                    contentDescription = "Skipped",
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(18.dp)
                )
                else -> Spacer(Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyToday(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    onSetup: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Medication,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSetup, shape = MaterialTheme.shapes.large) {
            Text("Go to Setup")
        }
    }
}
