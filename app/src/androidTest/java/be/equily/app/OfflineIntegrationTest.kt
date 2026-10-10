package be.equily.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import be.equily.app.data.*
import be.equily.app.domain.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun completeBackupRestoresIntoEmptyDatabaseAndRejectsTampering() = runBlocking {
        val source = LocalRepository(context, "backup-source-${UUID.randomUUID()}.db")
        val target = LocalRepository(context, "backup-target-${UUID.randomUUID()}.db")
        val root = File(context.cacheDir, "backup-test-${UUID.randomUUID()}")
        val sourceFiles = AttachmentStore(context, source, File(root, "source"))
        val targetFiles = AttachmentStore(context, target, File(root, "target"))
        val backupFile = File(context.cacheDir, "camera/test-${UUID.randomUUID()}.equily").apply { parentFile!!.mkdirs(); createNewFile() }
        val uri = FileProvider.getUriForFile(context, "be.equily.app.files", backupFile)
        try {
            val today = LocalDate.now()
            val charge = Charge(description = "School", cents = 12500, date = today, child = "Child", notes = "Invoice")
            source.saveBatch(listOf(
                Codec.record(Dossier(children = "Child")), Codec.record(charge),
                Codec.record(Movement(kind = PaymentKind.SUPPLIER, cents = 12500, date = today, source = Party.SELF, chargeId = charge.id)),
                Codec.record(Agreement(UUID.randomUUID().toString(), "Agreement", "school", 5000, today, "Evidence recorded locally")),
                Codec.record(RecurringRule(UUID.randomUUID().toString(), "Mortgage", 107000, today, Frequency.MONTHLY, 5000, "mortgage")),
            ))
            val bytes = invoiceBitmap()
            val document = Document(UUID.randomUUID().toString(), charge.id, "image/png", sha256(bytes), bytes.size.toLong())
            sourceFiles.file(document).writeBytes(bytes)
            source.save(Codec.record(document))
            val before = source.snapshot()
            BackupStore(context, source, sourceFiles).export(uri, "a very long secret passphrase".toCharArray())
            assertTrue(target.snapshot().records.isEmpty())
            BackupStore(context, target, targetFiles).restore(uri, "a very long secret passphrase".toCharArray())
            assertEquals(before.records, target.snapshot().records)
            assertArrayEquals(bytes, targetFiles.file(document).readBytes())
            assertEquals(6250L, Ledger.summarize(target.snapshot().charges, target.snapshot().movements).otherOwesSelf)
            val corrupted = backupFile.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            backupFile.writeBytes(corrupted)
            assertTrue(runCatching { BackupStore(context, target, targetFiles).restore(uri, "a very long secret passphrase".toCharArray()) }.isFailure)
            assertEquals(before.records, target.snapshot().records)
        } finally { source.database.close(); target.database.close(); root.deleteRecursively(); backupFile.delete() }
    }
    @Test fun bundledOcrReadsSyntheticSchoolInvoice() = runBlocking {
        val repository = LocalRepository(context, "ocr-${UUID.randomUUID()}.db")
        val root = File(context.cacheDir, "ocr-test-${UUID.randomUUID()}")
        val files = AttachmentStore(context, repository, root)
        try {
            val bytes = invoiceBitmap()
            val document = Document(UUID.randomUUID().toString(), null, "image/png", sha256(bytes), bytes.size.toLong())
            files.file(document).writeBytes(bytes)
            assertEquals(12500L, files.extract(document).cents)
        } finally { repository.database.close(); root.deleteRecursively() }
    }
    @Test fun ruleEffectiveDatesAndTransactionalValidation() = runBlocking {
        val repository = LocalRepository(context, "rules-${UUID.randomUUID()}.db")
        try {
            val first = LocalDate.of(2026, 1, 1)
            repository.save(Codec.record(Dossier()))
            repository.save(Codec.record(Agreement(UUID.randomUUID().toString(), "70/30", "school", 7000, first.plusMonths(1), "")))
            val snapshot = repository.snapshot()
            assertEquals(5000, snapshot.split("school", first))
            assertEquals(7000, snapshot.split("school", first.plusMonths(1)))
            val charge = Charge(description = "School", cents = 12500, date = first)
            repository.save(Codec.record(charge))
            val count = repository.snapshot().records.size
            val invalid = Movement(kind = PaymentKind.SUPPLIER, cents = 12501, date = first, source = Party.SELF, chargeId = charge.id)
            assertTrue(runCatching { repository.save(Codec.record(invalid)) }.isFailure)
            assertEquals(count, repository.snapshot().records.size)
            val originalRecord = Codec.record(charge)
            val malformed = originalRecord.copy(body = org.json.JSONObject(originalRecord.body).put("cents", 12500.5).toString())
            assertTrue(runCatching { repository.save(malformed) }.isFailure)
            assertEquals(count, repository.snapshot().records.size)
        } finally { repository.database.close() }
    }
    private fun invoiceBitmap(): ByteArray {
        val bitmap = Bitmap.createBitmap(1600, 650, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK; textSize = 90f; isAntiAlias = true }
        canvas.drawText("ECOLE", 100f, 140f, paint)
        canvas.drawText("Facture 10/10/2026", 100f, 300f, paint)
        canvas.drawText("Total TTC 125,00 EUR", 100f, 470f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
}
