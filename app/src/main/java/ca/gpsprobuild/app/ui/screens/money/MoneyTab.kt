@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.core.util.Dates
import ca.gpsprobuild.app.core.util.Hours
import ca.gpsprobuild.app.core.util.Money
import ca.gpsprobuild.app.data.local.entity.ExpenseEntity
import ca.gpsprobuild.app.data.local.entity.StaffEntity
import ca.gpsprobuild.app.data.local.entity.TimeEntryEntity
import ca.gpsprobuild.app.data.repository.JobCostSummary
import ca.gpsprobuild.app.data.repository.JobRepository
import ca.gpsprobuild.app.data.repository.StaffRepository
import ca.gpsprobuild.app.data.repository.WorkLogRepository
import ca.gpsprobuild.app.domain.model.ExpenseCategory
import ca.gpsprobuild.app.ui.components.MoneyKind
import ca.gpsprobuild.app.ui.components.MoneyText
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.components.StaffAvatar
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.LocalStatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MoneyTabUiState(
    val summary: JobCostSummary = JobCostSummary(),
    val timeEntries: List<TimeEntryEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val crew: List<StaffEntity> = emptyList()
)

@HiltViewModel
class MoneyTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workLog: WorkLogRepository,
    private val staffRepository: StaffRepository,
    jobRepository: JobRepository
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull() ?: 0L

    private val costs = combine(
        jobRepository.observeById(jobId),
        workLog.observeTimeEntries(jobId),
        workLog.observeExpenses(jobId),
        workLog.observeMaterialCost(jobId),
        workLog.observeApprovedChangeOrderCents(jobId)
    ) { job, time, expenses, materials, changeOrders ->
        JobCostSummary(
            contractCents = job?.approvedAmountCents ?: job?.estimateAmountCents ?: 0L,
            changeOrderCents = changeOrders,
            materialCents = materials
                .filter { !it.isClientSupplied && it.status.countsTowardCost }
                .sumOf { it.lineTotalCents },
            // Entries with no rate on record contribute hours but no cost, rather
            // than silently valuing that person's time at zero-looking-like-free.
            labourCents = time.sumOf { entry ->
                entry.rateSnapshotCents?.let { Money.hoursToCents(entry.hours, it) } ?: 0L
            },
            expenseCents = expenses.sumOf { it.totalCents },
            hoursLogged = time.sumOf { it.hours }
        )
    }

    val state: StateFlow<MoneyTabUiState> = combine(
        costs,
        workLog.observeTimeEntries(jobId),
        workLog.observeExpenses(jobId),
        staffRepository.observeActive()
    ) { summary, time, expenses, crew ->
        MoneyTabUiState(summary, time, expenses, crew)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyTabUiState())

    fun logHours(staffId: Long, hours: Double, notes: String) {
        if (hours <= 0) return
        viewModelScope.launch {
            workLog.logHours(jobId, staffId, LocalDate.now(), hours, notes)
        }
    }

    fun deleteTimeEntry(entry: TimeEntryEntity) {
        viewModelScope.launch { workLog.deleteTimeEntry(entry) }
    }

    fun addExpense(description: String, amount: String, category: ExpenseCategory, vendor: String) {
        val cents = Money.parseToCents(amount) ?: return
        if (description.isBlank()) return
        viewModelScope.launch {
            workLog.addExpense(
                ExpenseEntity(
                    jobId = jobId,
                    expenseDate = LocalDate.now(),
                    description = description.trim(),
                    category = category,
                    vendor = vendor.trim().takeIf { it.isNotBlank() },
                    amountCents = cents
                )
            )
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch { workLog.deleteExpense(expense) }
    }
}

