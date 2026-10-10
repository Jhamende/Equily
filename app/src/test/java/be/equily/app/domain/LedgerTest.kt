package be.equily.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LedgerTest {
    private val date = LocalDate.of(2026, 1, 31)
    private val mortgage = Charge(id = "mortgage", description = "Mortgage", cents = 107000, date = date)
    @Test fun europeanAmountsAndExactRounding() {
        assertEquals(107000, Money.parse("1.070,00 €"))
        assertEquals(107000, Money.parse("1.070"))
        assertEquals(12500, Money.parse("125.00"))
        assertEquals(Allocation(51, 50), Money.split(101, 5000))
        for (basis in 0..10000 step 137) assertEquals(101L, Money.split(101, basis).let { it.self + it.other })
    }
    @Test fun paidMortgageAndPartialReimbursement() {
        val paid = Movement(kind = PaymentKind.SUPPLIER, cents = 107000, date = date, source = Party.SELF, chargeId = mortgage.id)
        val transfer = Movement(kind = PaymentKind.TRANSFER, cents = 20000, date = date, source = Party.OTHER, destination = Party.SELF)
        val summary = Ledger.summarize(listOf(mortgage), listOf(paid, transfer))
        assertEquals(33500, summary.otherOwesSelf)
        assertEquals(0, summary.outstanding)
    }
    @Test fun sharedContributionDoesNotSettleAnExpense() {
        val deposit = Movement(kind = PaymentKind.CONTRIBUTION, cents = 107000, date = date, source = Party.SELF, destination = Party.SHARED)
        val summary = Ledger.summarize(listOf(mortgage), listOf(deposit))
        assertEquals(107000, summary.outstanding)
        assertEquals(0, summary.otherOwesSelf)
        assertEquals(107000, summary.sharedCash)
    }
    @Test fun sharedPaymentIsQualified() {
        val payment = Movement(kind = PaymentKind.SUPPLIER, cents = 107000, date = date, source = Party.SHARED, chargeId = mortgage.id)
        assertTrue(Ledger.summarize(listOf(mortgage), listOf(payment)).qualified)
    }
    @Test fun partialSupplierPaymentOnlyCreatesFundedClaim() {
        val payment = Movement(kind = PaymentKind.SUPPLIER, cents = 20000, date = date, source = Party.SELF, chargeId = mortgage.id)
        val summary = Ledger.summarize(listOf(mortgage), listOf(payment))
        assertEquals(87000, summary.outstanding)
        assertEquals(10000, summary.otherOwesSelf)
    }
    @Test fun refundReducesCostAndClaim() {
        val payment = Movement(kind = PaymentKind.SUPPLIER, cents = 107000, date = date, source = Party.SELF, chargeId = mortgage.id)
        val refund = Movement(kind = PaymentKind.REFUND, cents = 7000, date = date, source = Party.UNKNOWN, destination = Party.SELF, chargeId = mortgage.id)
        assertEquals(50000, Ledger.summarize(listOf(mortgage), listOf(payment, refund)).otherOwesSelf)
    }
    @Test fun contestedAmountsAreSeparated() {
        val payment = Movement(kind = PaymentKind.SUPPLIER, cents = 107000, date = date, source = Party.SELF, chargeId = mortgage.id)
        val summary = Ledger.summarize(listOf(mortgage.copy(status = EntryStatus.CONTESTED)), listOf(payment))
        assertEquals(107000, summary.contested)
        assertEquals(0, summary.otherOwesSelf)
    }
    @Test fun refundOnPartialPaymentDoesNotSettleRemainingSupplierDebt() {
        val payment = Movement(kind = PaymentKind.SUPPLIER, cents = 50000, date = date, source = Party.SELF, chargeId = mortgage.id)
        val refund = Movement(kind = PaymentKind.REFUND, cents = 10000, date = date, source = Party.UNKNOWN, destination = Party.SELF, chargeId = mortgage.id)
        val summary = Ledger.summarize(listOf(mortgage), listOf(payment, refund))
        assertEquals(57000, summary.outstanding)
        assertEquals(20000, summary.otherOwesSelf)
    }
    @Test fun sharedCreditReceivedBeforePaymentIsSplitExactly() {
        val credit = Movement(kind = PaymentKind.CREDIT, cents = 10000, date = date, source = Party.UNKNOWN, destination = Party.SELF, chargeId = mortgage.id)
        val summary = Ledger.summarize(listOf(mortgage), listOf(credit))
        assertEquals(-5000, summary.otherOwesSelf)
        assertEquals(107000, summary.outstanding)
    }
    @Test fun onePaymentAllocatedAcrossTwoExpensesIsNotDoubleCounted() {
        val school = Charge(id = "school", description = "School", cents = 12500, date = date, selfBasisPoints = 6000)
        val payment = Movement(kind = PaymentKind.SUPPLIER, cents = 119500, date = date, source = Party.SELF, allocations = mapOf(mortgage.id to 107000, school.id to 12500))
        val summary = Ledger.summarize(listOf(mortgage, school), listOf(payment))
        assertEquals(119500, summary.supplierPaid)
        assertEquals(58500, summary.otherOwesSelf)
        assertEquals(0, summary.outstanding)
    }
    @Test fun archivePreservesFinancialImpact() {
        val payment = Movement(kind = PaymentKind.SUPPLIER, cents = 107000, date = date, source = Party.SELF, chargeId = mortgage.id)
        assertEquals(Ledger.summarize(listOf(mortgage), listOf(payment)), Ledger.summarize(listOf(mortgage.copy(archived = true)), listOf(payment)))
    }
    @Test fun supplierPaymentIsNeverMislabelledAsPaidToOtherPerson() {
        assertTrue(runCatching { Movement(kind = PaymentKind.SUPPLIER, cents = 107000, date = date, source = Party.SELF, destination = Party.OTHER, chargeId = mortgage.id) }.isFailure)
    }
    @Test fun recurrenceKeepsAnchorAndNeverDuplicates() {
        val recurring = RecurringRule("rule", "Mortgage", 107000, date, Frequency.MONTHLY, 5000, "mortgage")
        val entries = recurring.generate(LocalDate.of(2026, 3, 31), emptySet())
        assertEquals(listOf(date, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)), entries.map { it.date })
        assertTrue(recurring.generate(LocalDate.of(2026, 3, 31), entries.map { it.id }.toSet()).isEmpty())
        assertEquals(5000, entries.first().selfBasisPoints)
    }
}
