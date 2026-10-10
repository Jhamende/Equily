package be.equily.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import be.equily.app.data.Codec
import be.equily.app.data.Dossier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Rule
import org.junit.Test

class FormRecreationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun ordinaryFieldsSurviveActivityRecreationButPassphraseDoesNot() {
        val model = ViewModelProvider(compose.activity)[EquilyModel::class.java]
        compose.waitUntil(60000) { model.ready.value && model.preferencesReady.value }
        runBlocking { model.repository.mutex.withLock { model.repository.database.records().clear() }; model.repository.save(Codec.record(Dossier())) }
        try {
            compose.waitUntil(60000) { compose.onAllNodesWithContentDescription("Ajouter une dépense").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Ajouter une dépense").performClick()
            compose.onNodeWithText("Description").performTextInput("Synthetic retained form")
            compose.onNodeWithText("Montant (€)").performTextInput("123,45")
            compose.onNodeWithText("Enfant / personne concernée (facultatif)").performScrollTo().performTextInput("Synthetic child")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(60000) { compose.onAllNodesWithText("Synthetic retained form").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("123,45").assertExists()
            compose.onNodeWithText("Synthetic child").assertExists()
            compose.onNodeWithText("Réglages").performClick()
            compose.onNodeWithText("Sauvegarde et restauration").performScrollTo().performClick()
            compose.onNodeWithText("Phrase secrète (12 caractères minimum)").performTextInput("synthetic secret not persisted")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(60000) { compose.onAllNodesWithText("Phrase secrète (12 caractères minimum)").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Phrase secrète (12 caractères minimum)").assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString("")))
        } finally {
            runBlocking { model.repository.mutex.withLock { model.repository.database.records().clear() } }
            compose.waitForIdle()
        }
    }
}
