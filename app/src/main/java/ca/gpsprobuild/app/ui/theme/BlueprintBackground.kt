package ca.gpsprobuild.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A drafting-paper ground for the whole app.
 *
 * The reference is a working architectural drawing rather than blueprint-print
 * novelty: a fine construction grid, a heavier module line every fifth square, and
 * registration ticks at the sheet corners. It is drawn at very low contrast —
 * roughly 3–5% against the surface — because this sits behind form fields that get
 * read outdoors in poor light, and a background that competes with the text would
 * be a decoration that makes the app worse.
 *
 * Drawn with `drawBehind` so it costs one draw pass and never enters composition.
 */
@Composable
fun Modifier.blueprintBackground(
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val dark = isSystemInDarkTheme()
    // Ink on paper in light, chalk on board in dark.
    val minorLine = if (dark) Color.White.copy(alpha = 0.030f) else IronworkNavy.copy(alpha = 0.038f)
    val majorLine = if (dark) Color.White.copy(alpha = 0.055f) else IronworkNavy.copy(alpha = 0.075f)
    val tickLine = if (dark) LevelAmberLight.copy(alpha = 0.16f) else LevelAmber.copy(alpha = 0.16f)

    return this.drawBehind {
        val minor = 12.dp.toPx()
        val majorEvery = 5
        val strokeMinor = 0.7f
        val strokeMajor = 1.1f

        var index = 0
        var x = 0f
        while (x <= size.width) {
            val isMajor = index % majorEvery == 0
            drawLine(
                color = if (isMajor) majorLine else minorLine,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) strokeMajor else strokeMinor
            )
            x += minor
            index++
        }

        index = 0
        var y = 0f
        while (y <= size.height) {
            val isMajor = index % majorEvery == 0
            drawLine(
                color = if (isMajor) majorLine else minorLine,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (isMajor) strokeMajor else strokeMinor
            )
            y += minor
            index++
        }

        // Registration ticks, as on a drawing sheet: short amber marks set in from
        // each corner. Two strokes each, so they read as crosshairs rather than
        // decoration, and they anchor the grid to the sheet edge.
        val inset = 20.dp.toPx()
        val tick = 14.dp.toPx()
        val corners = listOf(
            Offset(inset, inset),
            Offset(size.width - inset, inset),
            Offset(inset, size.height - inset),
            Offset(size.width - inset, size.height - inset)
        )
        corners.forEach { corner ->
            drawLine(
                color = tickLine,
                start = Offset(corner.x - tick / 2, corner.y),
                end = Offset(corner.x + tick / 2, corner.y),
                strokeWidth = 1.4f
            )
            drawLine(
                color = tickLine,
                start = Offset(corner.x, corner.y - tick / 2),
                end = Offset(corner.x, corner.y + tick / 2),
                strokeWidth = 1.4f
            )
        }
    }
}
