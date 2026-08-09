@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.schedule

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.core.util.Dates
import ca.gpsprobuild.app.data.local.SyncMeta
import ca.gpsprobuild.app.data.local.dao.AppointmentDao
import ca.gpsprobuild.app.data.local.entity.AppointmentEntity
import ca.gpsprobuild.app.data.local.entity.JobEntity
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.data.repository.JobRepository
import ca.gpsprobuild.app.domain.model.AppointmentType
import ca.gpsprobuild.app.ui.components.EmptyState
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.components.StatusChip
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.LocalStatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** One row in the agenda, from either an appointment or a job date. */
data class AgendaItem(
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val kind: String,
    val jobId: Long?
)

data class ScheduleUiState(
    val items: List<AgendaItem> = emptyList(),
    val jobs: List<JobEntity> = emptyList(),
    val loading: Boolean = true
) {
    val byDate: List<Pair<LocalDate, List<AgendaItem>>>
        get() = items.groupBy { it.date }.toList().sortedBy { it.first }
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val appointmentDao: AppointmentDao,
    private val settings: SettingsRepository,
    jobRepository: JobRepository
) : ViewModel() {

    private val windowStart = LocalDate.now().minusDays(7)
    private val windowEnd = LocalDate.now().plusMonths(3)

    val state: StateFlow<ScheduleUiState> = combine(
        appointmentDao.observeBetween(windowStart.toInstant(), windowEnd.toInstant()),
        jobRepository.observeOpen()
    ) { appointments, jobs ->
        val jobNumbers = jobs.associate { it.id to it.jobNumber }

        val fromAppointments = appointments.map { appointment ->
            AgendaItem(
                date = Dates.toLocalDate(appointment.startAt),
                title = appointment.title,
                subtitle = listOfNotNull(
                    appointment.type.label,
                    appointment.jobId?.let { jobNumbers[it] },
                    appointment.location
                ).joinToString(" · "),
                kind = appointment.type.label,
                jobId = appointment.jobId
            )
        }

        // Job start and target dates appear alongside appointments, because a
        // schedule that only shows meetings is not the schedule anyone works to.
        val fromJobs = jobs.flatMap { job ->
            listOfNotNull(
                job.startDate?.takeIf { it in windowStart..windowEnd }?.let {
                    AgendaItem(it, "Start: ${job.title}", job.jobNumber, "Job", job.id)
                },
                job.targetEndDate?.takeIf { it in windowStart..windowEnd }?.let {
                    AgendaItem(it, "Target finish: ${job.title}", job.jobNumber, "Job", job.id)
                }
            )
        }

        ScheduleUiState(
            items = (fromAppointments + fromJobs).sortedBy { it.date },
            jobs = jobs,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun addAppointment(
        title: String,
        date: LocalDate,
        type: AppointmentType,
        jobId: Long?,
        location: String
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val deviceId = settings.settings.first().deviceId
            appointmentDao.insert(
                AppointmentEntity(
                    jobId = jobId,
                    title = title.trim(),
                    type = type,
                    startAt = date.atTime(LocalTime.of(8, 0))
                        .atZone(ZoneId.systemDefault()).toInstant(),
                    isAllDay = true,
                    location = location.trim().takeIf { it.isNotBlank() },
                    sync = SyncMeta.new(deviceId)
                )
            )
        }
    }
}

private fun LocalDate.toInstant(): Instant =
    atStartOfDay(ZoneId.systemDefault()).toInstant()

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onOpenJob: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current
    var showAdd by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Add appointment")
            }
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.CalendarMonth,
                    title = "Nothing scheduled",
                    message = "Site visits, deliveries and inspections show here, alongside job " +
                        "start and finish dates.",
                    actionLabel = "Add something",
                    onAction = { showAdd = true }
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding)
            ) {
                Spacer(Modifier.height(12.dp))
                state.byDate.forEach { (date, items) ->
                    SectionHeader(
                        when (date) {
                            today -> "Today · ${Dates.formatWithDay(date)}"
                            else -> Dates.formatWithDay(date)
                        }
                    )
                    items.forEach { item ->
                        Card(
                            onClick = { item.jobId?.let(onOpenJob) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (date == today) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusChip(
                                    label = item.kind,
                                    color = if (date < today) statusColors.cancelled
                                    else statusColors.scheduled
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(96.dp))
            }
        }
    }

    if (showAdd) {
        AddAppointmentDialog(
            jobs = state.jobs,
            onDismiss = { showAdd = false },
            onAdd = { title, date, type, jobId, location ->
                viewModel.addAppointment(title, date, type, jobId, location)
                showAdd = false
            }
        )
    }
}

@Composable
private fun AddAppointmentDialog(
    jobs: List<JobEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, LocalDate, AppointmentType, Long?, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var type by remember { mutableStateOf(AppointmentType.SITE_VISIT) }
    var typeMenu by remember { mutableStateOf(false) }
    var jobMenu by remember { mutableStateOf(false) }
    var selectedJob by remember { mutableStateOf<JobEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to schedule") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                // The platform date picker rather than the Material 3 one, which is
                // still experimental — this is not the screen to spend that risk on.
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
                            date.year, date.monthValue - 1, date.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(Dates.formatWithDay(date)) }

                Box {
                    OutlinedButton(
                        onClick = { typeMenu = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text(type.label) }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        AppointmentType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { type = option; typeMenu = false }
                            )
                        }
                    }
                }

                Box {
                    OutlinedButton(
                        onClick = { jobMenu = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text(selectedJob?.title ?: "No job (optional)") }
                    DropdownMenu(expanded = jobMenu, onDismissRequest = { jobMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("No job") },
                            onClick = { selectedJob = null; jobMenu = false }
                        )
                        jobs.forEach { job ->
                            DropdownMenuItem(
                                text = { Text("${job.jobNumber} — ${job.title}") },
                                onClick = { selectedJob = job; jobMenu = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Where (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, date, type, selectedJob?.id, location) },
                enabled = title.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
