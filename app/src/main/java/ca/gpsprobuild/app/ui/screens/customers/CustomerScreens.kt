@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.customers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.gpsprobuild.app.core.util.Addresses
import ca.gpsprobuild.app.core.util.Dates
import ca.gpsprobuild.app.core.util.IntentLauncher
import ca.gpsprobuild.app.core.util.Phones
import ca.gpsprobuild.app.core.util.PostalCodes
import ca.gpsprobuild.app.data.local.entity.CustomerEntity
import ca.gpsprobuild.app.domain.model.ContactMethod
import ca.gpsprobuild.app.domain.model.CustomerStatus
import ca.gpsprobuild.app.domain.model.CustomerType
import ca.gpsprobuild.app.ui.components.EmptyState
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.components.StaffAvatar
import ca.gpsprobuild.app.ui.components.StatusChip
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.LocalStatusColors

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

@Composable
fun CustomerListScreen(
    viewModel: CustomerListViewModel = hiltViewModel(),
    onOpenCustomer: (Long) -> Unit,
    onAddCustomer: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCustomer) {
                Icon(Icons.Filled.Add, contentDescription = "Add customer")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search name, phone, address") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomerFilter.entries.forEach { filter ->
                    val selected = state.filter == filter
                    if (selected) {
                        FilledTonalButton(onClick = { viewModel.setFilter(filter) }) {
                            Text(filter.label)
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.setFilter(filter) }) {
                            Text(filter.label)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                state.loading -> Centered(Modifier.fillMaxSize()) { CircularProgressIndicator() }

                state.customers.isEmpty() && state.query.isBlank() -> EmptyState(
                    icon = Icons.Filled.People,
                    title = "No customers yet",
                    message = "Add the first customer and their jobs will hang off it.",
                    actionLabel = "Add customer",
                    onAction = onAddCustomer
                )

                state.customers.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Nothing matched",
                    message = "No customer matches \"${state.query}\". Try a phone number or street name."
                )

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardGap)
                ) {
                    items(state.customers, key = { it.id }) { customer ->
                        CustomerRow(
                            customer = customer,
                            statusColor = when (customer.status) {
                                CustomerStatus.LEAD -> statusColors.lead
                                CustomerStatus.ACTIVE -> statusColors.inProgress
                                CustomerStatus.PAST -> statusColors.complete
                                CustomerStatus.DORMANT -> statusColors.cancelled
                            },
                            onClick = { onOpenCustomer(customer.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CustomerRow(
    customer: CustomerEntity,
    statusColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StaffAvatar(name = customer.displayName, seed = customer.sync.syncId, size = 44.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customer.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (customer.isFavourite) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Favourite",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp)
                        )
                    }
                }
                val subtitle = listOfNotNull(
                    customer.city?.takeIf { it.isNotBlank() },
                    Phones.format(customer.primaryPhone).takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StatusChip(label = customer.status.label, color = statusColor)
        }
    }
}

// ---------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------

@Composable
fun CustomerDetailScreen(
    viewModel: CustomerDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenJob: (Long) -> Unit,
    onAddJob: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val customer = state.customer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.displayName ?: "Customer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (customer != null) {
                        IconButton(onClick = viewModel::toggleFavourite) {
                            Icon(
                                if (customer.isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Favourite"
                            )
                        }
                        IconButton(onClick = { onEdit(customer.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (customer != null) {
                FloatingActionButton(onClick = { onAddJob(customer.id) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add job")
                }
            }
        }
    ) { padding ->
        if (customer == null) {
            Centered(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator() }
            return@Scaffold
        }

        val address = Addresses.oneLine(
            customer.street1, customer.street2, customer.city, customer.province, customer.postalCode
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(Icons.Filled.Phone, "Call", customer.primaryPhone != null) {
                    IntentLauncher.dial(context, customer.primaryPhone)
                }
                ActionButton(Icons.Filled.Message, "Text", customer.primaryPhone != null) {
                    IntentLauncher.sms(context, customer.primaryPhone)
                }
                ActionButton(Icons.Filled.Email, "Email", customer.email != null) {
                    IntentLauncher.email(context, customer.email)
                }
                ActionButton(Icons.Filled.Place, "Directions", address.isNotBlank()) {
                    IntentLauncher.directions(context, address)
                }
            }

            if (address.isNotBlank()) {
                SectionHeader("Address")
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = Addresses.multiLine(
                            customer.street1, customer.street2, customer.city,
                            customer.province, customer.postalCode
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(Modifier.height(Dimens.sectionGap))
            }

            // Access notes are tinted because this is the "don't get locked out at
            // 7am" information, and it needs to be findable in one glance.
            if (!customer.gateCode.isNullOrBlank() || !customer.accessNotes.isNullOrBlank()) {
                SectionHeader("Site access")
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        customer.gateCode?.takeIf { it.isNotBlank() }?.let {
                            Text("Code: $it", style = MaterialTheme.typography.titleMedium)
                        }
                        customer.accessNotes?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.sectionGap))
            }

            SectionHeader("Jobs (${state.jobs.size})")
            if (state.jobs.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = "No jobs yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                state.jobs.forEach { job ->
                    Card(
                        onClick = { onOpenJob(job.id) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.cardGap)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(job.jobNumber, style = MaterialTheme.typography.labelMedium)
                            Text(job.title, style = MaterialTheme.typography.titleMedium)
                            Row(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusChip(
                                    label = job.status.label,
                                    color = LocalStatusColors.current.forJob(job.status)
                                )
                                job.startDate?.let {
                                    Text(
                                        Dates.formatShort(it),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            customer.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Dimens.sectionGap))
                SectionHeader("Notes")
                Card(Modifier.fillMaxWidth()) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------------------------------------------------------------------------
// Edit
// ---------------------------------------------------------------------------

@Composable
fun CustomerEditScreen(
    viewModel: CustomerEditViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New customer" else "Edit customer") },
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
            SectionHeader("Who")
            Field("Name", state.displayName, required = true) { v ->
                viewModel.update { it.copy(displayName = v) }
            }
            Field("Company (optional)", state.companyName) { v ->
                viewModel.update { it.copy(companyName = v) }
            }
            EnumPicker("Type", state.customerType, CustomerType.entries) { v ->
                viewModel.update { it.copy(customerType = v) }
            }
            EnumPicker("Status", state.status, CustomerStatus.entries) { v ->
                viewModel.update { it.copy(status = v) }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Contact")
            Field("Phone", state.primaryPhone, keyboard = KeyboardType.Phone) { v ->
                viewModel.update { it.copy(primaryPhone = v) }
            }
            Field("Second phone", state.secondaryPhone, keyboard = KeyboardType.Phone) { v ->
                viewModel.update { it.copy(secondaryPhone = v) }
            }
            Field("Email", state.email, keyboard = KeyboardType.Email) { v ->
                viewModel.update { it.copy(email = v) }
            }
            EnumPicker("Prefers", state.preferredContact, ContactMethod.entries) { v ->
                viewModel.update { it.copy(preferredContact = v) }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Address")
            Field("Street", state.street1) { v -> viewModel.update { it.copy(street1 = v) } }
            Field("Unit / buzzer", state.street2) { v -> viewModel.update { it.copy(street2 = v) } }
            Field("City", state.city) { v -> viewModel.update { it.copy(city = v) } }
            Field("Province", state.province) { v -> viewModel.update { it.copy(province = v) } }
            Field("Postal code", state.postalCode) { v ->
                viewModel.update { it.copy(postalCode = PostalCodes.format(v)) }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Site access")
            Field("Gate / lockbox code", state.gateCode) { v ->
                viewModel.update { it.copy(gateCode = v) }
            }
            Field("Access notes", state.accessNotes, lines = 3) { v ->
                viewModel.update { it.copy(accessNotes = v) }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Other")
            Field("Where they came from", state.referralSource) { v ->
                viewModel.update { it.copy(referralSource = v) }
            }
            Field("Notes", state.notes, lines = 4) { v -> viewModel.update { it.copy(notes = v) } }

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
                Text(if (state.isNew) "Add customer" else "Save changes")
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun Field(
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
            capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Words
            else KeyboardCapitalization.None
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    )
}

/**
 * A button that opens a plain DropdownMenu. ExposedDropdownMenuBox would look
 * slightly better but is still experimental, and this screen is not the place to
 * spend that risk.
 */
@Composable
private fun <T> EnumPicker(
    label: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit
) where T : Enum<T>, T : ca.gpsprobuild.app.domain.model.Labelled {
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 14.dp
            )
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
