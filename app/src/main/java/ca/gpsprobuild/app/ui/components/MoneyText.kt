package ca.gpsprobuild.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import ca.gpsprobuild.app.core.util.Money
import androidx.compose.ui.text.font.FontWeight
import ca.gpsprobuild.app.domain.model.PrivacyMode
import ca.gpsprobuild.app.ui.theme.Barlow

val LocalPrivacyMode = staticCompositionLocalOf { PrivacyMode.FULL }

/**
 * Which bucket a figure belongs to. The distinction matters: a client sitting
 * beside you already knows the contract value, but has no business seeing what the
 * tile cost or what the crew is paid.
 */
enum class MoneyKind {
    /** Contract value, approved amount, deposits, change order amounts. */
    CONTRACT,

    /** Material cost, expenses, labour cost, crew rates, margin. */
    INTERNAL
}

/**
 * The only place in the app that renders currency.
 *
 * Privacy masking lives here rather than in each screen on purpose: a new screen
 * cannot leak a figure by forgetting to check the mode, because there is no other
 * way to draw money.
 */
@Composable
fun MoneyText(
    cents: Long?,
    modifier: Modifier = Modifier,
    kind: MoneyKind = MoneyKind.INTERNAL,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    showZeroAsDash: Boolean = false,
    signed: Boolean = false
) {
    val privacy = LocalPrivacyMode.current
    val hidden = when (kind) {
        MoneyKind.CONTRACT -> privacy.hidesContractValue
        MoneyKind.INTERNAL -> privacy.hidesInternalCost
    }

    val text = when {
        hidden -> "— — —"
        cents == null -> "—"
        cents == 0L && showZeroAsDash -> "—"
        signed -> Money.formatSigned(cents)
        else -> Money.format(cents)
    }

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontFamily = Barlow,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum"
        ),
        color = if (hidden && color == Color.Unspecified) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            color
        },
        maxLines = 1
    )
}
