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
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
    @Test fun largeBackupStreamsAllOriginalsAndCleansTemporaryFiles() = runBlocking {
        val source = LocalRepository(context, "large-source-${UUID.randomUUID()}.db")
        val target = LocalRepository(context, "large-target-${UUID.randomUUID()}.db")
        val root = File(context.cacheDir, "large-test-${UUID.randomUUID()}")
        val sourceFiles = AttachmentStore(context, source, File(root, "source"))
        val targetFiles = AttachmentStore(context, target, File(root, "target"))
        val backup = File(context.cacheDir, "camera/large-${UUID.randomUUID()}.equily").apply { parentFile!!.mkdirs(); createNewFile() }
        val uri = FileProvider.getUriForFile(context, "be.equily.app.files", backup)
        try {
            source.save(Codec.record(Dossier()))
            val prefix = invoiceBitmap()
            repeat(5) { index ->
                val original = File(root, "original-$index.png")
                val random = Random(index.toLong())
                val buffer = ByteArray(8192)
                original.outputStream().buffered().use { output ->
                    output.write(prefix)
                    repeat(1954) { random.nextBytes(buffer); output.write(buffer) }
                }
                val hash = sha256(original)
                val document = Document(UUID.randomUUID().toString(), null, "image/png", hash, original.length())
                check(original.renameTo(sourceFiles.file(document)))
                source.save(Codec.record(document))
            }
            val secret = "a very long test passphrase".toCharArray()
            BackupStore(context, source, sourceFiles).export(uri, secret)
            assertTrue(secret.all { it == '\u0000' })
            assertTrue(backup.length() > 75_000_000)
            BackupStore(context, target, targetFiles).restore(uri, "a very long test passphrase".toCharArray())
            assertEquals(source.snapshot().records, target.snapshot().records)
            target.snapshot().documents.forEach { document ->
                assertEquals(document.size, targetFiles.file(document).length())
                assertEquals(document.hash, sha256(targetFiles.file(document)))
            }
            assertFalse(context.cacheDir.listFiles().orEmpty().any { it.isDirectory && it.name.matches(Regex("(?:backup|restore)-[a-f0-9-]{36}")) })
        } finally { source.database.close(); target.database.close(); root.deleteRecursively(); backup.delete() }
    }
    @Test fun startupCleanupDoesNotDeleteAnotherActiveBackupDirectory() = runBlocking {
        val repository = LocalRepository(context, "cleanup-${UUID.randomUUID()}.db")
        val originalDirectory = File(context.cacheDir, "cleanup-originals-${UUID.randomUUID()}")
        val active = File(context.cacheDir, "backup-${UUID.randomUUID()}")
        try {
            val files = AttachmentStore(context, repository, originalDirectory)
            BackupStore(context, repository, files).cleanupStaging()
            check(active.mkdirs())
            val marker = File(active, "archive.zip").apply { writeText("synthetic active export") }
            BackupStore(context, repository, files).cleanupStaging()
            assertTrue(marker.isFile)
        } finally { repository.database.close(); originalDirectory.deleteRecursively(); active.deleteRecursively() }
    }
    @Test fun authenticatedMalformedZipCannotMutateExistingDossier() = runBlocking {
        val repository = LocalRepository(context, "invalid-archive-${UUID.randomUUID()}.db")
        val root = File(context.cacheDir, "invalid-test-${UUID.randomUUID()}")
        val files = AttachmentStore(context, repository, root)
        val backup = File(context.cacheDir, "camera/invalid-${UUID.randomUUID()}.equily").apply { parentFile!!.mkdirs(); createNewFile() }
        val uri = FileProvider.getUriForFile(context, "be.equily.app.files", backup)
        try {
            repository.save(Codec.record(Dossier(self = "Keep current dossier")))
            val before = repository.snapshot().records
            val bytes = invoiceBitmap()
            val document = Document(UUID.randomUUID().toString(), null, "image/png", sha256(bytes), bytes.size.toLong())
            val records = Codec.encode(listOf(Codec.record(Dossier()), Codec.record(document)))
            val variants = listOf(
                listOf("../escape" to bytes, "records.json" to records),
                listOf("records.json" to records),
                listOf("records.json" to records, "attachments/${document.hash}" to byteArrayOf(1, 2, 3)),
            )
            for (entries in variants) {
                val zip = ByteArrayOutputStream()
                ZipOutputStream(zip).use { output ->
                    entries.forEach { (name, content) -> output.putNextEntry(ZipEntry(name)); output.write(content); output.closeEntry() }
                }
                backup.writeBytes(BackupCrypto.encrypt(zip.toByteArray(), "a very long test passphrase".toCharArray()))
                val secret = "a very long test passphrase".toCharArray()
                assertTrue(runCatching { BackupStore(context, repository, files).restore(uri, secret) }.isFailure)
                assertTrue(secret.all { it == '\u0000' })
                assertEquals(before, repository.snapshot().records)
                assertTrue(files.directory.listFiles().orEmpty().isEmpty())
            }
        } finally { repository.database.close(); root.deleteRecursively(); backup.delete() }
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
    @Test fun recurringPauseRevisionAndLegacyRecordsRemainTransactional() = runBlocking {
        val repository = LocalRepository(context, "recurring-${UUID.randomUUID()}.db")
        try {
            repository.save(Codec.record(Dossier()))
            val first = LocalDate.of(2026, 1, 31)
            val source = Charge(description = "Manual mortgage", cents = 107000, date = first)
            val rule = RecurringRule(UUID.randomUUID().toString(), "Mortgage", 107000, first.plusMonths(1), Frequency.MONTHLY, 5000, "mortgage", anchorDay = 31, seedChargeId = source.id)
            repository.saveBatch(listOf(Codec.record(source), Codec.record(rule)))
            assertTrue(runCatching { repository.save(Codec.record(rule.copy(id = UUID.randomUUID().toString()))) }.isFailure)
            repository.setRecurringPaused(rule.id, true, LocalDate.of(2026, 3, 1))
            repository.setRecurringPaused(rule.id, false, LocalDate.of(2026, 5, 1))
            val restored = repository.snapshot().recurring.single()
            assertEquals(listOf(RecurringPause(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 1))), restored.pauses)
            val generated = restored.generate(LocalDate.of(2026, 5, 31), emptySet())
            repository.saveBatch(generated.map(Codec::record))
            val before = repository.snapshot().records
            val conflicting = rule.copy(id = UUID.randomUUID().toString(), first = LocalDate.of(2026, 5, 1), seedChargeId = null)
            assertTrue(runCatching { repository.reviseRecurring(rule.id, conflicting) }.isFailure)
            assertEquals(before, repository.snapshot().records)
            val replacement = conflicting.copy(first = LocalDate.of(2026, 6, 1), cents = 110000)
            repository.reviseRecurring(rule.id, replacement)
            val snapshot = repository.snapshot()
            assertEquals(LocalDate.of(2026, 5, 31), snapshot.recurring.first { it.id == rule.id }.end)
            assertEquals(generated, snapshot.charges.filter { it.ruleId == rule.id }.sortedBy { it.date })
            val legacy = Codec.record(rule).let { record -> record.copy(body = org.json.JSONObject(record.body).apply { remove("pauses"); remove("anchorDay"); remove("child"); remove("notes"); remove("seedCharge") }.toString()) }
            assertEquals(28, Codec.recurring(legacy).anchorDay)
            assertTrue(Codec.recurring(legacy).pauses.isEmpty())
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
