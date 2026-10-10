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
    @Test fun extractsMetadataAndCategoryWithoutInventingCertainty() {
        val candidate = InvoiceParser.parse("École communale\nFacture n° SCH-2026/42\nRéférence +++123/4567/89012+++\nSous-total 103,31\nTVA 21,69\nTotal TTC 125,00 €")
        assertEquals("SCH-2026/42", candidate.number)
        assertEquals("school", candidate.category)
        assertTrue(candidate.reference.contains("123/4567/89012"))
        assertEquals(12500L, candidate.cents)
    }
    @Test fun discountedVatInclusiveSubtotalIsNotPayableTotal() {
        assertEquals(12500L, InvoiceParser.parse("Supplier\nSubtotal incl VAT 150,00\nDiscount 25,00\nGrand total 125,00").cents)
    }
    @Test fun invoiceDateTakesPriorityOverDueDateAndSupportsIso() {
        assertEquals("2026-10-10", InvoiceParser.parse("Supplier\nDue date 10/11/2026\nInvoice date 2026-10-10\nTotal 125,00").date.toString())
    }
}
