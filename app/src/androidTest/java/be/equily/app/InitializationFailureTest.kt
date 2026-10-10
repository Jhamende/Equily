package be.equily.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import be.equily.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InitializationFailureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun invalidStoredDataShowsRetryWithoutErasingAndRecoversAfterRepair() {
        val model = ViewModelProvider(compose.activity)[EquilyModel::class.java]
        compose.waitUntil(60000) { model.ready.value && model.preferencesReady.value }
        val invalid = Record("dossier", "dossier", "{\"start\":\"invalid-date\"}")
        runBlocking { model.repository.mutex.withLock { model.repository.database.records().clear(); model.repository.database.records().put(invalid) } }
        try {
            compose.waitUntil(60000) { model.initializationFailed.value }
            assertEquals(listOf(invalid), runBlocking { model.repository.snapshot().records })
            val label = android.content.res.Configuration(compose.activity.resources.configuration).apply { setLocale(java.util.Locale.FRENCH) }.let { compose.activity.createConfigurationContext(it).getString(R.string.retry) }
            compose.onNodeWithText(label).assertIsDisplayed()
            runBlocking { model.repository.mutex.withLock { model.repository.database.records().put(Codec.record(Dossier())) } }
            compose.onNodeWithText(label).performClick()
            compose.waitUntil(60000) { !model.initializationFailed.value && model.snapshot.value.dossier != null }
            compose.onNodeWithContentDescription("Ajouter une dépense").assertIsDisplayed()
        } finally {
            runBlocking { model.repository.mutex.withLock { model.repository.database.records().clear() } }
            compose.waitForIdle()
        }
    }
}
