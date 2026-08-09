package ca.gpsprobuild.app.ui.screens.setup

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.domain.model.DeviceRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val deviceName: String = "",
    val role: DeviceRole = DeviceRole.OWNER,
    val pin: String = "",
    val pinConfirm: String = "",
    val error: String? = null,
    val finished: Boolean = false
) {
    val canFinish: Boolean
        get() = deviceName.isNotBlank() && when (role) {
            DeviceRole.OWNER -> pin.length == 6 && pin == pinConfirm
            DeviceRole.FIELD -> true
        }
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        // A sensible default beats an empty field: most people keep it.
        SetupUiState(deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
    )
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        // Generate the device UUID up front — everything in the sync layer hangs
        // off it, and it must exist before the first row is ever written.
        viewModelScope.launch { settings.ensureDeviceId() }
    }

    fun setDeviceName(value: String) = _state.update { it.copy(deviceName = value, error = null) }
    fun setRole(value: DeviceRole) = _state.update { it.copy(role = value, error = null) }
    fun setPin(value: String) =
        _state.update { it.copy(pin = value.filter(Char::isDigit).take(6), error = null) }
    fun setPinConfirm(value: String) =
        _state.update { it.copy(pinConfirm = value.filter(Char::isDigit).take(6), error = null) }

    fun finish() {
        val current = _state.value
        if (!current.canFinish) return
        viewModelScope.launch {
            runCatching {
                settings.ensureDeviceId()
                if (current.role == DeviceRole.OWNER) settings.setOwnerPin(current.pin)
                settings.completeSetup(current.deviceName.trim(), current.role)
            }.onSuccess {
                _state.update { it.copy(finished = true) }
            }.onFailure { error ->
                _state.update { it.copy(error = "Could not save setup: ${error.message}") }
            }
        }
    }
}
