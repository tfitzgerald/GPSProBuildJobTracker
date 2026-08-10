package ca.gpsprobuild.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.domain.model.DeviceRole
import ca.gpsprobuild.app.ui.navigation.Destination
import ca.gpsprobuild.app.ui.navigation.GpsBottomBar
import ca.gpsprobuild.app.ui.screens.customers.CustomerDetailScreen
import ca.gpsprobuild.app.ui.screens.customers.CustomerEditScreen
import ca.gpsprobuild.app.ui.screens.customers.CustomerListScreen
import ca.gpsprobuild.app.ui.screens.dashboard.DashboardScreen
import ca.gpsprobuild.app.ui.screens.jobs.JobDetailScreen
import ca.gpsprobuild.app.ui.screens.jobs.JobEditScreen
import ca.gpsprobuild.app.ui.screens.jobs.JobListScreen
import ca.gpsprobuild.app.ui.screens.materials.BuyListScreen
import ca.gpsprobuild.app.ui.screens.more.MoreScreen
import ca.gpsprobuild.app.ui.screens.staff.CrewEditScreen
import ca.gpsprobuild.app.ui.screens.staff.CrewListScreen
import ca.gpsprobuild.app.ui.screens.schedule.ScheduleScreen
import ca.gpsprobuild.app.ui.screens.settings.SettingsScreen
import ca.gpsprobuild.app.ui.screens.setup.SetupScreen
import ca.gpsprobuild.app.ui.theme.GpsProBuildTheme
import ca.gpsprobuild.app.ui.theme.blueprintBackground
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

            // Wait for settings before drawing anything, so the app never flashes
            // the setup screen at someone who already finished it.
            val resolved = settings ?: return@setContent

            GpsProBuildTheme(
                themeMode = resolved.themeMode,
                privacyMode = resolved.effectivePrivacyMode
            ) {
                if (!resolved.setupComplete) {
                    SetupScreen(onSetupComplete = { /* settings re-emits and swaps the tree */ })
                } else {
                    MainShell(role = resolved.deviceRole)
                }
            }
        }
    }
}

/**
 * Routes are plain strings with path arguments. Type-safe serializable routes are
 * tidier but add a moving part, and navigation here is shallow enough that the
 * trade is not worth it.
 */
private object Routes {
    const val CUSTOMER_DETAIL = "customer/{customerId}"
    const val CUSTOMER_EDIT = "customer/{customerId}/edit"
    const val CUSTOMER_NEW = "customer/new"

    const val JOB_DETAIL = "job/{jobId}"
    const val JOB_EDIT = "job/{jobId}/edit"
    const val JOB_NEW = "job/new"
    const val JOB_NEW_FOR_CUSTOMER = "job/new/{customerId}"

    fun customerDetail(id: Long) = "customer/$id"
    fun customerEdit(id: Long) = "customer/$id/edit"
    fun jobDetail(id: Long) = "job/$id"
    fun jobEdit(id: Long) = "job/$id/edit"
    fun jobNewForCustomer(customerId: Long) = "job/new/$customerId"

    const val CREW = "crew"
    const val CREW_NEW = "crew/new"
    const val CREW_EDIT = "crew/{staffId}/edit"
    const val BUY_LIST = "buylist"
    const val SETTINGS = "settings"

    fun crewEdit(id: Long) = "crew/$id/edit"
}

@Composable
private fun MainShell(role: DeviceRole) {
    val navController = rememberNavController()

    Box(Modifier.fillMaxSize().blueprintBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { GpsBottomBar(navController, role) }
        ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Dashboard.route) { DashboardScreen() }

            // --- Customers -------------------------------------------------
            composable(Destination.Customers.route) {
                CustomerListScreen(
                    onOpenCustomer = { navController.navigate(Routes.customerDetail(it)) },
                    onAddCustomer = { navController.navigate(Routes.CUSTOMER_NEW) }
                )
            }
            composable(
                route = Routes.CUSTOMER_DETAIL,
                arguments = listOf(navArgument("customerId") { type = NavType.StringType })
            ) {
                CustomerDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.customerEdit(it)) },
                    onOpenJob = { navController.navigate(Routes.jobDetail(it)) },
                    onAddJob = { navController.navigate(Routes.jobNewForCustomer(it)) }
                )
            }
            composable(Routes.CUSTOMER_NEW) {
                CustomerEditScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CUSTOMER_EDIT,
                arguments = listOf(navArgument("customerId") { type = NavType.StringType })
            ) {
                CustomerEditScreen(onBack = { navController.popBackStack() })
            }

            // --- Jobs ------------------------------------------------------
            composable(Destination.Jobs.route) {
                JobListScreen(
                    onOpenJob = { navController.navigate(Routes.jobDetail(it)) },
                    onAddJob = { navController.navigate(Routes.JOB_NEW) }
                )
            }
            composable(
                route = Routes.JOB_DETAIL,
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) {
                JobDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.jobEdit(it)) },
                    onOpenCustomer = { navController.navigate(Routes.customerDetail(it)) }
                )
            }
            composable(Routes.JOB_NEW) {
                JobEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id -> navController.replaceWith(Routes.jobDetail(id)) }
                )
            }
            composable(
                route = Routes.JOB_NEW_FOR_CUSTOMER,
                arguments = listOf(navArgument("customerId") { type = NavType.StringType })
            ) {
                JobEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id -> navController.replaceWith(Routes.jobDetail(id)) }
                )
            }
            composable(
                route = Routes.JOB_EDIT,
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) {
                JobEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            // --- Crew and buy list -----------------------------------------
            composable(Routes.CREW) {
                CrewListScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.CREW_NEW) },
                    onEdit = { navController.navigate(Routes.crewEdit(it)) }
                )
            }
            composable(Routes.CREW_NEW) {
                CrewEditScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CREW_EDIT,
                arguments = listOf(navArgument("staffId") { type = NavType.StringType })
            ) {
                CrewEditScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BUY_LIST) {
                BuyListScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Destination.Schedule.route) {
                ScheduleScreen(onOpenJob = { navController.navigate(Routes.jobDetail(it)) })
            }
            composable(Destination.More.route) {
                MoreScreen(
                    onOpenCrew = { navController.navigate(Routes.CREW) },
                    onOpenBuyList = { navController.navigate(Routes.BUY_LIST) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            }
        }
    }
}

/**
 * After creating a job, land on the new job rather than back on the empty form —
 * pressing back from there should return to the list, not to a form for a job
 * that already exists.
 */
private fun NavHostController.replaceWith(route: String) {
    popBackStack()
    navigate(route)
}
