package ca.gpsprobuild.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.gpsprobuild.app.BuildConfig
import ca.gpsprobuild.app.core.util.Dates
import ca.gpsprobuild.app.ui.components.BrandWordmark
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.components.StatCard
import ca.gpsprobuild.app.ui.components.StatusChip
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.LocalStatusColors
import java.time.LocalDate

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                BrandWordmark()
                Text(
                    text = Dates.formatWithDay(LocalDate.now()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(
                label = state.settings.deviceRole.label,
                color = statusColors.scheduled
            )
        }

        Spacer(Modifier.height(Dimens.sectionGap))

        SectionHeader("At a glance")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            StatCard(
                value = state.openJobCount.toString(),
                label = "Open jobs",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = state.customerCount.toString(),
                label = "Customers",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(Dimens.cardGap))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            StatCard(
                value = state.overdueTaskCount.toString(),
                label = "Overdue tasks",
                accent = if (state.overdueTaskCount > 0) statusColors.overdue
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = state.dueTodayCount.toString(),
                label = "Due today",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(Dimens.cardGap))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
            StatCard(
                value = state.materialsNeeded.toString(),
                label = "To buy",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = state.photoCount.toString(),
                label = "Photos",
                modifier = Modifier.weight(1f)
            )
        }

        if (state.settings.isField) {
            Spacer(Modifier.height(Dimens.sectionGap))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.unsentChanges == 0) "Everything sent"
                        else "${state.unsentChanges} changes not yet sent",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (state.unsentChanges == 0) {
                            "Nothing waiting to go back to the office."
                        } else {
                            "Build a field report when you have signal."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.sectionGap))

        // Foundation build marker. Replaced by the real Today / Needs attention
        // sections in step 10, once the data they summarise actually exists.
        SectionHeader("Build")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Foundation build", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Schema, theme, roles and navigation are in place. Customers and " +
                        "jobs come next.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    text = "Device ${state.settings.deviceName.ifBlank { "unnamed" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
