package ca.gpsprobuild.app.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import ca.gpsprobuild.app.core.util.Addresses
import ca.gpsprobuild.app.core.util.Dates
import ca.gpsprobuild.app.core.util.Hours
import ca.gpsprobuild.app.core.util.Money
import ca.gpsprobuild.app.core.util.Phones
import ca.gpsprobuild.app.data.local.entity.CustomerEntity
import ca.gpsprobuild.app.data.local.entity.JobEntity
import ca.gpsprobuild.app.data.local.entity.JobEventEntity
import ca.gpsprobuild.app.data.local.entity.MaterialEntity
import ca.gpsprobuild.app.data.local.entity.StaffEntity
import ca.gpsprobuild.app.data.local.entity.TaskEntity
import ca.gpsprobuild.app.data.prefs.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Letter at 72dpi: 612 x 792 points. Everything is laid out in points from the top
 * left, which is how PdfDocument thinks, so no coordinate translation is needed.
 */
private const val PAGE_WIDTH = 612
private const val PAGE_HEIGHT = 792
private const val MARGIN = 48f
private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

/**
 * A thin cursor over PdfDocument that tracks the vertical position and starts a
 * new page before anything would run off the bottom. Without this, long material
 * lists silently draw past the page edge and simply vanish.
 */
private class PdfCanvasCursor(private val document: PdfDocument) {
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    var y = MARGIN
        private set

    val body = Paint().apply { textSize = 10f; color = 0xFF1A1C1E.toInt() }
    val bodyMuted = Paint().apply { textSize = 9f; color = 0xFF5F6368.toInt() }
    val bold = Paint().apply {
        textSize = 11f; color = 0xFF14314F.toInt()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val heading = Paint().apply {
        textSize = 15f; color = 0xFF14314F.toInt()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val title = Paint().apply {
        textSize = 22f; color = 0xFF14314F.toInt()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rule = Paint().apply { color = 0xFFD0D4D8.toInt(); strokeWidth = 0.7f }

    fun newPage() {
        page?.let { document.finishPage(it) }
        pageNumber += 1
        val created = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        page = created
        canvas = created.canvas
        y = MARGIN
        stampFooter(footer)
    }

    var footer: String = ""

    private fun ensure(space: Float) {
        if (canvas == null) newPage()
        if (y + space > PAGE_HEIGHT - MARGIN - 20f) newPage()
    }

    fun text(value: String, paint: Paint = body, indent: Float = 0f, lineGap: Float = 4f) {
        ensure(paint.textSize + lineGap)
        // Wrap by measuring rather than guessing a character count — proportional
        // fonts make a fixed cutoff either waste space or overflow.
        var remaining = value
        val available = CONTENT_WIDTH - indent
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, available, null)
            val slice = remaining.take(count.coerceAtLeast(1))
            val breakAt = if (count < remaining.length) {
                slice.lastIndexOf(' ').takeIf { it > 0 } ?: slice.length
            } else {
                slice.length
            }
            val line = remaining.take(breakAt).trimEnd()
            ensure(paint.textSize + lineGap)
            canvas?.drawText(line, MARGIN + indent, y + paint.textSize, paint)
            y += paint.textSize + lineGap
            remaining = remaining.drop(breakAt).trimStart()
        }
    }

    /** Label left, value right — the shape most of these reports need. */
    fun row(label: String, value: String, labelPaint: Paint = bodyMuted, valuePaint: Paint = body) {
        ensure(valuePaint.textSize + 5f)
        canvas?.drawText(label, MARGIN, y + valuePaint.textSize, labelPaint)
        val width = valuePaint.measureText(value)
        canvas?.drawText(value, PAGE_WIDTH - MARGIN - width, y + valuePaint.textSize, valuePaint)
        y += valuePaint.textSize + 5f
    }

    fun divider(gap: Float = 8f) {
        ensure(gap * 2)
        y += gap
        canvas?.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += gap
    }

    fun space(amount: Float) {
        ensure(amount)
        y += amount
    }

    fun sectionHeading(value: String) {
        space(10f)
        text(value.uppercase(), heading)
        divider(4f)
    }

    /**
     * PdfDocument pages are immutable once finished, so a footer has to be drawn
     * before the page closes rather than stamped on at the end. Each page gets it
     * as it is created.
     */
    fun finish() {
        page?.let { document.finishPage(it) }
        page = null
        canvas = null
    }

    fun stampFooter(text: String) {
        canvas?.drawText(text, MARGIN, PAGE_HEIGHT - MARGIN + 12f, bodyMuted)
    }
}

data class QuoteLine(val description: String, val amountCents: Long)

@Singleton
class PdfReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun outputDir(): File = File(context.filesDir, "exports").apply { mkdirs() }

