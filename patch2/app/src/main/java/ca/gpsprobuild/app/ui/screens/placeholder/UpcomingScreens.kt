package ca.gpsprobuild.app.ui.screens.placeholder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.Composable
import ca.gpsprobuild.app.ui.components.EmptyState

/**
 * Foundation-build stand-ins. Each names the screen that lands here and what it
 * will hold, rather than saying "coming soon" — the person installing this build
 * should be able to tell what is finished and what is not.
 */

@Composable
fun SchedulePlaceholder() = EmptyState(
    icon = Icons.Filled.CalendarMonth,
    title = "Schedule",
    message = "Agenda, week and month views arrive in step 9, once there are " +
        "appointments and job dates to show."
)

