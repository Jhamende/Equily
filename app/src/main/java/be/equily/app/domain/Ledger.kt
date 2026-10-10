package be.equily.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class Party { SELF, OTHER, SHARED, UNKNOWN }
enum class EntryStatus { DRAFT, DECLARED, ACCEPTED, CONTESTED, ARCHIVED }
enum class PaymentKind { SUPPLIER, TRANSFER, CONTRIBUTION, REFUND, CREDIT }

data class Charge(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val cents: Long,
    val date: LocalDate,
    val selfBasisPoints: Int = 5000,
    val status: EntryStatus = EntryStatus.DECLARED,
    val category: String = "general",
    val child: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val ruleId: String? = null,
    val archived: Boolean = false,
) {
    init { require(cents in 1..MAX_CENTS); require(selfBasisPoints in 0..10000) }
}

const val MAX_CENTS = 100_000_000_000L

data class Movement(
    val id: String = UUID.randomUUID().toString(),
    val kind: PaymentKind,
    val cents: Long,
    val date: LocalDate,
    val source: Party,
    val destination: Party = Party.UNKNOWN,
    val chargeId: String? = null,
    val reference: String = "",
    val allocations: Map<String, Long> = emptyMap(),
) {
    init {
        require(cents in 1..MAX_CENTS)
        require(chargeId == null || allocations.isEmpty())
        require(allocations.values.all { it in 1..MAX_CENTS })
        require(allocations.isEmpty() || allocations.values.sum() == cents)
        when (kind) {
            PaymentKind.SUPPLIER -> require((chargeId != null || allocations.isNotEmpty()) && destination == Party.UNKNOWN)
            PaymentKind.TRANSFER -> require(source in listOf(Party.SELF, Party.OTHER) && destination in listOf(Party.SELF, Party.OTHER) && source != destination)
            PaymentKind.CONTRIBUTION -> require(source in listOf(Party.SELF, Party.OTHER) && destination == Party.SHARED && chargeId == null && allocations.isEmpty())
            PaymentKind.REFUND -> require((chargeId != null || allocations.isNotEmpty()) && destination != Party.UNKNOWN)
            PaymentKind.CREDIT -> require((chargeId != null || allocations.isNotEmpty()) && destination != Party.UNKNOWN)
        }
    }
    fun expanded(): List<Movement> = if (allocations.isEmpty()) listOf(this) else allocations.map { (expenseId, amount) -> copy(id = "$id:$expenseId", cents = amount, chargeId = expenseId, allocations = emptyMap()) }
}

data class Allocation(val self: Long, val other: Long)
data class LedgerSummary(
    val declared: Long,
    val accepted: Long,
    val contested: Long,
    val allocated: Allocation,
    val supplierPaid: Long,
    val outstanding: Long,
    val otherOwesSelf: Long,
    val qualified: Boolean,
    val sharedCash: Long,
)

object Money {
    fun parse(value: String): Long {
        val compact = value.trim().replace("€", "").replace("EUR", "", true)
            .replace(" ", "").replace("\u00a0", "").replace("\u202f", "")
        require(compact.isNotBlank())
        val normalized = when {
            compact.contains(',') -> compact.replace(".", "").replace(',', '.')
            compact.matches(Regex("[+]?\\d{1,3}(?:\\.\\d{3})+")) -> compact.replace(".", "")
            else -> compact
        }
        val amount = BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        require(amount in 0..MAX_CENTS)
        return amount
    }
    fun split(cents: Long, selfBasisPoints: Int): Allocation {
        require(cents in 0..MAX_CENTS && selfBasisPoints in 0..10000)
        val self = BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(selfBasisPoints.toLong()))
            .divide(BigDecimal(10000), 0, RoundingMode.HALF_UP).longValueExact()
        return Allocation(self, cents - self)
    }
    fun format(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',') + " €"
}

