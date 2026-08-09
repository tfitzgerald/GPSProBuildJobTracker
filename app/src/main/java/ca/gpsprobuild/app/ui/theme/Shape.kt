package ca.gpsprobuild.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val GpsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),   // cards
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp) // sheets and dialogs
)

/** Glove-friendly minimums. Referenced rather than re-guessed per screen. */
object Dimens {
    val screenPadding = 16.dp
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val touchTarget = 48.dp
    val buttonHeight = 52.dp
    val listRowHeight = 68.dp
    val chipHeight = 32.dp
}
