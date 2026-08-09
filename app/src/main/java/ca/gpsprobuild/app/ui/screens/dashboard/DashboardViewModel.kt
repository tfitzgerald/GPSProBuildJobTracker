package ca.gpsprobuild.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.data.local.dao.CustomerDao
import ca.gpsprobuild.app.data.local.dao.JobDao
import ca.gpsprobuild.app.data.local.dao.MaterialDao
import ca.gpsprobuild.app.data.local.dao.PhotoDao
import ca.gpsprobuild.app.data.local.dao.SyncDao
import ca.gpsprobuild.app.data.local.dao.TaskDao
import ca.gpsprobuild.app.data.prefs.AppSettings
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val settings: AppSettings = AppSettings(),
    val customerCount: Int = 0,
    val openJobCount: Int = 0,
    val overdueTaskCount: Int = 0,
    val dueTodayCount: Int = 0,
    val materialsNeeded: Int = 0,
    val photoCount: Int = 0,
    val unsentChanges: Int = 0
)

/**
 * Reads straight from the DAOs for now. Once repositories land in step 2 this
 * moves behind them, but the shape of the state stays the same — which is the
 * point of proving it end to end this early.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val customerDao: CustomerDao,
    private val jobDao: JobDao,
    private val taskDao: TaskDao,
    private val materialDao: MaterialDao,
    private val photoDao: PhotoDao,
    private val syncDao: SyncDao
) : ViewModel() {

    private val today = LocalDate.now()

    // Kotlin's typed `combine` tops out at five flows, and the vararg form forces
    // every source to the same type. Nesting two typed combines keeps this readable
    // and keeps the compiler doing the type checking rather than casts at runtime.
    private data class Counts(
        val customers: Int,
        val openJobs: Int,
        val overdue: Int,
        val dueToday: Int
    )

    private data class Extras(
        val materialsNeeded: Int,
        val photos: Int,
        val unsent: Int
    )

    private val counts = combine(
        customerDao.observeCount(),
        jobDao.observeOpenCount(),
        taskDao.observeOverdue(today),
        taskDao.observeDueOn(today)
    ) { customers, openJobs, overdue, dueToday ->
        Counts(customers, openJobs, overdue.size, dueToday.size)
    }

    private val extras = combine(
        materialDao.observeNeededCount(),
        photoDao.observeCount(),
        syncDao.observePendingCount()
    ) { materials, photos, unsent ->
        Extras(materials, photos, unsent)
    }

    val state: StateFlow<DashboardUiState> = combine(
        settingsRepository.settings,
        counts,
        extras
    ) { settings, c, e ->
        DashboardUiState(
            settings = settings,
            customerCount = c.customers,
            openJobCount = c.openJobs,
            overdueTaskCount = c.overdue,
            dueTodayCount = c.dueToday,
            materialsNeeded = e.materialsNeeded,
            photoCount = e.photos,
            unsentChanges = e.unsent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
