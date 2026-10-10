package be.equily.app.data

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import be.equily.app.R
import be.equily.app.domain.*
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class Reports(private val context: Context) {
    fun rows(snapshot: Snapshot, month: YearMonth, annual: Boolean, language: String): List<List<String>> {
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
        fun label(resource: Int) = localized.getString(resource)
        fun amount(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString()
        val dates = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        fun inPeriod(date: java.time.LocalDate) = if (annual) date.year == month.year else YearMonth.from(date) == month
        fun party(value: Party) = label(when (value) { Party.SELF -> R.string.self; Party.OTHER -> R.string.other; Party.SHARED -> R.string.shared; Party.UNKNOWN -> R.string.unknown })
        fun status(value: EntryStatus) = label(when (value) { EntryStatus.DRAFT -> R.string.draft; EntryStatus.DECLARED -> R.string.declared; EntryStatus.ACCEPTED -> R.string.accepted; EntryStatus.CONTESTED -> R.string.contested; EntryStatus.ARCHIVED -> R.string.archived })
        fun kind(value: PaymentKind) = label(when (value) { PaymentKind.SUPPLIER -> R.string.supplier; PaymentKind.TRANSFER -> R.string.transfer; PaymentKind.CONTRIBUTION -> R.string.contribution; PaymentKind.REFUND -> R.string.refund; PaymentKind.CREDIT -> R.string.credit })
        val presets = mapOf("general" to R.string.general, "medical" to R.string.medical, "school" to R.string.school, "childcare" to R.string.childcare, "extracurricular" to R.string.extracurricular, "clothing" to R.string.clothing, "mortgage" to R.string.mortgage, "insurance" to R.string.insurance, "property_tax" to R.string.property_tax, "maintenance" to R.string.maintenance, "repairs" to R.string.repairs, "utilities" to R.string.utilities, "occupancy" to R.string.occupancy)
        fun category(key: String) = snapshot.dossier?.categoryNames?.get(key) ?: when (key) { "children_other" -> label(R.string.children_other); "financial" -> label(R.string.financial); else -> presets[key]?.let(::label) ?: key }
        val charges = snapshot.charges.filter { inPeriod(it.date) }.sortedWith(compareBy<Charge> { it.date }.thenBy { it.id })
        val ids = charges.map { it.id }.toSet()
        val payments = snapshot.payments.filter { it.chargeId in ids || it.allocations.keys.any { id -> id in ids } || (it.chargeId == null && it.allocations.isEmpty() && inPeriod(it.date)) }
        val movements = payments.flatMap { it.expanded() }.filter { it.chargeId in ids || (it.chargeId == null && inPeriod(it.date)) }
        val summary = Ledger.summarize(charges, movements)
        val rows = mutableListOf(
            listOf("Equily", if (annual) month.year.toString() else month.toString()),
            listOf(label(R.string.disclaimer)),
            listOf(label(R.string.report_scope)),
            listOf(label(R.string.description), "EUR", label(R.string.date), label(R.string.split), label(R.string.status), label(R.string.category), label(R.string.child), label(R.string.due), label(R.string.notes), label(R.string.invoice_supplier), label(R.string.invoice_number), label(R.string.reference), label(R.string.audit)),
        )
        charges.forEach { charge -> rows += listOf(charge.description, amount(charge.cents), charge.date.format(dates), BigDecimal.valueOf(charge.selfBasisPoints.toLong(), 2).toPlainString() + " %", status(charge.status) + if (charge.archived) " / " + label(R.string.archived) else "", category(charge.category), charge.child, charge.dueDate?.format(dates).orEmpty(), charge.notes, charge.supplier, charge.invoiceNumber, charge.invoiceReference, label(R.string.unverified)) }
        rows += listOf(label(R.string.payment_history), "EUR", label(R.string.date), label(R.string.kind), label(R.string.source), label(R.string.destination), label(R.string.expense_link), label(R.string.reference))
        payments.sortedWith(compareBy<Movement> { it.date }.thenBy { it.id }).forEach { payment ->
            val allocations = if (payment.allocations.isEmpty()) payment.chargeId?.let { mapOf(it to payment.cents) }.orEmpty() else payment.allocations
            rows += listOf(payment.id, amount(payment.cents), payment.date.format(dates), kind(payment.kind), party(payment.source), party(payment.destination), allocations.entries.joinToString(" | ") { (id, cents) -> (snapshot.charges.firstOrNull { it.id == id }?.description ?: id) + ": " + amount(cents) + " EUR" }, payment.reference)
        }
        rows += listOf(label(R.string.declared), amount(summary.declared), label(R.string.accepted), amount(summary.accepted), label(R.string.contested), amount(summary.contested))
        rows += listOf(label(R.string.your_share), amount(summary.allocated.self), label(R.string.other_share), amount(summary.allocated.other), label(R.string.paid), amount(summary.supplierPaid))
        rows += listOf(label(R.string.outstanding), amount(summary.outstanding), label(R.string.balance), amount(summary.otherOwesSelf))
        rows += listOf(label(R.string.qualified), label(if (summary.qualified) R.string.yes else R.string.no))
        rows += listOf(label(R.string.agreements))
        snapshot.agreements.filter { it.effective <= if (annual) java.time.LocalDate.of(month.year, 12, 31) else month.atEndOfMonth() }.sortedBy { it.effective }.forEach { agreement -> rows += listOf(agreement.title, agreement.effective.format(dates), if (agreement.category == "*") label(R.string.all) else category(agreement.category), BigDecimal.valueOf(agreement.selfBasisPoints.toLong(), 2).toPlainString() + " %", agreement.notes, label(R.string.unverified)) }
        return rows
    }

    fun write(uri: Uri, snapshot: Snapshot, month: YearMonth, pdf: Boolean, disclaimer: String, annual: Boolean = false, language: String = "fr") {
        val rows = rows(snapshot, month, annual, language)
        require(disclaimer.isNotBlank())
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { stream ->
            if (!pdf) stream.writer(Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF")
                rows.forEach { writer.write(Csv.row(it)) }
            } else {
                val document = PdfDocument()
                val paint = Paint().apply { textSize = 10f; color = Color.rgb(22, 33, 62); isAntiAlias = true }
                val title = Paint(paint).apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
                val lines = rows.flatMap { row -> wrap(row.joinToString("  ·  "), paint, 531f) + listOf("") }
                try {
                    lines.chunked(42).forEachIndexed { index, pageLines ->
                        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                        page.canvas.drawText("Equily · " + (if (annual) month.year else month).toString(), 32f, 42f, title)
                        pageLines.forEachIndexed { lineIndex, line -> page.canvas.drawText(line, 32f, 72f + lineIndex * 17f, paint) }
                        page.canvas.drawText("${index + 1}", 550f, 812f, paint)
                        document.finishPage(page)
                    }
                    document.writeTo(stream)
                } finally { document.close() }
            }
        }
    }

    private fun wrap(value: String, paint: Paint, width: Float): List<String> = value.lines().flatMap { paragraph ->
        val lines = mutableListOf<String>()
        var remaining = paragraph
        while (remaining.isNotEmpty()) {
            val measured = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
            val split = if (measured < remaining.length) remaining.lastIndexOf(' ', measured).takeIf { it > 0 } ?: measured else measured
            lines += remaining.substring(0, split)
            remaining = remaining.substring(split).trimStart()
        }
        lines.ifEmpty { listOf("") }
    }
}
