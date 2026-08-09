@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.staff

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.core.util.Dates
import ca.gpsprobuild.app.core.util.IntentLauncher
import ca.gpsprobuild.app.core.util.Money
import ca.gpsprobuild.app.core.util.Phones
import ca.gpsprobuild.app.data.local.entity.StaffEntity
import ca.gpsprobuild.app.data.repository.StaffRepository
import ca.gpsprobuild.app.domain.model.EmploymentType
import ca.gpsprobuild.app.domain.model.Labelled
import ca.gpsprobuild.app.domain.model.StaffRole
import ca.gpsprobuild.app.ui.components.EmptyState
import ca.gpsprobuild.app.ui.components.MoneyKind
import ca.gpsprobuild.app.ui.components.MoneyText
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.components.StaffAvatar
import ca.gpsprobuild.app.ui.components.StatusChip
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.LocalStatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

data class CrewListUiState(
    val staff: List<StaffEntity> = emptyList(),
    val expiring: List<StaffEntity> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class CrewListViewModel @Inject constructor(
    private val repository: StaffRepository
) : ViewModel() {

    val state: StateFlow<CrewListUiState> = combine(
        repository.observeAll(),
        repository.observeExpiringCompliance()
    ) { staff, expiring ->
        CrewListUiState(staff, expiring, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrewListUiState())
}

@Composable
fun CrewListScreen(
    viewModel: CrewListViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusColors = LocalStatusColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crew") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add crew member")
            }
        }
    ) { padding ->
        if (state.staff.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.Group,
                    title = "No crew yet",
                    message = "Add the people and subs who work on your jobs, then assign them to tasks.",
                    actionLabel = "Add crew member",
                    onAction = onAdd
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding)
        ) {
            // Working an uninsured sub is a real exposure, so this warning sits
            // above the roster rather than buried on a detail screen.
            if (state.expiring.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                "Insurance expiring",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                state.expiring.joinToString { it.fullName },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            state.staff.forEach { member ->
                Card(
                    onClick = { onEdit(member.id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.cardGap),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        StaffAvatar(member.fullName, seed = member.sync.syncId)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(member.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    member.role.label,
                                    member.companyName?.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!member.isActive) {
                            StatusChip("Inactive", statusColors.cancelled)
                        }
                        member.phone?.let { phone ->
                            IconButton(onClick = { IntentLauncher.dial(context, phone) }) {
                                Icon(Icons.Filled.Phone, contentDescription = "Call")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Edit
// ---------------------------------------------------------------------------

data class CrewFormState(
    val id: Long = 0,
    val fullName: String = "",
    val role: StaffRole = StaffRole.CARPENTER,
    val employmentType: EmploymentType = EmploymentType.EMPLOYEE,
    val companyName: String = "",
    val phone: String = "",
    val email: String = "",
    val hourlyRate: String = "",
    val skills: String = "",
    val licenceNumber: String = "",
    val insuranceExpiry: LocalDate? = null,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val isActive: Boolean = true,
    val notes: String = "",
    val saved: Boolean = false,
    val error: String? = null
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = fullName.isNotBlank()
}

@HiltViewModel
class CrewEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StaffRepository
) : ViewModel() {

    private val staffId: Long = savedStateHandle.get<String>("staffId")?.toLongOrNull() ?: 0L
    private val _state = MutableStateFlow(CrewFormState())
    val state: StateFlow<CrewFormState> = _state.asStateFlow()
    private var original: StaffEntity? = null

    init {
        if (staffId != 0L) {
            viewModelScope.launch {
                repository.getById(staffId)?.let { member ->
                    original = member
                    _state.value = CrewFormState(
                        id = member.id,
                        fullName = member.fullName,
                        role = member.role,
                        employmentType = member.employmentType,
                        companyName = member.companyName.orEmpty(),
                        phone = member.phone.orEmpty(),
                        email = member.email.orEmpty(),
                        hourlyRate = member.hourlyRateCents
                            ?.let { "%.2f".format(it / 100.0) }.orEmpty(),
                        skills = member.skills.orEmpty(),
                        licenceNumber = member.licenceNumber.orEmpty(),
                        insuranceExpiry = member.insuranceExpiry,
                        emergencyContactName = member.emergencyContactName.orEmpty(),
                        emergencyContactPhone = member.emergencyContactPhone.orEmpty(),
                        isActive = member.isActive,
                        notes = member.notes.orEmpty()
                    )
                }
            }
        }
    }

    fun update(transform: (CrewFormState) -> CrewFormState) =
        _state.update { transform(it).copy(error = null) }

    fun save() {
        val form = _state.value
        if (!form.canSave) return
        viewModelScope.launch {
            runCatching {
                val base = original ?: StaffEntity(fullName = form.fullName)
                repository.save(
                    base.copy(
                        id = form.id,
                        fullName = form.fullName.trim(),
                        role = form.role,
                        employmentType = form.employmentType,
                        companyName = form.companyName.trim().takeIf { it.isNotBlank() },
                        phone = form.phone.trim().takeIf { it.isNotBlank() },
                        email = form.email.trim().takeIf { it.isNotBlank() },
                        hourlyRateCents = Money.parseToCents(form.hourlyRate),
                        skills = form.skills.trim().takeIf { it.isNotBlank() },
                        licenceNumber = form.licenceNumber.trim().takeIf { it.isNotBlank() },
                        insuranceExpiry = form.insuranceExpiry,
                        emergencyContactName = form.emergencyContactName.trim()
                            .takeIf { it.isNotBlank() },
                        emergencyContactPhone = form.emergencyContactPhone.trim()
                            .takeIf { it.isNotBlank() },
                        isActive = form.isActive,
                        notes = form.notes.trim().takeIf { it.isNotBlank() }
                    )
                )
            }.onSuccess {
                _state.update { it.copy(saved = true) }
            }.onFailure { error ->
                _state.update { it.copy(error = "Could not save: ${error.message}") }
            }
        }
    }
}

@Composable
fun CrewEditScreen(
    viewModel: CrewEditViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add crew member" else "Edit crew member") },
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
            SectionHeader("Person")
            CrewField("Name", state.fullName, required = true) { v ->
                viewModel.update { it.copy(fullName = v) }
            }
            CrewPicker("Role", state.role, StaffRole.entries) { v ->
                viewModel.update { it.copy(role = v) }
            }
            CrewPicker("Type", state.employmentType, EmploymentType.entries) { v ->
                viewModel.update { it.copy(employmentType = v) }
            }
            if (state.employmentType == EmploymentType.SUBCONTRACTOR) {
                CrewField("Company", state.companyName) { v ->
                    viewModel.update { it.copy(companyName = v) }
                }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Contact")
            CrewField("Phone", state.phone, keyboard = KeyboardType.Phone) { v ->
                viewModel.update { it.copy(phone = v) }
            }
            CrewField("Email", state.email, keyboard = KeyboardType.Email) { v ->
                viewModel.update { it.copy(email = v) }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Work")
            CrewField("Hourly rate", state.hourlyRate, keyboard = KeyboardType.Decimal) { v ->
                viewModel.update { it.copy(hourlyRate = v) }
            }
            CrewField("Skills (comma separated)", state.skills) { v ->
                viewModel.update { it.copy(skills = v) }
            }
            CrewField("Licence number", state.licenceNumber) { v ->
                viewModel.update { it.copy(licenceNumber = v) }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Emergency contact")
            CrewField("Name", state.emergencyContactName) { v ->
                viewModel.update { it.copy(emergencyContactName = v) }
            }
            CrewField("Phone", state.emergencyContactPhone, keyboard = KeyboardType.Phone) { v ->
                viewModel.update { it.copy(emergencyContactPhone = v) }
            }

            CrewField("Notes", state.notes, lines = 3) { v ->
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
                Text(if (state.isNew) "Add to crew" else "Save changes")
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun CrewField(
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
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    )
}

@Composable
private fun <T> CrewPicker(
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
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Job crew tab
// ---------------------------------------------------------------------------

data class JobCrewUiState(
    val assigned: List<StaffEntity> = emptyList(),
    val available: List<StaffEntity> = emptyList()
)

@HiltViewModel
class JobCrewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StaffRepository
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull() ?: 0L

    val state: StateFlow<JobCrewUiState> = combine(
        repository.observeJobAssignments(jobId),
        repository.observeActive()
    ) { assignments, staff ->
        val assignedIds = assignments.map { it.staffId }.toSet()
        JobCrewUiState(
            assigned = staff.filter { it.id in assignedIds },
            available = staff.filterNot { it.id in assignedIds }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobCrewUiState())

    fun assign(staffId: Long) {
        viewModelScope.launch { repository.assignToJob(jobId, staffId) }
    }

    fun remove(staffId: Long) {
        viewModelScope.launch { repository.removeFromJob(jobId, staffId) }
    }
}

@Composable
fun JobCrewTab(viewModel: JobCrewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var addMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        SectionHeader("On this job (${state.assigned.size})")

        if (state.assigned.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Nobody assigned yet. Assigning someone to a task adds them here automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        state.assigned.forEach { member ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StaffAvatar(member.fullName, seed = member.sync.syncId, size = 36.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(member.fullName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            member.role.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    member.hourlyRateCents?.let {
                        MoneyText(
                            cents = it,
                            kind = MoneyKind.INTERNAL,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    member.phone?.let { phone ->
                        IconButton(onClick = { IntentLauncher.dial(context, phone) }) {
                            Icon(Icons.Filled.Phone, contentDescription = "Call")
                        }
                    }
                    TextButton(onClick = { viewModel.remove(member.id) }) { Text("Remove") }
                }
            }
        }

        Spacer(Modifier.height(Dimens.cardGap))
        Box {
            OutlinedButton(
                onClick = { addMenu = true },
                enabled = state.available.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    if (state.available.isEmpty()) "Everyone is already on this job"
                    else "Add someone to the crew",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                state.available.forEach { member ->
                    DropdownMenuItem(
                        text = { Text("${member.fullName} — ${member.role.label}") },
                        onClick = {
                            viewModel.assign(member.id)
                            addMenu = false
                        }
                    )
                }
            }
        }
    }
}
