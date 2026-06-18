package com.tookit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import com.tookit.app.ui.log.LogScreen
import com.tookit.app.ui.setup.SetupScreen
import com.tookit.app.ui.theme.TookITTheme
import com.tookit.app.ui.today.TodayScreen
import com.tookit.app.worker.DailyResetWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            DailyResetWorker.schedule(this@MainActivity)
        }

        setContent {
            TookITTheme {
                TookItApp()
            }
        }
    }
}

private enum class Destination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY("Today", Icons.Filled.Today, Icons.Outlined.Today),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    SETUP("Setup", Icons.Filled.Tune, Icons.Outlined.Tune)
}

@Composable
private fun TookItApp() {
    var selectedIndex by rememberSaveable { mutableIntStateOf(Destination.TODAY.ordinal) }
    val destinations = Destination.entries

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                destinations.forEach { destination ->
                    val selected = destination.ordinal == selectedIndex
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedIndex = destination.ordinal },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Only consume the bottom (nav-bar) inset here; each screen's own
                // top app bar handles the status-bar inset, avoiding double padding.
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            Crossfade(
                targetState = selectedIndex,
                animationSpec = tween(250),
                label = "screen-crossfade"
            ) { index ->
                when (destinations[index]) {
                    Destination.TODAY -> TodayScreen(
                        onNavigateToSetup = { selectedIndex = Destination.SETUP.ordinal }
                    )
                    Destination.HISTORY -> LogScreen()
                    Destination.SETUP -> SetupScreen()
                }
            }
        }
    }
}
