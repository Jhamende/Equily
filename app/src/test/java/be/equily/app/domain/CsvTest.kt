package be.equily.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun quotesSeparatorsAndPreservesNewlines() {
        assertEquals("\"a;\"\"b\"\"\nnext\"", Csv.cell("a;\"b\"\nnext"))
        assertEquals("\"école\";\"125.00\"\r\n", Csv.row(listOf("école", "125.00")))
    }

    @Test fun neutralizesSpreadsheetFormulasIncludingLeadingWhitespace() {
        listOf("=1+1", " +cmd", "\t@SUM(A1)", "\n-1", "\u0000=1", "\u007f=1", "\u0085+1", "\uFEFF=1").forEach { value -> assertEquals("\"'$value\"", Csv.cell(value)) }
        assertEquals("\"ordinary text\"", Csv.cell("ordinary text"))
    }
}
