package be.equily.app.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import be.equily.app.domain.Ledger
import be.equily.app.domain.Money
import java.time.YearMonth

class Reports(private val context: Context) {
    fun write(uri: Uri, snapshot: Snapshot, month: YearMonth, pdf: Boolean, disclaimer: String) {
        val charges = snapshot.charges.filter { YearMonth.from(it.date) == month }
        val ids = charges.map { it.id }.toSet()
        val payments = snapshot.movements.filter { it.chargeId in ids || (it.chargeId == null && YearMonth.from(it.date) == month) }
        val summary = Ledger.summarize(charges, payments)
        val rows = mutableListOf(
            listOf("Equily", month.toString()),
            listOf(disclaimer),
            listOf("description", "EUR", "date", "self_basis_points", "status_user_recorded", "category", "child", "source"),
        )
        charges.forEach { rows += listOf(it.description, java.math.BigDecimal.valueOf(it.cents, 2).toPlainString(), it.date.toString(), it.selfBasisPoints.toString(), it.status.name, it.category, it.child, "local-user-unverified") }
        rows += listOf("payments", "EUR", "date", "kind", "source", "destination", "expense", "reference")
        payments.forEach { rows += listOf(it.id, java.math.BigDecimal.valueOf(it.cents, 2).toPlainString(), it.date.toString(), it.kind.name, it.source.name, it.destination.name, it.chargeId.orEmpty(), it.reference) }
        rows += listOf("declared", Money.format(summary.declared), "accepted_user_recorded", Money.format(summary.accepted), "contested_user_recorded", Money.format(summary.contested))
        rows += listOf("self_share", Money.format(summary.allocated.self), "other_share", Money.format(summary.allocated.other), "supplier_paid", Money.format(summary.supplierPaid))
        rows += listOf("outstanding_supplier", Money.format(summary.outstanding), "other_owes_self_personal_only", Money.format(summary.otherOwesSelf), "qualified_shared_account", summary.qualified.toString())
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { stream ->
            if (!pdf) stream.writer(Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF")
                rows.forEach { row -> writer.write(row.joinToString(";") { value -> "\"" + value.replace("\"", "\"\"").let { if (it.startsWith("=") || it.startsWith("+") || it.startsWith("-") || it.startsWith("@")) "'" + it else it } + "\"" } + "\r\n") }
            } else {
                val document = PdfDocument()
                try {
                val paint = Paint().apply { textSize = 10f; color = android.graphics.Color.rgb(22, 33, 62); isAntiAlias = true }
                val lines = rows.flatMap { row -> row.joinToString(" | ").chunked(95) }
                lines.chunked(44).forEachIndexed { index, pageLines ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    pageLines.forEachIndexed { lineIndex, line -> page.canvas.drawText(line, 32f, 40f + lineIndex * 17f, paint) }
                    document.finishPage(page)
                }
                document.writeTo(stream)
                } finally { document.close() }
            }
        }
    }
}
