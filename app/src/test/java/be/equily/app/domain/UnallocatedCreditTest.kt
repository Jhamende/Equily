package be.equily.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class UnallocatedCreditTest {
    private val date = LocalDate.of(2026, 10, 10)
    @Test fun benefitsDoNotChangeSharesOrBalancesUntilExplicitlyAllocated() {
        val charge = Charge(description = "School", cents = 12500, date = date)
        val supplier = Movement(kind = PaymentKind.SUPPLIER, cents = 12500, date = date, source = Party.SELF, chargeId = charge.id)
        val benefit = Movement(kind = PaymentKind.CREDIT, cents = 2500, date = date, source = Party.UNKNOWN, destination = Party.SELF, reference = "Optional benefit")
        val unchanged = Ledger.summarize(listOf(charge), listOf(supplier, benefit))
        assertEquals(6250L, unchanged.otherOwesSelf)
        assertEquals(Allocation(6250, 6250), unchanged.allocated)
        val configured = Ledger.summarize(listOf(charge), listOf(supplier, benefit.copy(chargeId = charge.id)))
        assertEquals(5000L, configured.otherOwesSelf)
        assertEquals(Allocation(5000, 5000), configured.allocated)
    }

    @Test fun sharedCreditRecordsActualCashButNotAPersonalClaim() {
        val credit = Movement(kind = PaymentKind.CREDIT, cents = 2500, date = date, source = Party.UNKNOWN, destination = Party.SHARED)
        val summary = Ledger.summarize(emptyList(), listOf(credit))
        assertEquals(2500L, summary.sharedCash)
        assertEquals(0L, summary.otherOwesSelf)
        assertTrue(runCatching { credit.copy(kind = PaymentKind.REFUND) }.isFailure)
    }
}
