package be.equily.app.domain

enum class Settlement { UNPAID, PARTIAL, SETTLED, QUALIFIED }

fun Charge.settlement(movements: List<Movement>): Settlement {
    val relevant = movements.flatMap { it.expanded() }.filter { it.chargeId == id }
    val summary = Ledger.summarize(listOf(this), relevant)
    return when {
        status == EntryStatus.DRAFT || status == EntryStatus.CONTESTED -> Settlement.QUALIFIED
        summary.qualified -> Settlement.QUALIFIED
        summary.outstanding == 0L && summary.otherOwesSelf == 0L -> Settlement.SETTLED
        relevant.isEmpty() -> Settlement.UNPAID
        else -> Settlement.PARTIAL
    }
}
