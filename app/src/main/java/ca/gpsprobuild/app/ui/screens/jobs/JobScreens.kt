@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.jobs

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.gpsprobuild.app.core.util.Addresses
import ca.gpsprobuild.app.core.util.Dates
import ca.gpsprobuild.app.domain.model.JobStatus
import ca.gpsprobuild.app.domain.model.JobType
import ca.gpsprobuild.app.domain.model.Labelled
import ca.gpsprobuild.app.domain.model.Priority
import ca.gpsprobuild.app.ui.components.EmptyState
import ca.gpsprobuild.app.ui.screens.materials.MaterialTab
import ca.gpsprobuild.app.ui.screens.staff.JobCrewTab
import ca.gpsprobuild.app.ui.screens.tasks.TaskTab
import ca.gpsprobuild.app.ui.components.MoneyKind
import ca.gpsprobuild.app.ui.components.MoneyText
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.components.StatusChip
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.LocalStatusColors

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

@Composable
fun JobListScreen(
    viewModel: JobListViewModel = hiltViewModel(),
    onOpenJob: (Long) -> Unit,
    onAddJob: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddJob) {
                Icon(Icons.Filled.Add, contentDescription = "Add job")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search job number, title, address") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding, vertical = 8.dp)
            )

            Row(
                Modifier.padding(horizontal = Dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.openOnly) {
                    FilledTonalButton(onClick = { viewModel.setOpenOnly(true) }) { Text("Open") }
                    OutlinedButton(onClick = { viewModel.setOpenOnly(false) }) { Text("All") }
                } else {
                    OutlinedButton(onClick = { viewModel.setOpenOnly(true) }) { Text("Open") }
                    FilledTonalButton(onClick = { viewModel.setOpenOnly(false) }) { Text("All") }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.rows.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Construction,
                    title = if (state.query.isBlank()) "No jobs yet" else "Nothing matched",
                    message = if (state.query.isBlank()) {
                        "Add a job and it gets a number, a status and a timeline automatically."
                    } else {
                        "No job matches \"${state.query}\"."
                    },
                    actionLabel = if (state.query.isBlank()) "Add job" else null,
                    onAction = if (state.query.isBlank()) onAddJob else null
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)
                ) {
                    items(state.rows, key = { it.job.id }) { row ->
                        Card(
                            onClick = { onOpenJob(row.job.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        row.job.jobNumber,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    StatusChip(
                                        label = row.job.status.label,
                                        color = statusColors.forJob(row.job.status)
                                    )
                                }
                                Text(row.job.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    row.customerName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        row.job.jobType.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    MoneyText(
                                        cents = row.job.approvedAmountCents
                                            ?: row.job.estimateAmountCents,
                                        kind = MoneyKind.CONTRACT,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class JobTab(val label: String) {
    OVERVIEW("Overview"),
    TASKS("Tasks"),
    MATERIALS("Materials"),
    CREW("Crew")
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

@Composable
fun JobDetailScreen(
    viewModel: JobDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenCustomer: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val job = state.job
    val statusColors = LocalStatusColors.current
    var noteText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(job?.jobNumber ?: "Job") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (job != null) {
                        IconButton(onClick = { onEdit(job.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (job == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                JobTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) }
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding)
            ) {
                when (JobTab.entries[selectedTab]) {
                    JobTab.TASKS -> {
                        Spacer(Modifier.height(Dimens.cardGap))
                        TaskTab()
                        Spacer(Modifier.height(64.dp))
                        return@Column
                    }
                    JobTab.MATERIALS -> {
                        Spacer(Modifier.height(Dimens.cardGap))
                        MaterialTab()
                        Spacer(Modifier.height(64.dp))
                        return@Column
                    }
                    JobTab.CREW -> {
                        Spacer(Modifier.height(Dimens.cardGap))
                        JobCrewTab()
                        Spacer(Modifier.height(64.dp))
                        return@Column
                    }
                    JobTab.OVERVIEW -> Unit
                }

            Spacer(Modifier.height(8.dp))
            Text(job.title, style = MaterialTheme.typography.headlineSmall)
            state.customer?.let { customer ->
                TextButton(
                    onClick = { onOpenCustomer(customer.id) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(customer.displayName)
                }
            }

            Spacer(Modifier.height(Dimens.cardGap))

            // The pipeline strip doubles as the status control: tapping a stage
            // moves the job there and writes a timeline entry, so status changes
            // are never silent.
            SectionHeader("Status")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JobStatus.pipeline.forEach { status ->
                    val selected = job.status == status
                    Box(Modifier.padding(vertical = 4.dp)) {
                        if (selected) {
                            StatusChip(
                                label = status.label,
                                color = statusColors.forJob(status),
                                filled = true
                            )
                        } else {
                            TextButton(onClick = { viewModel.changeStatus(status) }) {
                                Text(status.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Details")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    DetailRow("Type", job.jobType.label)
                    DetailRow("Priority", job.priority.label)
                    job.startDate?.let { DetailRow("Start", Dates.format(it)) }
                    job.targetEndDate?.let { DetailRow("Target finish", Dates.format(it)) }
                    if (job.permitRequired) {
                        DetailRow("Permit", job.permitNumber ?: "Required")
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Estimate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MoneyText(
                            cents = job.estimateAmountCents,
                            kind = MoneyKind.CONTRACT,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            state.customer?.let { customer ->
                val address = Addresses.oneLine(
                    customer.street1, customer.street2, customer.city,
                    customer.province, customer.postalCode
                )
                if (address.isNotBlank()) {
                    Spacer(Modifier.height(Dimens.sectionGap))
                    SectionHeader("Site")
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Card(
                        onClick = {
                            ca.gpsprobuild.app.core.util.IntentLauncher.directions(context, address)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Place, contentDescription = null)
                            Text(address, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }

            job.scopeOfWork?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Dimens.sectionGap))
                SectionHeader("Scope of work")
                Card(Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Log (${state.events.size})")
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Add a note") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            viewModel.addNote(noteText)
                            noteText = ""
                        },
                        enabled = noteText.isNotBlank()
                    ) { Text("Add") }
                }
            )
            Spacer(Modifier.height(Dimens.cardGap))
            state.events.forEach { event ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleSmall)
                        event.body?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            Dates.formatDateTime(event.occurredAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------------------------------------------------------------------------
// Create / edit
// ---------------------------------------------------------------------------

@Composable
fun JobEditScreen(
    viewModel: JobEditViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var customerMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedJobId) {
        state.savedJobId?.let(onSaved)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New job" else "Edit job") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.screenPadding)
        ) {
            SectionHeader("Customer")
            OutlinedButton(
                onClick = { customerMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    state.customerName.ifBlank { "Choose a customer *" },
                    modifier = Modifier.weight(1f)
                )
            }
            DropdownMenu(
                expanded = customerMenuOpen,
                onDismissRequest = { customerMenuOpen = false }
            ) {
                if (state.customers.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No customers yet — add one first") },
                        onClick = { customerMenuOpen = false }
                    )
                }
                state.customers.forEach { customer ->
                    DropdownMenuItem(
                        text = { Text(customer.displayName) },
                        onClick = {
                            viewModel.selectCustomer(customer)
                            customerMenuOpen = false
                        }
                    )
                }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Job")
            JobField("Title", state.title, required = true) { v ->
                viewModel.update { it.copy(title = v) }
            }
            JobEnumPicker("Type", state.jobType, JobType.entries) { v ->
                viewModel.update { it.copy(jobType = v) }
            }
            JobEnumPicker("Status", state.status, JobStatus.entries) { v ->
                viewModel.update { it.copy(status = v) }
            }
            JobEnumPicker("Priority", state.priority, Priority.entries) { v ->
                viewModel.update { it.copy(priority = v) }
            }
            JobField("Scope of work", state.scopeOfWork, lines = 4) { v ->
                viewModel.update { it.copy(scopeOfWork = v) }
            }
            JobField(
                "Estimate",
                state.estimateAmount,
                keyboard = KeyboardType.Decimal
            ) { v -> viewModel.update { it.copy(estimateAmount = v) } }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.permitRequired,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(permitRequired = checked) }
                    }
                )
                Text("Permit required", style = MaterialTheme.typography.bodyLarge)
            }

            JobField("Notes", state.notes, lines = 3) { v ->
                viewModel.update { it.copy(notes = v) }
            }

            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            FilledTonalButton(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().height(Dimens.buttonHeight)
            ) {
                Text(if (state.isNew) "Create job" else "Save changes")
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun JobField(
    label: String,
    value: String,
    required: Boolean = false,
    lines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(if (required) "$label *" else label) },
        singleLine = lines == 1,
        minLines = lines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences
            else KeyboardCapitalization.None
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    )
}

@Composable
private fun <T> JobEnumPicker(
    label: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit
) where T : Enum<T>, T : Labelled {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(selected.label, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
