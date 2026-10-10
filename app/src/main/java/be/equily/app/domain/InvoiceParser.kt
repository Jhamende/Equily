package be.equily.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class InvoiceCandidate(val supplier: String, val cents: Long?, val date: LocalDate?, val number: String, val alternatives: List<Long>)

object InvoiceParser {
    fun parse(text: String): InvoiceCandidate {
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        val amountPattern = Regex("(?<![\\d/])(?:\\d{1,3}(?:[. \\u00a0\\u202f]\\d{3})+|\\d+)[,.]\\d{2}(?!\\d)")
        val found = lines.flatMap { line -> amountPattern.findAll(line).mapNotNull { match ->
            runCatching { Money.parse(match.value) }.getOrNull()?.let { amount ->
                val lower = line.lowercase()
                val score = when {
                    listOf("ttc", "à payer", "a payer", "te betalen", "grand total", "total incl", "totaal incl").any(lower::contains) -> 3
                    listOf("tva", "btw", "vat", "subtotal", "sous-total", "hors taxe").any(lower::contains) -> -1
                    listOf("total", "totaal").any(lower::contains) -> 2
                    else -> 0
                }
                Triple(amount, score, line)
            }
        }.toList() }.filter { it.first > 0 }
        val ranked = found.sortedWith(compareByDescending<Triple<Long, Int, String>> { it.second }.thenByDescending { it.first })
        val date = Regex("\\b\\d{2}[/.\\-]\\d{2}[/.\\-]\\d{4}\\b").findAll(text).mapNotNull {
            runCatching { LocalDate.parse(it.value.replace('/', '-').replace('.', '-'), DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
        }.firstOrNull()
        val number = lines.firstOrNull { Regex("(?i)(facture|factuur|invoice).*(n°|nr|number|#|nummer)").containsMatchIn(it) }.orEmpty()
        return InvoiceCandidate(lines.firstOrNull().orEmpty().take(200), ranked.firstOrNull()?.first, date, number.take(200), ranked.map { it.first }.distinct().take(20))
    }
}
