@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.materials

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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import ca.gpsprobuild.app.core.util.IntentLauncher
import ca.gpsprobuild.app.core.util.Money
import ca.gpsprobuild.app.data.local.entity.JobEntity
import ca.gpsprobuild.app.data.local.entity.MaterialEntity
import ca.gpsprobuild.app.data.local.entity.SupplierEntity
import ca.gpsprobuild.app.data.repository.JobRepository
import ca.gpsprobuild.app.data.repository.MaterialRepository
import ca.gpsprobuild.app.domain.model.MaterialCategory
import ca.gpsprobuild.app.domain.model.MaterialUnit
import ca.gpsprobuild.app.ui.components.EmptyState
import ca.gpsprobuild.app.ui.components.MoneyKind
import ca.gpsprobuild.app.ui.components.MoneyText
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.components.StatusChip
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.LocalStatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Job tab
// ---------------------------------------------------------------------------

data class MaterialTabUiState(
    val materials: List<MaterialEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList()
) {
    /** Client-supplied and cancelled lines are excluded — they are not our cost. */
    val subtotalCents: Long
        get() = materials
            .filter { !it.isClientSupplied && it.status.countsTowardCost }
            .sumOf { it.lineTotalCents }

    val outstanding: Int get() = materials.count { it.status.isOutstanding }
}

@HiltViewModel
class MaterialTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MaterialRepository
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull() ?: 0L

    val state: StateFlow<MaterialTabUiState> = combine(
        repository.observeForJob(jobId),
        repository.observeSuppliers()
    ) { materials, suppliers ->
        MaterialTabUiState(materials, suppliers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaterialTabUiState())

    fun add(name: String, quantity: Double, unit: MaterialUnit, category: MaterialCategory) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.save(
                MaterialEntity(
                    jobId = jobId,
                    name = name.trim(),
                    quantity = quantity,
                    unit = unit,
                    category = category
                )
            )
        }
    }

    fun advance(material: MaterialEntity) {
        viewModelScope.launch { repository.advanceStatus(material) }
    }

    fun delete(material: MaterialEntity) {
        viewModelScope.launch { repository.delete(material) }
    }
}

@Composable
fun MaterialTab(viewModel: MaterialTabViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current

    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(MaterialUnit.EA) }
    var category by remember { mutableStateOf(MaterialCategory.OTHER) }
    var unitMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        if (state.materials.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${state.outstanding} still to get",
                    style = MaterialTheme.typography.titleMedium
                )
                MoneyText(
                    cents = state.subtotalCents,
                    kind = MoneyKind.INTERNAL,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        state.materials.groupBy { it.category }.forEach { (cat, items) ->
            SectionHeader(cat.label)
            items.forEach { material ->
                Card(
                    onClick = { viewModel.advance(material) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(material.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${trimQuantity(material.quantity)} ${material.unit.short}" +
                                    if (material.isClientSupplied) " · client supplied" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusChip(
                                label = material.status.label,
                                color = statusColors.forMaterial(material.status)
                            )
                            if (material.unitCostCents != null) {
                                MoneyText(
                                    cents = material.lineTotalCents,
                                    kind = MoneyKind.INTERNAL,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.cardGap))
        }

        if (state.materials.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Nothing on the list yet. Add what this job needs and it shows up on the buy list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(Modifier.height(Dimens.cardGap))
        }

        SectionHeader("Add material")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Qty") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { unitMenu = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(unit.short) }
                DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                    MaterialUnit.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.short} — ${option.label}") },
                            onClick = { unit = option; unitMenu = false }
                        )
                    }
                }
            }
        }
        Box {
            OutlinedButton(
                onClick = { categoryMenu = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(category.label) }
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                MaterialCategory.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { category = option; categoryMenu = false }
                    )
                }
            }
        }
        TextButton(
            onClick = {
                viewModel.add(name, quantity.toDoubleOrNull() ?: 1.0, unit, category)
                name = ""
                quantity = "1"
            },
            enabled = name.isNotBlank()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add to list", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private fun trimQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)

// ---------------------------------------------------------------------------
// Cross-job buy list
// ---------------------------------------------------------------------------

data class BuyListRow(
    val material: MaterialEntity,
    val jobNumber: String,
    val supplierName: String
)

data class BuyListUiState(
    val rows: List<BuyListRow> = emptyList(),
    val loading: Boolean = true
) {
    val bySupplier: List<Pair<String, List<BuyListRow>>>
        get() = rows.groupBy { it.supplierName }.toList().sortedBy { it.first }

    val totalCents: Long get() = rows.sumOf { it.material.lineTotalCents }
}

@HiltViewModel
class BuyListViewModel @Inject constructor(
    private val materialRepository: MaterialRepository,
    jobRepository: JobRepository
) : ViewModel() {

    val state: StateFlow<BuyListUiState> = combine(
        materialRepository.observeBuyList(),
        jobRepository.observeAll(),
        materialRepository.observeSuppliers()
    ) { materials, jobs, suppliers ->
        val jobNumbers = jobs.associate { it.id to it.jobNumber }
        val supplierNames = suppliers.associate { it.id to it.name }
        BuyListUiState(
            rows = materials.map { material ->
                BuyListRow(
                    material = material,
                    jobNumber = jobNumbers[material.jobId] ?: "—",
                    supplierName = material.supplierId?.let { supplierNames[it] } ?: "No supplier set"
                )
            },
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuyListUiState())

    fun advance(material: MaterialEntity) {
        viewModelScope.launch { materialRepository.advanceStatus(material) }
    }

    /** Plain text, because the person going to the store may not have the app. */
    fun asShareText(state: BuyListUiState): String = buildString {
        appendLine("GPS ProBuild — buy list")
        appendLine()
        state.bySupplier.forEach { (supplier, rows) ->
            appendLine(supplier)
            rows.forEach { row ->
                appendLine(
                    "  [ ] ${trimQuantity(row.material.quantity)} ${row.material.unit.short}  " +
                        "${row.material.name}  (${row.jobNumber})"
                )
            }
            appendLine()
        }
    }
}

@Composable
fun BuyListScreen(
    viewModel: BuyListViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buy list") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.rows.isNotEmpty()) {
                        IconButton(onClick = {
                            IntentLauncher.shareText(
                                context,
                                "GPS ProBuild buy list",
                                viewModel.asShareText(state)
                            )
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share list")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.ShoppingCart,
                    title = "Nothing to buy",
                    message = "Materials marked Needed or Ordered across all open jobs land here, " +
                        "grouped by supplier."
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
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${state.rows.size} items", style = MaterialTheme.typography.titleMedium)
                MoneyText(
                    cents = state.totalCents,
                    kind = MoneyKind.INTERNAL,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            state.bySupplier.forEach { (supplier, rows) ->
                SectionHeader("$supplier (${rows.size})")
                rows.forEach { row ->
                    Card(
                        onClick = { viewModel.advance(row.material) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${trimQuantity(row.material.quantity)} ${row.material.unit.short}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(row.material.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    row.jobNumber,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusChip(
                                label = row.material.status.label,
                                color = statusColors.forMaterial(row.material.status)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.cardGap))
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
