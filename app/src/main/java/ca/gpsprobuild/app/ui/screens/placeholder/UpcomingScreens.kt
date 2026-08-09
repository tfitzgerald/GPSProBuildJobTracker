package ca.gpsprobuild.app.ui.screens.placeholder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import ca.gpsprobuild.app.ui.components.EmptyState

/**
 * Foundation-build stand-ins. Each names the screen that lands here and what it
 * will hold, rather than saying "coming soon" — the person installing this build
 * should be able to tell what is finished and what is not.
 */

@Composable
fun JobsPlaceholder() = EmptyState(
    icon = Icons.Filled.Construction,
    title = "Jobs",
    message = "Job list, board view and the nine-tab job detail arrive in step 3. " +
        "The database behind them is already in place."
)

@Composable
fun CustomersPlaceholder() = EmptyState(
    icon = Icons.Filled.People,
    title = "Customers",
    message = "Customer list, detail and the call / text / email / directions row " +
        "arrive in step 2."
)

@Composable
fun SchedulePlaceholder() = EmptyState(
    icon = Icons.Filled.CalendarMonth,
    title = "Schedule",
    message = "Agenda, week and month views arrive in step 9, once there are " +
        "appointments and job dates to show."
)

@Composable
fun MorePlaceholder() = EmptyState(
    icon = Icons.Filled.Settings,
    title = "More",
    message = "Crew, buy list, suppliers, reports, sync and settings live here."
)
