package ca.gpsprobuild.app.ui.screens.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ca.gpsprobuild.app.BuildConfig
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.theme.Dimens

/**
 * The hub for everything that is not a daily tab. Entries that are not built yet
 * say so plainly rather than being hidden — knowing what is coming is more useful
 * than a shorter list.
 */
@Composable
fun MoreScreen(
    onOpenCrew: () -> Unit,
    onOpenBuyList: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding)
    ) {
        Spacer(Modifier.height(16.dp))
        SectionHeader("Manage")
        HubRow(Icons.Filled.Group, "Crew", "People and subs, rates and insurance", onOpenCrew)
        HubRow(
            Icons.Filled.ShoppingCart,
            "Buy list",
            "Everything still to get, grouped by supplier",
            onOpenBuyList
        )

        Spacer(Modifier.height(Dimens.sectionGap))
        SectionHeader("Not built yet")
        HubRow(Icons.Filled.Sync, "Field sync", "Send work out, take reports back in", null)
        HubRow(Icons.Filled.Settings, "Settings and backup", "Company profile, PDF reports, ZIP backup", null)

        Spacer(Modifier.height(Dimens.sectionGap))
        Text(
            "GPS ProBuild ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    val card: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (onClick != null) {
            Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                content()
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { content() }
        }
    }

    card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (onClick != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
