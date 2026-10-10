package be.equily.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import be.equily.app.data.*
import be.equily.app.domain.Ledger
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AcceptanceJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun waitFor(label: String, timeout: Long = 60000) = compose.waitUntil(timeout) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    private fun click(label: String) {
        val model = ViewModelProvider(compose.activity)[EquilyModel::class.java]
        compose.waitUntil(60000) { model.message.value != R.string.success }
        android.util.Log.i("EquilyJourney", "Control: $label")
        if (label == "Ajouter une dépense") { compose.waitUntil(60000) { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithContentDescription(label).performClick() }
        else { waitFor(label); compose.onNodeWithText(label).performScrollTo().performClick() }
    }
    private fun field(label: String, value: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(value) }
    private fun choose(label: String, option: String) { click(label); waitFor(option); compose.onNodeWithText(option).performClick() }
    private fun result(action: String, file: File): Instrumentation.ActivityMonitor {
        val uri = FileProvider.getUriForFile(compose.activity, "be.equily.app.files", file)
        return object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = if (intent.action == action) Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) else null
        }.also(instrumentation::addMonitor)
    }

    @Test fun mortgageInvoicePartialReimbursementReportsAndBackupThroughRealScreens() {
        val model = ViewModelProvider(compose.activity)[EquilyModel::class.java]
        compose.waitUntil(60000) { model.ready.value && model.preferencesReady.value }
        runBlocking { model.repository.mutex.withLock { model.repository.database.records().clear() } }
        val root = File(compose.activity.cacheDir, "camera/journey-${UUID.randomUUID()}").apply { mkdirs() }
        val invoice = File(root, "school.png")
        val csv = File(root, "monthly.csv")
        val pdf = File(root, "monthly.pdf")
        val archive = File(root, "complete.equily")
        var monitor: Instrumentation.ActivityMonitor? = null
        try {
            waitFor("Retrouvez un peu de sérénité")
            field("Votre prénom ou alias (facultatif)", "Parent A")
            field("Autre personne (facultatif)", "Parent B")
            field("Enfants / alias, séparés par une virgule (facultatif)", "Child")
            click("Enregistrer")
            click("Ajouter une dépense")
            field("Description", "Mortgage acceptance")
            field("Montant (€)", "1070,00")
            compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
            choose("Source du paiement : Inconnu", "Vous")
            click("Enregistrer")
            click("Mortgage acceptance")
            click("Nouvelle récurrence")
            click("Enregistrer")
            compose.waitUntil(60000) { model.snapshot.value.recurring.size == 1 }
            assertEquals(107000L, model.snapshot.value.recurring.single().cents)
            assertTrue(model.snapshot.value.recurring.single().first > LocalDate.now())
            compose.onNodeWithText("Accueil").performClick()
            val bitmap = Bitmap.createBitmap(1600, 650, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { color = Color.BLACK; textSize = 90f; isAntiAlias = true }
            canvas.drawText("ECOLE", 100f, 140f, paint)
            canvas.drawText("Total TTC 125,00 EUR", 100f, 400f, paint)
            invoice.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            click("Scanner une facture")
            val cameraDirectory = File(compose.activity.cacheDir, "camera")
            val cameraFiles = cameraDirectory.listFiles().orEmpty().filter { it.extension == "jpg" }.map { it.name }.toSet()
            monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = if (intent.action == android.provider.MediaStore.ACTION_IMAGE_CAPTURE) Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null) else null
            }.also(instrumentation::addMonitor)
            click("Prendre une photo")
            compose.waitUntil(60000) { monitor!!.hits > 0 && cameraDirectory.listFiles().orEmpty().filter { it.extension == "jpg" }.map { it.name }.toSet() == cameraFiles }
            instrumentation.removeMonitor(monitor); monitor = null
            monitor = result(Intent.ACTION_OPEN_DOCUMENT, invoice)
            click("Importer image / PDF")
            waitFor("Vérifier la facture", 180000)
            instrumentation.removeMonitor(monitor); monitor = null
            assertEquals(12500L, model.scanCandidate?.cents)
            field("Description", "School acceptance")
            field("Montant (€)", "125,01")
            field("Montant (€)", "125,00")
            field("Enfant / personne concernée (facultatif)", "Child")
            compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
            choose("Source du paiement : Inconnu", "Vous")
            click("Enregistrer")
            compose.waitUntil(60000) { model.snapshot.value.charges.size == 2 }
            val school = model.snapshot.value.charges.first { it.description == "School acceptance" }
            assertEquals("Child", school.child)
            assertEquals(1, model.snapshot.value.documents.count { it.chargeId == school.id })
            click("Ajouter un paiement")
            field("Montant (€)", "200,00")
            choose("Source du paiement : Vous", "Autre personne")
            choose("Destinataire : Autre personne", "Vous")
            click("Enregistrer")
            compose.waitUntil(60000) { model.snapshot.value.payments.size == 3 }
            assertEquals(39750L, Ledger.summarize(model.snapshot.value.charges, model.snapshot.value.movements).otherOwesSelf)
            compose.onNodeWithText("397,50 €").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Réglages").performClick()
            click("Rapports")
            monitor = result(Intent.ACTION_CREATE_DOCUMENT, csv); click("Exporter CSV")
            compose.waitUntil(60000) { csv.length() > 0 && !model.busy.value }
            instrumentation.removeMonitor(monitor); monitor = null
            assertTrue(csv.readText().contains("125.00")); assertTrue(csv.readText().contains("397.50"))
            monitor = result(Intent.ACTION_CREATE_DOCUMENT, pdf); click("Exporter PDF")
            compose.waitUntil(60000) { pdf.length() > 0 && !model.busy.value }
            instrumentation.removeMonitor(monitor); monitor = null
            assertTrue(pdf.inputStream().use { input -> ByteArray(5).also { input.read(it) }.toString(Charsets.US_ASCII) } == "%PDF-")
            compose.onNodeWithContentDescription("Retour").performClick()
            click("Sauvegarde et restauration")
            field("Phrase secrète (12 caractères minimum)", "synthetic journey passphrase")
            monitor = result(Intent.ACTION_CREATE_DOCUMENT, archive); click("Créer une sauvegarde")
            compose.waitUntil(180000) { archive.length() > 0 && !model.busy.value }
            instrumentation.removeMonitor(monitor); monitor = null
            val expected = runBlocking { model.repository.snapshot() }
            val target = LocalRepository(compose.activity, "journey-restored-${UUID.randomUUID()}.db")
            val files = AttachmentStore(compose.activity, target, File(root, "restored-originals"))
            try {
                runBlocking { BackupStore(compose.activity, target, files).restore(FileProvider.getUriForFile(compose.activity, "be.equily.app.files", archive), "synthetic journey passphrase".toCharArray()) }
                val restored = runBlocking { target.snapshot() }
                assertEquals(expected.records, restored.records)
                assertEquals(39750L, Ledger.summarize(restored.charges, restored.movements).otherOwesSelf)
                restored.documents.forEach { assertEquals(it.hash, sha256(files.file(it))) }
            } finally { target.database.close() }
        } catch (failure: Throwable) {
            android.util.Log.e("EquilyJourney", "Synthetic state: busy=${model.busy.value}, imported=${model.scanDocument != null}, recognized=${model.scanCandidate != null}, pickerHits=${monitor?.hits}")
            android.util.Log.e("EquilyJourney", "Synthetic acceptance scenario failed", failure)
            runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().forEachIndexed { index, _ -> android.util.Log.e("EquilyJourney", compose.onAllNodes(isRoot())[index].printToString()) } }
            throw failure
        } finally {
            monitor?.let(instrumentation::removeMonitor)
            runBlocking { model.repository.mutex.withLock { model.repository.database.records().clear() } }
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }
}
