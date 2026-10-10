package be.equily.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelProvider
import be.equily.app.data.*
import be.equily.app.domain.*
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Rule
import org.junit.Test

class VisualLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun syntheticDashboardUsesLocalizedAccessibleLabelsInEachTheme() {
        val model = ViewModelProvider(compose.activity)[EquilyModel::class.java]
        compose.waitUntil(60000) { model.ready.value && model.preferencesReady.value }
        val charge = Charge(description = "Synthetic school invoice", cents = 12500, date = LocalDate.now(), category = "school")
        runBlocking {
            model.repository.mutex.withLock { model.repository.database.records().clear() }
            model.repository.saveBatch(listOf(Codec.record(Dossier()), Codec.record(charge), Codec.record(Movement(kind = PaymentKind.SUPPLIER, cents = 12500, date = charge.date, source = Party.SELF, chargeId = charge.id))))
        }
        try {
            listOf("fr" to "light", "fr" to "dark", "nl" to "light", "nl" to "dark", "en" to "light", "en" to "dark").forEach { (language, theme) ->
                runBlocking { compose.activity.preferences.edit { it[stringPreferencesKey("language")] = language; it[stringPreferencesKey("theme")] = theme } }
                val localized = compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
                val label = localized.getString(R.string.add_expense)
                compose.waitUntil(60000) { model.preferences.value.language == language && model.preferences.value.theme == theme && compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription(label).assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.scan)).assertExists()
                compose.runOnIdle {
                    val view = compose.activity.window.decorView
                    org.junit.Assert.assertEquals(theme == "light", androidx.core.view.WindowInsetsControllerCompat(compose.activity.window, view).isAppearanceLightStatusBars)
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val file = File(compose.activity.cacheDir, "camera/equily-visual-$language-$theme.png").apply { parentFile!!.mkdirs() }
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        } finally {
            runBlocking {
                compose.activity.preferences.edit { it[stringPreferencesKey("language")] = "fr"; it[stringPreferencesKey("theme")] = "system" }
                model.repository.mutex.withLock { model.repository.database.records().clear() }
            }
            compose.waitForIdle()
        }
    }
}
