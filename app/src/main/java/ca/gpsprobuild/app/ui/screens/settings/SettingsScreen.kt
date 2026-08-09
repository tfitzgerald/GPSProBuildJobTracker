@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.BuildConfig
import ca.gpsprobuild.app.data.file.BackupManager
import ca.gpsprobuild.app.data.file.RestoreResult
import ca.gpsprobuild.app.data.prefs.AppSettings
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.data.prefs.ThemeMode
import ca.gpsprobuild.app.domain.model.PrivacyMode
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val message: String? = null,
    val busy: Boolean = false,
    val pendingRestore: RestoreResult.Ready? = null,
    val restoreComplete: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _backup = MutableStateFlow(BackupUiState())
    val backup: StateFlow<BackupUiState> = _backup.asStateFlow()

    fun suggestedBackupName() = backupManager.suggestedFileName()

    fun saveCompanyProfile(
        name: String, phone: String, email: String, website: String,
        street: String, city: String, province: String, postal: String, hst: String
    ) {
        viewModelScope.launch {
            settingsRepository.updateCompanyProfile(
                name, phone, email, website, street, city, province, postal, hst
            )
            _backup.update { it.copy(message = "Company profile saved") }
        }
    }

    fun setJobNumbering(prefix: String, next: Int) {
        viewModelScope.launch { settingsRepository.setJobNumbering(prefix, next) }
    }

    fun setPrivacyMode(mode: PrivacyMode) {
        viewModelScope.launch { settingsRepository.setPrivacyMode(mode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setUpdateCheck(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUpdateCheckEnabled(enabled) }
    }

    fun writeBackup(target: android.net.Uri) {
        viewModelScope.launch {
            _backup.update { it.copy(busy = true, message = null) }
            val deviceName = settings.value.deviceName
            backupManager.writeBackup(target, BuildConfig.VERSION_NAME, deviceName)
                .onSuccess { count ->
                    settingsRepository.markBackupTaken(System.currentTimeMillis())
                    _backup.update {
                        it.copy(busy = false, message = "Backup written — $count files")
                    }
                }
                .onFailure { error ->
                    _backup.update { it.copy(busy = false, message = "Backup failed: ${error.message}") }
                }
        }
    }

    fun inspectBackup(source: android.net.Uri) {
        viewModelScope.launch {
            _backup.update { it.copy(busy = true, message = null) }
            when (val result = backupManager.inspect(source)) {
                is RestoreResult.Ready ->
                    _backup.update { it.copy(busy = false, pendingRestore = result) }
                is RestoreResult.Failed ->
                    _backup.update { it.copy(busy = false, message = result.reason) }
            }
        }
    }

    fun confirmRestore(source: android.net.Uri) {
        viewModelScope.launch {
            _backup.update { it.copy(busy = true, pendingRestore = null) }
            backupManager.restore(source)
                .onSuccess { _backup.update { it.copy(busy = false, restoreComplete = true) } }
                .onFailure { error ->
                    _backup.update { it.copy(busy = false, message = "Restore failed: ${error.message}") }
                }
        }
    }

    fun cancelRestore() = _backup.update { it.copy(pendingRestore = null) }
    fun clearMessage() = _backup.update { it.copy(message = null) }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()

    var name by remember(settings.businessName) { mutableStateOf(settings.businessName) }
    var phone by remember(settings.businessPhone) { mutableStateOf(settings.businessPhone) }
    var email by remember(settings.businessEmail) { mutableStateOf(settings.businessEmail) }
    var website by remember(settings.businessWebsite) { mutableStateOf(settings.businessWebsite) }
    var street by remember(settings.businessStreet) { mutableStateOf(settings.businessStreet) }
    var city by remember(settings.businessCity) { mutableStateOf(settings.businessCity) }
    var province by remember(settings.businessProvince) { mutableStateOf(settings.businessProvince) }
    var postal by remember(settings.businessPostalCode) { mutableStateOf(settings.businessPostalCode) }
    var hst by remember(settings.hstNumber) { mutableStateOf(settings.hstNumber) }
    var prefix by remember(settings.jobNumberPrefix) { mutableStateOf(settings.jobNumberPrefix) }
    var nextNumber by remember(settings.jobNumberNext) { mutableStateOf(settings.jobNumberNext.toString()) }

    var restoreSource by remember { mutableStateOf<android.net.Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::writeBackup) }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            restoreSource = it
            viewModel.inspectBackup(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            backup.message?.let {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                    }
                }
            }

            SectionHeader("Company profile")
            Text(
                "These details print at the top of every quote, work order and job summary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            SettingField("Business name", name) { name = it }
            SettingField("Phone", phone, KeyboardType.Phone) { phone = it }
            SettingField("Email", email, KeyboardType.Email) { email = it }
            SettingField("Website", website) { website = it }
            SettingField("Street", street) { street = it }
            SettingField("City", city) { city = it }
            SettingField("Province", province) { province = it }
            SettingField("Postal code", postal) { postal = it }
            SettingField("HST number", hst) { hst = it }
            FilledTonalButton(
                onClick = {
                    viewModel.saveCompanyProfile(
                        name, phone, email, website, street, city, province, postal, hst
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save company profile") }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Job numbering")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Prefix") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = nextNumber,
                    onValueChange = { nextNumber = it.filter(Char::isDigit) },
                    label = { Text("Next number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Next job will be ${prefix}-${java.time.LocalDate.now().year}-" +
                    "%04d".format(nextNumber.toIntOrNull() ?: 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            OutlinedButton(
                onClick = { viewModel.setJobNumbering(prefix, nextNumber.toIntOrNull() ?: 1) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save numbering") }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Showing money")
            PrivacyMode.entries.forEach { mode ->
                val selected = settings.privacyMode == mode
                Card(
                    onClick = { viewModel.setPrivacyMode(mode) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(mode.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    if (settings.themeMode == mode) {
                        FilledTonalButton(onClick = { viewModel.setThemeMode(mode) }) {
                            Text(mode.label)
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.setThemeMode(mode) }) {
                            Text(mode.label)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("Backup")
            Text(
                "Everything — database, photos and documents — as one zip file you keep " +
                    "wherever you like. This is the only recovery path, so take one regularly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            FilledTonalButton(
                onClick = { createBackup.launch(viewModel.suggestedBackupName()) },
                enabled = !backup.busy,
                modifier = Modifier.fillMaxWidth().height(Dimens.buttonHeight)
            ) { Text(if (backup.busy) "Working…" else "Back up now") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !backup.busy,
                modifier = Modifier.fillMaxWidth().height(Dimens.buttonHeight)
            ) { Text("Restore from backup") }

            Spacer(Modifier.height(Dimens.sectionGap))
            SectionHeader("About")
            Text(
                "GPS ProBuild ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${settings.deviceRole.label} device · ${settings.deviceName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))
        }
    }

    // Restore replaces everything, so it asks once, plainly, with the numbers.
    backup.pendingRestore?.let { ready ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Replace everything on this phone?") },
            text = {
                Text(
                    "This backup was made on ${ready.manifest.deviceName.ifBlank { "another device" }} " +
                        "and holds ${ready.manifest.customerCount} customers, " +
                        "${ready.manifest.jobCount} jobs and ${ready.manifest.photoCount} photos.\n\n" +
                        "Everything currently on this phone will be replaced. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { restoreSource?.let(viewModel::confirmRestore) }) {
                    Text("Replace everything")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") }
            }
        )
    }

    // Room still holds a handle on the old database files, so the process has to
    // come down rather than carry on against swapped-out storage.
    if (backup.restoreComplete) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Restore complete") },
            text = { Text("GPS ProBuild needs to close and reopen to finish.") },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finishAffinity() }) {
                    Text("Close the app")
                }
            }
        )
    }
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}
