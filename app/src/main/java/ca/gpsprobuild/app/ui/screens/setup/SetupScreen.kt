package ca.gpsprobuild.app.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.gpsprobuild.app.domain.model.DeviceRole
import ca.gpsprobuild.app.ui.components.BrandWordmark
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.GpsProBuildTheme

/**
 * First run. Two decisions only: what to call this phone, and whether it is the
 * owner's book of record or a field device carrying assigned work.
 *
 * The role choice is explained in plain terms rather than named and left to guess,
 * because getting it wrong on a crew phone means somebody's hours end up in a
 * database nobody syncs from.
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onSetupComplete()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.screenPadding)
        ) {
            Spacer(Modifier.height(32.dp))
            BrandWordmark()
            Text(
                text = "Renovation. Managed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(40.dp))

            Text("Name this phone", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Shown on packets you send, so the other end knows where changes came from.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            OutlinedTextField(
                value = state.deviceName,
                onValueChange = viewModel::setDeviceName,
                label = { Text("Device name") },
                placeholder = { Text("Gordon's Pixel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            Text("What is this phone for?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            RoleOption(
                icon = Icons.Filled.HomeWork,
                title = "Owner",
                body = "Holds every customer, job and figure. Sends work out to the crew and " +
                    "takes their reports back in. There should only be one of these.",
                selected = state.role == DeviceRole.OWNER,
                onClick = { viewModel.setRole(DeviceRole.OWNER) }
            )
            Spacer(Modifier.height(10.dp))
            RoleOption(
                icon = Icons.Filled.Engineering,
                title = "Field",
                body = "Carries only the jobs assigned to it. Logs hours, photos, tasks and " +
                    "materials, then sends a report back. Never receives cost or pay figures.",
                selected = state.role == DeviceRole.FIELD,
                onClick = { viewModel.setRole(DeviceRole.FIELD) }
            )

            if (state.role == DeviceRole.OWNER) {
                Spacer(Modifier.height(32.dp))
                Text("Set an owner PIN", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Six digits. Required to switch a phone back to Owner, so a field " +
                        "phone can't be flipped over to see margins.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = state.pin,
                    onValueChange = viewModel::setPin,
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.pinConfirm,
                    onValueChange = viewModel::setPinConfirm,
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = state.pinConfirm.isNotEmpty() && state.pinConfirm != state.pin,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = viewModel::finish,
                enabled = state.canFinish,
                modifier = Modifier.fillMaxWidth().height(Dimens.buttonHeight)
            ) {
                Text("Start using GPS ProBuild")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RoleOption(
    icon: ImageVector,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, border, MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (selected) {
            Column(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun SetupPreview() {
    GpsProBuildTheme { /* Preview renders the static layout; VM is supplied at runtime. */ }
}
