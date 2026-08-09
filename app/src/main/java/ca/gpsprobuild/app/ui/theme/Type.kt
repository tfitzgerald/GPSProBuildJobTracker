package ca.gpsprobuild.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ca.gpsprobuild.app.R

/**
 * Two faces, chosen for the job rather than for novelty.
 *
 * Barlow Semi Condensed carries titles, job numbers and money: condensed grotesques
 * are the vernacular of site signage and equipment plates, and the narrower set
 * width means a long job title fits on one line of a phone held in one hand.
 *
 * Inter carries body and form text. It was drawn for screen UI at small sizes,
 * which is what matters when the screen is being read outdoors at arm's length.
 *
 * Both are bundled as real font files, not downloadable fonts — the app has to
 * render correctly in a basement with no signal.
 */

val Barlow = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold)
)

/**
 * Inter ships as a single variable file. minSdk 26 supports variation settings,
 * so one 880 KB asset covers every weight instead of six static files.
 */
val Inter = FontFamily(
    Font(
        R.font.inter_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

/** Tabular figures, so money in a column lines up on the decimal. */
val MoneyTextStyle = TextStyle(
    fontFamily = Barlow,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum"
)

val GpsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp
    ),
    displaySmall = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp, lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)