    private fun PdfCanvasCursor.companyHeader(settings: AppSettings, documentTitle: String) {
        newPage()
        text("GPS ProBuild", title)
        text(settings.businessName, bold)
        val address = Addresses.oneLine(
            settings.businessStreet, null, settings.businessCity,
            settings.businessProvince, settings.businessPostalCode
        )
        if (address.isNotBlank()) text(address, bodyMuted)
        val contact = listOfNotNull(
            Phones.format(settings.businessPhone).takeIf { it.isNotBlank() },
            settings.businessEmail.takeIf { it.isNotBlank() }
        ).joinToString("  ·  ")
        if (contact.isNotBlank()) text(contact, bodyMuted)
        if (settings.hstNumber.isNotBlank()) text("HST ${settings.hstNumber}", bodyMuted)

        space(8f)
        text(documentTitle, heading)
        text("Generated ${Dates.format(LocalDate.now())}", bodyMuted)
        divider()
    }

    private fun PdfCanvasCursor.customerBlock(customer: CustomerEntity?, job: JobEntity) {
        sectionHeading("Client and site")
        customer?.let {
            text(it.displayName, bold)
            val address = Addresses.oneLine(
                it.street1, it.street2, it.city, it.province, it.postalCode
            )
            if (address.isNotBlank()) text(address)
            it.primaryPhone?.let { phone -> text(Phones.format(phone), bodyMuted) }
        }
        space(4f)
        row("Job number", job.jobNumber)
        row("Job", job.title)
        row("Type", job.jobType.label)
        job.startDate?.let { row("Start", Dates.format(it)) }
        job.targetEndDate?.let { row("Target finish", Dates.format(it)) }
    }

    private suspend fun write(fileName: String, block: (PdfCanvasCursor) -> Unit): File =
        withContext(Dispatchers.IO) {
            val document = PdfDocument()
            val cursor = PdfCanvasCursor(document)
            cursor.footer = "GPS Probuild Inc. — Pickering, Ontario"
            block(cursor)
            cursor.finish()
            val file = File(outputDir(), fileName)
            file.outputStream().use { document.writeTo(it) }
            document.close()
            file
        }

    /**
     * The full record: details, tasks, materials, hours and timeline. This is the
     * document that answers a warranty question or an insurance adjuster two years
     * from now, so it errs toward including too much rather than too little.
     */
    suspend fun jobSummary(
        settings: AppSettings,
        job: JobEntity,
        customer: CustomerEntity?,
        tasks: List<TaskEntity>,
        materials: List<MaterialEntity>,
        events: List<JobEventEntity>,
        crew: List<StaffEntity>,
        hoursLogged: Double,
        showCosts: Boolean
    ): File = write("JobSummary-${job.jobNumber}.pdf") { c ->
        c.companyHeader(settings, "Job summary")
        c.customerBlock(customer, job)

        job.scopeOfWork?.takeIf { it.isNotBlank() }?.let {
            c.sectionHeading("Scope of work")
            c.text(it)
        }

        if (tasks.isNotEmpty()) {
            val done = tasks.count { it.status.isFinished }
            c.sectionHeading("Work completed ($done of ${tasks.size})")
            tasks.groupBy { it.phase }.toList().sortedBy { it.first.order }
                .forEach { (phase, phaseTasks) ->
                    c.text(phase.label, c.bold)
                    phaseTasks.forEach { task ->
                        val mark = if (task.status.isFinished) "[x]" else "[ ]"
                        c.text("$mark ${task.title}", indent = 10f)
                    }
                    c.space(4f)
                }
        }

        if (materials.isNotEmpty()) {
            c.sectionHeading("Materials")
            materials.forEach { material ->
                val qty = if (material.quantity % 1.0 == 0.0) {
                    material.quantity.toInt().toString()
                } else {
                    "%.2f".format(material.quantity)
                }
                if (showCosts && material.unitCostCents != null) {
                    c.row(
                        "$qty ${material.unit.short}  ${material.name}",
                        Money.format(material.lineTotalCents)
                    )
                } else {
                    c.text("$qty ${material.unit.short}  ${material.name}")
                }
            }
        }

        if (crew.isNotEmpty() || hoursLogged > 0) {
            c.sectionHeading("Crew and hours")
            crew.forEach { c.text("${it.fullName} — ${it.role.label}") }
            if (hoursLogged > 0) c.row("Total hours", Hours.format(hoursLogged))
        }

        if (events.isNotEmpty()) {
            c.sectionHeading("Job log")
            events.sortedBy { it.occurredAt }.forEach { event ->
                c.text(Dates.formatDateTime(event.occurredAt), c.bodyMuted)
                c.text(event.title, indent = 10f)
                event.body?.takeIf { it.isNotBlank() }?.let { c.text(it, c.bodyMuted, indent = 10f) }
            }
        }
    }

