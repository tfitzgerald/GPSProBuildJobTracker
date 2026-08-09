package ca.gpsprobuild.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.ui.navigation.Destination
import ca.gpsprobuild.app.ui.navigation.GpsBottomBar
import ca.gpsprobuild.app.ui.screens.dashboard.DashboardScreen
import ca.gpsprobuild.app.ui.screens.placeholder.CustomersPlaceholder
import ca.gpsprobuild.app.ui.screens.placeholder.JobsPlaceholder
import ca.gpsprobuild.app.ui.screens.placeholder.MorePlaceholder
import ca.gpsprobuild.app.ui.screens.placeholder.SchedulePlaceholder
import ca.gpsprobuild.app.ui.screens.setup.SetupScreen
import ca.gpsprobuild.app.ui.theme.GpsProBuildTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)

            // Hold the splash window until settings resolve, so the app never
            // flashes the setup screen at someone who already finished it.
            val resolved = settings ?: return@setContent

            GpsProBuildTheme(
                themeMode = resolved.themeMode,
                privacyMode = resolved.effectivePrivacyMode
            ) {
                if (!resolved.setupComplete) {
                    SetupScreen(onSetupComplete = { /* settings flow re-emits and swaps the tree */ })
                } else {
                    MainShell(role = resolved.deviceRole)
                }
            }
        }
    }
}

@Composable
private fun MainShell(role: ca.gpsprobuild.app.domain.model.DeviceRole) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { GpsBottomBar(navController, role) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Dashboard.route) { DashboardScreen() }
            composable(Destination.Jobs.route) { JobsPlaceholder() }
            composable(Destination.Customers.route) { CustomersPlaceholder() }
            composable(Destination.Schedule.route) { SchedulePlaceholder() }
            composable(Destination.More.route) { MorePlaceholder() }
        }
    }
}
