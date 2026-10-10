package be.equily.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class OnboardingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onboardAndRecordMortgageOffline() {
        compose.waitUntil(30000) { compose.onAllNodesWithText("Enregistrer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Enregistrer").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Ajouter une dépense").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ajouter une dépense").performScrollTo().performClick()
        try {
            compose.waitUntil(30000) { compose.onAllNodesWithText("Description").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError(compose.onRoot().printToString(), failure)
        }
        compose.onNodeWithText("Description").performTextInput("Crédit hypothécaire")
        compose.onNodeWithText("Montant (€)").performTextInput("1070,00")
        compose.onNodeWithText("Enregistrer").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Crédit hypothécaire").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crédit hypothécaire").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scanner une facture").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Importer image / PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Retour").performClick()
        compose.onNodeWithText("Réglages").performClick()
        compose.onNodeWithText("Sauvegarde et restauration").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Créer une sauvegarde").fetchSemanticsNodes().isNotEmpty() }
    }
}