    /** Priced quote. Tax rate comes from settings so it is right for the province. */
    suspend fun quote(
        settings: AppSettings,
        job: JobEntity,
        customer: CustomerEntity?,
        lines: List<QuoteLine>
    ): File = write("Quote-${job.jobNumber}.pdf") { c ->
        c.companyHeader(settings, "Quotation")
        c.customerBlock(customer, job)

        job.scopeOfWork?.takeIf { it.isNotBlank() }?.let {
            c.sectionHeading("Scope of work")
            c.text(it)
        }

        c.sectionHeading("Price")
        val subtotal = lines.sumOf { it.amountCents }
        lines.forEach { c.row(it.description, Money.format(it.amountCents)) }
        c.divider(4f)
        c.row("Subtotal", Money.format(subtotal))
        val tax = Money.taxCents(subtotal, settings.taxRatePercent)
        c.row("HST (${"%.0f".format(settings.taxRatePercent)}%)", Money.format(tax))
        c.row("Total", Money.format(subtotal + tax), c.bold, c.bold)

        if (settings.quoteTermsText.isNotBlank()) {
            c.sectionHeading("Terms")
            c.text(settings.quoteTermsText, c.bodyMuted)
        }

        c.space(24f)
        c.sectionHeading("Acceptance")
        c.space(20f)
        c.text("Signature: ______________________________    Date: ______________")
    }

    /** For the crew, not the client — no prices, but the access notes they need. */
    suspend fun workOrder(
        settings: AppSettings,
        job: JobEntity,
        customer: CustomerEntity?,
        tasks: List<TaskEntity>,
        materials: List<MaterialEntity>,
        crew: List<StaffEntity>
    ): File = write("WorkOrder-${job.jobNumber}.pdf") { c ->
        c.companyHeader(settings, "Work order")
        c.customerBlock(customer, job)

        customer?.let {
            val access = listOfNotNull(
                it.gateCode?.takeIf { code -> code.isNotBlank() }?.let { code -> "Code: $code" },
                it.accessNotes?.takeIf { note -> note.isNotBlank() }
            )
            if (access.isNotEmpty()) {
                c.sectionHeading("Site access")
                access.forEach { line -> c.text(line, c.bold) }
            }
        }

        if (crew.isNotEmpty()) {
            c.sectionHeading("Crew")
            crew.forEach { c.text("${it.fullName} — ${it.role.label}") }
        }

        c.sectionHeading("Tasks")
        tasks.groupBy { it.phase }.toList().sortedBy { it.first.order }
            .forEach { (phase, phaseTasks) ->
                c.text(phase.label, c.bold)
                phaseTasks.forEach { task ->
                    c.text("[  ]  ${task.title}", indent = 10f)
                }
                c.space(4f)
            }

        if (materials.isNotEmpty()) {
            c.sectionHeading("Materials")
            materials.forEach { material ->
                val qty = if (material.quantity % 1.0 == 0.0) {
                    material.quantity.toInt().toString()
                } else {
                    "%.2f".format(material.quantity)
                }
                c.text("[  ]  $qty ${material.unit.short}  ${material.name}")
            }
        }
    }

    /** Printable pick list, grouped by supplier, with a checkbox column. */
    suspend fun pickList(
        settings: AppSettings,
        grouped: List<Pair<String, List<Pair<MaterialEntity, String>>>>
    ): File = write("PickList-${LocalDate.now()}.pdf") { c ->
        c.companyHeader(settings, "Material pick list")
        grouped.forEach { (supplier, rows) ->
            c.sectionHeading(supplier)
            rows.forEach { (material, jobNumber) ->
                val qty = if (material.quantity % 1.0 == 0.0) {
                    material.quantity.toInt().toString()
                } else {
                    "%.2f".format(material.quantity)
                }
                c.row("[  ]  $qty ${material.unit.short}  ${material.name}", jobNumber)
            }
        }
    }
}
