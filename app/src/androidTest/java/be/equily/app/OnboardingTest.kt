package be.equily.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
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
        val model = ViewModelProvider(compose.activity)[EquilyModel::class.java]
        compose.runOnIdle { model.locked.value = true }
        compose.waitUntil(30000) { compose.onAllNodesWithText("Déverrouiller").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Description").assertDoesNotExist()
        compose.onNodeWithText("1070,00").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Déverrouiller").assertExists()
        compose.runOnIdle { model.locked.value = false }
        compose.waitUntil(30000) { compose.onAllNodesWithText("Description").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1070,00").assertExists()
        compose.onNodeWithText("Enregistrer").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Crédit hypothécaire").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crédit hypothécaire").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scanner une facture").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Importer image / PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Retour").performClick()
        compose.onNodeWithText("Réglages").performClick()
        compose.onNodeWithText("Sauvegarde et restauration").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Créer une sauvegarde").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Phrase secrète (12 caractères minimum)").performTextInput("synthetic test passphrase")
        compose.runOnIdle { model.locked.value = true }
        compose.waitUntil(30000) { compose.onAllNodesWithText("Déverrouiller").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Créer une sauvegarde").assertDoesNotExist()
        compose.runOnIdle { model.locked.value = false }
        compose.waitUntil(30000) { compose.onAllNodesWithText("Créer une sauvegarde").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Phrase secrète (12 caractères minimum)").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.InputText, androidx.compose.ui.text.AnnotatedString("synthetic test passphrase")))
        compose.onNodeWithText("Phrase secrète (12 caractères minimum)").performTextClearance()
    }
}