@Composable
fun MoneyTab(viewModel: MoneyTabViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current
    val summary = state.summary

    var hoursText by remember { mutableStateOf("") }
    var hoursNote by remember { mutableStateOf("") }
    var selectedStaff by remember { mutableStateOf<StaffEntity?>(null) }
    var staffMenu by remember { mutableStateOf(false) }

    var expenseDesc by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }
    var expenseVendor by remember { mutableStateOf("") }
    var expenseCategory by remember { mutableStateOf(ExpenseCategory.MATERIALS) }
    var expenseMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Where this job stands")
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                MoneyRow("Contract", summary.contractCents, MoneyKind.CONTRACT)
                if (summary.changeOrderCents != 0L) {
                    MoneyRow("Change orders", summary.changeOrderCents, MoneyKind.CONTRACT)
                }
                Spacer(Modifier.height(6.dp))
                MoneyRow("Materials", summary.materialCents)
                MoneyRow("Labour", summary.labourCents)
                MoneyRow("Expenses", summary.expenseCents)
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Margin", style = MaterialTheme.typography.titleMedium)
                    MoneyText(
                        cents = summary.marginCents,
                        kind = MoneyKind.INTERNAL,
                        style = MaterialTheme.typography.titleMedium,
                        signed = true,
                        color = if (summary.marginCents < 0) statusColors.overdue
                        else statusColors.complete
                    )
                }
                summary.marginFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 6.dp)
                    )
                    Text(
                        "%.0f%% of revenue".format(fraction * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.sectionGap))
        SectionHeader("Hours (${Hours.format(summary.hoursLogged)})")

        state.timeEntries.forEach { entry ->
            val person = state.crew.firstOrNull { it.id == entry.staffId }
            Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StaffAvatar(
                        name = person?.fullName ?: "?",
                        seed = entry.staffId.toString(),
                        size = 32.dp
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            person?.fullName ?: "Unknown",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            listOfNotNull(
                                Dates.formatShort(entry.workDate),
                                entry.notes
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(Hours.format(entry.hours), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { viewModel.deleteTimeEntry(entry) }) { Text("×") }
                }
            }
        }

        Box {
            OutlinedButton(onClick = { staffMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedStaff?.fullName ?: "Who worked?")
            }
            DropdownMenu(expanded = staffMenu, onDismissRequest = { staffMenu = false }) {
                if (state.crew.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Add crew members first") },
                        onClick = { staffMenu = false }
                    )
                }
                state.crew.forEach { member ->
                    DropdownMenuItem(
                        text = { Text(member.fullName) },
                        onClick = { selectedStaff = member; staffMenu = false }
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = hoursText,
                onValueChange = { hoursText = it },
                label = { Text("Hours") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = hoursNote,
                onValueChange = { hoursNote = it },
                label = { Text("Doing what") },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
        }
        TextButton(
            onClick = {
                selectedStaff?.let {
                    viewModel.logHours(it.id, hoursText.toDoubleOrNull() ?: 0.0, hoursNote)
                    hoursText = ""
                    hoursNote = ""
                }
            },
            enabled = selectedStaff != null && (hoursText.toDoubleOrNull() ?: 0.0) > 0
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Log hours", Modifier.padding(start = 6.dp))
        }

        Spacer(Modifier.height(Dimens.sectionGap))
        SectionHeader("Expenses")

        state.expenses.forEach { expense ->
            Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(expense.description, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(
                                expense.category.label,
                                expense.vendor,
                                Dates.formatShort(expense.expenseDate)
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MoneyText(
                        cents = expense.totalCents,
                        kind = MoneyKind.INTERNAL,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { viewModel.deleteExpense(expense) }) { Text("×") }
                }
            }
        }

        OutlinedTextField(
            value = expenseDesc,
            onValueChange = { expenseDesc = it },
            label = { Text("What was bought") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = expenseAmount,
                onValueChange = { expenseAmount = it },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = expenseVendor,
                onValueChange = { expenseVendor = it },
                label = { Text("Vendor") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Box {
            OutlinedButton(
                onClick = { expenseMenu = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(expenseCategory.label) }
            DropdownMenu(expanded = expenseMenu, onDismissRequest = { expenseMenu = false }) {
                ExpenseCategory.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { expenseCategory = option; expenseMenu = false }
                    )
                }
            }
        }
        TextButton(
            onClick = {
                viewModel.addExpense(expenseDesc, expenseAmount, expenseCategory, expenseVendor)
                expenseDesc = ""
                expenseAmount = ""
                expenseVendor = ""
            },
            enabled = expenseDesc.isNotBlank() && Money.parseToCents(expenseAmount) != null
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add expense", Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun MoneyRow(label: String, cents: Long, kind: MoneyKind = MoneyKind.INTERNAL) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MoneyText(cents = cents, kind = kind, style = MaterialTheme.typography.bodyLarge)
    }
}
