package be.equily.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SettlementTest {
    private val charge = Charge(description = "School", cents = 12500, date = LocalDate.of(2026, 10, 10))
    private fun paid(source: Party, amount: Long = 12500) = Movement(kind = PaymentKind.SUPPLIER, cents = amount, date = charge.date, source = source, chargeId = charge.id)

    @Test fun supplierPaymentDoesNotAutomaticallySettleBetweenParties() {
        assertEquals(Settlement.UNPAID, charge.settlement(emptyList()))
        assertEquals(Settlement.PARTIAL, charge.settlement(listOf(paid(Party.SELF, 5000))))
        assertEquals(Settlement.PARTIAL, charge.settlement(listOf(paid(Party.SELF))))
        val transfer = Movement(kind = PaymentKind.TRANSFER, cents = 6250, date = charge.date, source = Party.OTHER, destination = Party.SELF, chargeId = charge.id)
        assertEquals(Settlement.SETTLED, charge.settlement(listOf(paid(Party.SELF), transfer)))
    }

    @Test fun disputedAndUnknownPaymentsCannotClaimSettlement() {
        assertEquals(Settlement.QUALIFIED, charge.copy(status = EntryStatus.CONTESTED).settlement(listOf(paid(Party.SELF))))
        assertEquals(Settlement.QUALIFIED, charge.copy(status = EntryStatus.DRAFT).settlement(emptyList()))
        assertEquals(Settlement.QUALIFIED, charge.settlement(listOf(paid(Party.SHARED))))
        assertEquals(Settlement.QUALIFIED, charge.settlement(listOf(paid(Party.UNKNOWN))))
    }
}
