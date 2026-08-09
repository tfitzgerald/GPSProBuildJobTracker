package ca.gpsprobuild.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gpsprobuild.app.core.util.Initials
import ca.gpsprobuild.app.domain.model.Labelled
import ca.gpsprobuild.app.ui.theme.Barlow
import ca.gpsprobuild.app.ui.theme.Dimens
import ca.gpsprobuild.app.ui.theme.GpsProBuildTheme

/**
 * The wordmark: GPS in bold, ProBuild in medium, both condensed. Set as one
 * annotated string so the weight shift reads as a single mark rather than two
 * words that happen to sit beside each other.
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.secondary
) {
    Text(
        text = wordmarkText(color, accent),
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier
    )
}

private fun wordmarkText(color: Color, accent: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)) {
        append("GPS")
    }
    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
        append(" ProBuild")
    }
}

/** Small filled chip carrying a status colour. Used for jobs, tasks and materials. */
@Composable
fun StatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Box(
        modifier = modifier
            .background(
                color = if (filled) color else color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50)
            )
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) Color.White else color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatusChip(status: Labelled, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) =
    StatusChip(status.label, color, modifier, filled)

/**
 * Deterministic avatar. Colour derives from the id, so the same person is the same
 * colour on every screen and on every device without storing a photo.
 */
@Composable
fun StaffAvatar(
    name: String,
    seed: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val palette = listOf(
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A), Color(0xFFAD1457),
        Color(0xFF00838F), Color(0xFF4E342E), Color(0xFFC62828), Color(0xFF37474F)
    )
    val color = palette[(seed.hashCode().let { if (it < 0) -it else it }) % palette.size]

    Box(
        modifier = modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = Initials.of(name),
            color = Color.White,
            fontFamily = Barlow,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        trailing?.invoke()
    }
}

/**
 * An empty screen is an invitation to act, not an apology. Every empty state names
 * what goes here and offers the action that puts something in it.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 24.dp)
            ) { Text(actionLabel) }
        }
    }
}

/** Compact figure card used across the dashboard. */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComponentsPreview() {
    GpsProBuildTheme {
        Column(Modifier.padding(Dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandWordmark()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("In progress", Color(0xFF1565C0))
                StatusChip("Overdue", Color(0xFFB3261E), filled = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StaffAvatar("Gordon Fitzgerald", seed = "1")
                StaffAvatar("Dave Mercer", seed = "2")
            }
            StatCard(value = "7", label = "Open jobs")
        }
    }
}
