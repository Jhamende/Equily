package be.equily.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RecurringSafetyTest {
    @Test fun unsupportedAncientHorizonIsRejectedBeforeGeneration() {
        val rule = RecurringRule("rule", "Obligation", 100, LocalDate.of(1, 1, 1), Frequency.MONTHLY, 5000, "general")
        assertThrows(IllegalArgumentException::class.java) { rule.generate(LocalDate.of(2026, 10, 10), emptySet()) }
    }

    @Test fun closedHistoricalRuleUsesItsEndInsteadOfTheCurrentYear() {
        val rule = RecurringRule("rule", "Obligation", 100, LocalDate.of(1, 1, 1), Frequency.YEARLY, 5000, "general", end = LocalDate.of(100, 1, 1))
        assertEquals(100, rule.generate(LocalDate.of(2026, 10, 10), emptySet()).size)
    }
}
