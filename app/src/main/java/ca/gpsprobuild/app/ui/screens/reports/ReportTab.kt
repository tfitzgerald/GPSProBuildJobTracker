@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.reports

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.core.util.Money
import ca.gpsprobuild.app.data.pdf.PdfReportGenerator
import ca.gpsprobuild.app.data.pdf.QuoteLine
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.data.repository.CustomerRepository
import ca.gpsprobuild.app.data.repository.JobRepository
import ca.gpsprobuild.app.data.repository.MaterialRepository
import ca.gpsprobuild.app.data.repository.StaffRepository
import ca.gpsprobuild.app.data.repository.TaskRepository
import ca.gpsprobuild.app.data.repository.WorkLogRepository
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReportUiState(
    val busy: Boolean = false,
    val generated: File? = null,
    val error: String? = null
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val generator: PdfReportGenerator,
    private val settings: SettingsRepository,
    private val jobRepository: JobRepository,
    private val customerRepository: CustomerRepository,
    private val taskRepository: TaskRepository,
    private val materialRepository: MaterialRepository,
    private val staffRepository: StaffRepository,
    private val workLog: WorkLogRepository
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun clear() = _state.update { it.copy(generated = null, error = null) }

    private fun generate(block: suspend () -> File) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onSuccess { file -> _state.update { it.copy(busy = false, generated = file) } }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.message ?: "Failed") }
                }
        }
    }

    fun jobSummary() = generate {
        val job = requireNotNull(jobRepository.getById(jobId)) { "Job not found" }
        val current = settings.settings.first()
        generator.jobSummary(
            settings = current,
            job = job,
            customer = customerRepository.getById(job.customerId),
            tasks = taskRepository.observeForJob(jobId).first(),
            materials = materialRepository.observeForJob(jobId).first(),
            events = jobRepository.observeEvents(jobId).first(),
            crew = crewFor(jobId),
            hoursLogged = workLog.observeTimeEntries(jobId).first().sumOf { it.hours },
            // The owner's privacy setting carries into the document, so a report
            // produced while sitting beside a client does not print internal cost.
            showCosts = !current.effectivePrivacyMode.hidesInternalCost
        )
    }

    fun quote() = generate {
        val job = requireNotNull(jobRepository.getById(jobId)) { "Job not found" }
        val current = settings.settings.first()
        val materials = materialRepository.observeForJob(jobId).first()
        val materialCents = materials
            .filter { !it.isClientSupplied && it.status.countsTowardCost }
            .sumOf { it.lineTotalCents }
        val labourCents = job.estimatedHours
            ?.let { Money.hoursToCents(it, current.defaultLabourRateCents) } ?: 0L

        // Fall back to the estimate figure when there is no itemised breakdown yet,
        // rather than printing a quote that totals zero.
        val lines = buildList {
            if (materialCents > 0) add(QuoteLine("Materials", materialCents))
            if (labourCents > 0) add(QuoteLine("Labour", labourCents))
            if (isEmpty()) {
                add(QuoteLine(job.title, job.estimateAmountCents ?: 0L))
            }
        }

        generator.quote(
            settings = current,
            job = job,
            customer = customerRepository.getById(job.customerId),
            lines = lines
        )
    }

    fun workOrder() = generate {
        val job = requireNotNull(jobRepository.getById(jobId)) { "Job not found" }
        generator.workOrder(
            settings = settings.settings.first(),
            job = job,
            customer = customerRepository.getById(job.customerId),
            tasks = taskRepository.observeForJob(jobId).first(),
            materials = materialRepository.observeForJob(jobId).first(),
            crew = crewFor(jobId)
        )
    }

    private suspend fun crewFor(jobId: Long) =
        staffRepository.observeJobAssignments(jobId).first().mapNotNull {
            staffRepository.getById(it.staffId)
        }
}

@Composable
fun ReportTab(viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.generated) {
        state.generated?.let { file ->
            sharePdf(context, file)
            viewModel.clear()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Documents")

        ReportRow(
            title = "Job summary",
            body = "Everything on record: tasks, materials, crew, hours and the full log. " +
                "The document that answers a warranty question two years from now.",
            enabled = !state.busy,
            onClick = viewModel::jobSummary
        )
        ReportRow(
            title = "Quotation",
            body = "Priced, with HST and a signature block, ready to send to the client.",
            enabled = !state.busy,
            onClick = viewModel::quote
        )
        ReportRow(
            title = "Work order",
            body = "For the crew: task checklist, materials and site access notes. No prices.",
            enabled = !state.busy,
            onClick = viewModel::workOrder
        )

        if (state.busy) {
            Spacer(Modifier.height(8.dp))
            Text("Building the document…", style = MaterialTheme.typography.bodyMedium)
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ReportRow(title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.cardGap),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share document"))
}
