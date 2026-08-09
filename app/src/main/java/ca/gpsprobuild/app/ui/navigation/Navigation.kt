package ca.gpsprobuild.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import ca.gpsprobuild.app.domain.model.DeviceRole

sealed class Destination(val route: String) {
    data object Dashboard : Destination("dashboard")
    data object Jobs : Destination("jobs")
    data object Customers : Destination("customers")
    data object Schedule : Destination("schedule")
    data object More : Destination("more")
}

data class TabItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector
)

/**
 * A field device has no reason to browse the full customer book, so that tab is
 * absent rather than disabled. Hiding capability behind a greyed-out control just
 * invites people to poke at it.
 */
fun tabsFor(role: DeviceRole): List<TabItem> = when (role) {
    DeviceRole.OWNER -> listOf(
        TabItem(Destination.Dashboard, "Home", Icons.Filled.Dashboard),
        TabItem(Destination.Jobs, "Jobs", Icons.Filled.Construction),
        TabItem(Destination.Customers, "Customers", Icons.Filled.People),
        TabItem(Destination.Schedule, "Schedule", Icons.Filled.CalendarMonth),
        TabItem(Destination.More, "More", Icons.Filled.MoreHoriz)
    )
    DeviceRole.FIELD -> listOf(
        TabItem(Destination.Dashboard, "Home", Icons.Filled.Dashboard),
        TabItem(Destination.Jobs, "My jobs", Icons.Filled.Construction),
        TabItem(Destination.Schedule, "Schedule", Icons.Filled.CalendarMonth),
        TabItem(Destination.More, "More", Icons.Filled.MoreHoriz)
    )
}

@Composable
fun GpsBottomBar(navController: NavHostController, role: DeviceRole) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        tabsFor(role).forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}
