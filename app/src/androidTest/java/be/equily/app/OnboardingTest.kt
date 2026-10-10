package be.equily.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Rule
import org.junit.Test

class OnboardingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onboardAndRecordMortgageOffline() {
        val model = ViewModelProvider(compose.activity)[EquilyModel::class.java]
        compose.waitUntil(60000) { model.ready.value && model.preferencesReady.value }
        runBlocking { model.repository.mutex.withLock { model.repository.database.records().clear() } }
        try {
        compose.waitUntil(30000) { compose.onAllNodesWithText("Enregistrer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Enregistrer").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithContentDescription("Ajouter une dépense").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Ajouter une dépense").performClick()
        try {
            compose.waitUntil(30000) { compose.onAllNodesWithText("Description").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError(compose.onRoot().printToString(), failure)
        }
        compose.onNodeWithText("Description").performTextInput("Crédit hypothécaire")
        compose.onNodeWithText("Montant (€)").performTextInput("1070,00")
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
        compose.waitUntil(60000) { !model.busy.value && model.snapshot.value.charges.any { charge -> charge.description == "Crédit hypothécaire" && charge.cents == 107000L } && compose.onAllNodesWithText("Description").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Crédit hypothécaire").performScrollTo()
        compose.waitUntil(30000) { compose.onNodeWithText("Crédit hypothécaire").isDisplayed() }
        compose.onNodeWithText("Crédit hypothécaire").assertIsDisplayed()
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
        } finally {
            runBlocking { model.repository.mutex.withLock { model.repository.database.records().clear() } }
            compose.waitForIdle()
        }
    }
}
