package be.equily.app.domain

import org.junit.Assert.*
import org.junit.Test

class InvoiceParserTest {
    @Test fun vatSubtotalIsNotTotal() {
        val candidate = InvoiceParser.parse("École\nFacture n° 123\n10/10/2026\nSous-total 103,31\nTVA 21,69\nTotal TTC 125,00 €")
        assertEquals(12500L, candidate.cents)
        assertEquals("2026-10-10", candidate.date.toString())
        assertEquals(3, candidate.alternatives.size)
    }
    @Test fun dutchAndThousands() { assertEquals(107000L, InvoiceParser.parse("Bank\nTe betalen 1.070,00").cents) }
    @Test fun unreadableInvoiceRequiresManualEntry() { assertNull(InvoiceParser.parse("illisible").cents) }
}