object Ledger {
    fun summarize(charges: List<Charge>, movements: List<Movement>): LedgerSummary {
        require(charges.map { it.id }.distinct().size == charges.size)
        require(movements.map { it.id }.distinct().size == movements.size)
        val active = charges.filter { it.status != EntryStatus.DRAFT }
        val activeIds = active.map { it.id }.toSet()
        val included = movements.flatMap { it.expanded() }.filter { it.chargeId == null || it.chargeId in activeIds }
        var selfShare = 0L
        var otherShare = 0L
        var paid = 0L
        var outstanding = 0L
        var balance = 0L
        var qualified = false
        for (charge in active) {
            val related = included.filter { it.chargeId == charge.id }
            val credits = related.filter { it.kind in listOf(PaymentKind.REFUND, PaymentKind.CREDIT) }.sumOf { it.cents }
            require(credits <= charge.cents) { "Credits exceed charge" }
            val allocation = Money.split(charge.cents - credits, charge.selfBasisPoints)
            selfShare += allocation.self
            otherShare += allocation.other
            val supplier = related.filter { it.kind == PaymentKind.SUPPLIER }
            val supplierTotal = supplier.sumOf { it.cents }
            require(supplierTotal <= charge.cents) { "Supplier overpayment" }
            paid += supplierTotal
            outstanding += (charge.cents - supplierTotal).coerceAtLeast(0)
            val refundSelf = related.filter { it.kind in listOf(PaymentKind.REFUND, PaymentKind.CREDIT) && it.destination == Party.SELF }.sumOf { it.cents }
            val refundOther = related.filter { it.kind in listOf(PaymentKind.REFUND, PaymentKind.CREDIT) && it.destination == Party.OTHER }.sumOf { it.cents }
            val netSelf = supplier.filter { it.source == Party.SELF }.sumOf { it.cents } - refundSelf
            val netOther = supplier.filter { it.source == Party.OTHER }.sumOf { it.cents } - refundOther
            val personalPaid = netSelf + netOther
            if (charge.status != EntryStatus.CONTESTED) {
                val paidShare = Money.split(kotlin.math.abs(personalPaid), charge.selfBasisPoints)
                balance += netSelf - if (personalPaid >= 0) paidShare.self else -paidShare.self
            }
            if (supplier.any { it.source in listOf(Party.SHARED, Party.UNKNOWN) } || related.any { it.kind in listOf(PaymentKind.REFUND, PaymentKind.CREDIT) && it.destination == Party.SHARED }) qualified = true
        }
        val contestedIds = active.filter { it.status == EntryStatus.CONTESTED }.map { it.id }.toSet()
        for (movement in included.filter { it.kind == PaymentKind.TRANSFER && it.chargeId !in contestedIds }) {
            balance += if (movement.source == Party.SELF) movement.cents else -movement.cents
        }
        val sharedCash = included.filter { it.kind == PaymentKind.CONTRIBUTION }.sumOf { it.cents } -
            included.filter { it.kind == PaymentKind.SUPPLIER && it.source == Party.SHARED }.sumOf { it.cents } +
            included.filter { it.kind in listOf(PaymentKind.REFUND, PaymentKind.CREDIT) && it.destination == Party.SHARED }.sumOf { it.cents }
        return LedgerSummary(
            active.filter { it.status == EntryStatus.DECLARED }.sumOf { it.cents },
            active.filter { it.status == EntryStatus.ACCEPTED }.sumOf { it.cents },
            active.filter { it.status == EntryStatus.CONTESTED }.sumOf { it.cents },
            Allocation(selfShare, otherShare), paid, outstanding, balance, qualified, sharedCash,
        )
    }
}

enum class Frequency(val months: Long) { MONTHLY(1), QUARTERLY(3), YEARLY(12) }
data class RecurringRule(
    val id: String,
    val description: String,
    val cents: Long,
    val first: LocalDate,
    val frequency: Frequency,
    val selfBasisPoints: Int,
    val category: String,
    val end: LocalDate? = null,
    val paused: Boolean = false,
) {
    fun generate(until: LocalDate, existingIds: Set<String>): List<Charge> {
        if (paused || until < first) return emptyList()
        val generated = mutableListOf<Charge>()
        var offset = 0L
        while (true) {
            val month = YearMonth.from(first).plusMonths(offset)
            val date = month.atDay(first.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
            if (date > until || (end != null && date > end)) break
            val entryId = "$id:$date"
            if (entryId !in existingIds) generated += Charge(entryId, description, cents, date, selfBasisPoints, category = category, ruleId = id)
            offset += frequency.months
            require(offset <= 12000) { "Recurrence exceeds 1000 years" }
        }
        return generated
    }
}
