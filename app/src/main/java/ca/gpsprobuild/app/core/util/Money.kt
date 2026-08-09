package ca.gpsprobuild.app.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * All money in this app is a Long count of cents. Never a Double, never a Float —
 * a job with forty material lines will drift by real dollars otherwise, and a
 * contractor arguing a final invoice cannot afford a rounding artefact.
 */
object Money {

    private val canadaFormat: NumberFormat
        get() = NumberFormat.getCurrencyInstance(Locale.CANADA)

    fun format(cents: Long): String = canadaFormat.format(BigDecimal(cents).movePointLeft(2))

    fun formatSigned(cents: Long): String =
        if (cents >= 0) "+${format(cents)}" else "-${format(-cents)}"

    /** Compact form for dense cards: $1.2k, $14.8k. */
    fun formatCompact(cents: Long): String {
        val dollars = cents / 100.0
        return when {
            dollars >= 1_000_000 -> "$%.1fM".format(dollars / 1_000_000)
            dollars >= 10_000 -> "$%.1fk".format(dollars / 1_000)
            dollars >= 1_000 -> "$%.1fk".format(dollars / 1_000)
            else -> "$%,.0f".format(dollars)
        }
    }

    /** Parses "1,234.56", "$1234.56" or "1234" into cents. Null if unparseable. */
    fun parseToCents(input: String): Long? {
        val cleaned = input.replace(Regex("[^0-9.\\-]"), "")
        if (cleaned.isBlank() || cleaned == "-" || cleaned == ".") return null
        return runCatching {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        }.getOrNull()
    }

    /** Tax on a base amount, rounded half-up at the cent. */
    fun taxCents(baseCents: Long, ratePercent: Double): Long =
        BigDecimal(baseCents)
            .multiply(BigDecimal(ratePercent))
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
            .toLong()

    fun applyMarkup(costCents: Long, markupPercent: Double): Long =
        BigDecimal(costCents)
            .multiply(BigDecimal(100 + markupPercent))
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
            .toLong()

    /**
     * Margin as a fraction of revenue. Returns null rather than zero when there is
     * no revenue, so the UI can show "—" instead of a misleading 0%.
     */
    fun marginFraction(revenueCents: Long, costCents: Long): Double? =
        if (revenueCents <= 0) null else (revenueCents - costCents).toDouble() / revenueCents

    fun hoursToCents(hours: Double, rateCents: Long): Long =
        BigDecimal(hours).multiply(BigDecimal(rateCents)).setScale(0, RoundingMode.HALF_UP).toLong()
}
