package be.equily.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class InvoiceCandidate(val supplier: String, val cents: Long?, val date: LocalDate?, val number: String, val alternatives: List<Long>, val reference: String = "", val category: String = "general")

object InvoiceParser {
    fun parse(text: String): InvoiceCandidate {
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        val amountPattern = Regex("(?<![\\d/])(?:\\d{1,3}(?:[. \\u00a0\\u202f]\\d{3})+|\\d+)[,.]\\d{2}(?!\\d)")
        val found = lines.flatMap { line -> amountPattern.findAll(line).mapNotNull { match ->
            runCatching { Money.parse(match.value) }.getOrNull()?.let { amount ->
                val lower = line.lowercase()
                val score = when {
                    listOf("sous-total", "subtotal", "sub-total", "subtotaal").any(lower::contains) -> -1
                    Regex("\\b(?:ttc|à payer|a payer|te betalen|grand total|total incl|totaal incl)\\b").containsMatchIn(lower) -> 3
                    listOf("tva", "btw", "vat", "subtotal", "sous-total", "hors taxe").any(lower::contains) -> -1
                    listOf("total", "totaal").any(lower::contains) -> 2
                    else -> 0
                }
                Triple(amount, score, line)
            }
        }.toList() }.filter { it.first > 0 }
        val ranked = found.sortedWith(compareByDescending<Triple<Long, Int, String>> { it.second }.thenByDescending { it.first })
        val datedLines = lines.sortedBy { line ->
            val lower = line.lowercase()
            when {
                listOf("échéance", "echeance", "due date", "vervaldatum").any(lower::contains) -> 2
                listOf("date facture", "invoice date", "factuurdatum").any(lower::contains) -> 0
                else -> 1
            }
        }
        val date = datedLines.firstNotNullOfOrNull { line ->
            Regex("\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{2}[/.\\-]\\d{2}[/.\\-]\\d{4})\\b").findAll(line).firstNotNullOfOrNull { match ->
                runCatching {
                    if (match.value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) LocalDate.parse(match.value)
                    else LocalDate.parse(match.value.replace('/', '-').replace('.', '-'), DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT))
                }.getOrNull()
            }
        }
        val numberPattern = Regex("(?i)(?:facture|factuur|invoice)\\s*(?:n[°ºo.]?|nr\\.?|number|#|nummer)?\\s*[:#]?\\s*([A-Z0-9][A-Z0-9/_-]{1,79})\\s*$")
        val number = lines.firstNotNullOfOrNull { line -> numberPattern.find(line)?.groupValues?.get(1) }.orEmpty()
        val reference = lines.firstOrNull { Regex("(?i)(référence|reference|referentie|communication|mededeling)").containsMatchIn(it) }.orEmpty().take(200)
        val lower = text.lowercase()
        val category = when {
            listOf("école", "ecole", "school", "scolaire").any(lower::contains) -> "school"
            listOf("crèche", "creche", "kinderopvang", "childcare").any(lower::contains) -> "childcare"
            listOf("médecin", "medecin", "apotheek", "pharmacie", "medical", "ziekenhuis").any(lower::contains) -> "medical"
            listOf("hypothécaire", "hypotheek", "mortgage").any(lower::contains) -> "mortgage"
            listOf("assurance", "verzekering", "insurance").any(lower::contains) -> "insurance"
            else -> "general"
        }
        return InvoiceCandidate(lines.firstOrNull().orEmpty().take(200), ranked.firstOrNull()?.first, date, number.take(200), ranked.map { it.first }.distinct().take(20), reference, category)
    }
}
