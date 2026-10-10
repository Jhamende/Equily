package be.equily.app

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import be.equily.app.data.*
import be.equily.app.domain.*
import java.io.File
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CleanInstallationBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.of(2026, 10, 10)
    private val image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=")
    private val document = Document("00000000-0000-0000-0000-000000000001", "school", "image/png", sha256(image), image.size.toLong())
    private fun fixture(): List<Record> = listOf(
        Codec.record(Dossier(self = "Parent A", other = "Parent B", children = "Child", start = today)),
        Codec.record(Charge(id = "mortgage", description = "Mortgage", cents = 107000, date = today)),
        Codec.record(Charge(id = "school", description = "School", cents = 12500, date = today, category = "school", child = "Child")),
        Codec.record(Movement(id = "paid-mortgage", kind = PaymentKind.SUPPLIER, cents = 107000, date = today, source = Party.SELF, chargeId = "mortgage")),
        Codec.record(Movement(id = "paid-school", kind = PaymentKind.SUPPLIER, cents = 12500, date = today, source = Party.SELF, chargeId = "school")),
        Codec.record(Movement(id = "partial", kind = PaymentKind.TRANSFER, cents = 20000, date = today, source = Party.OTHER, destination = Party.SELF)),
        Codec.record(Agreement("agreement", "50/50", "*", 5000, today, "User-recorded agreement")),
        Codec.record(RecurringRule("recurring", "Mortgage", 107000, today.plusMonths(1), Frequency.MONTHLY, 5000, "mortgage", seedChargeId = "mortgage")),
        Codec.record(document),
    ).sortedBy { it.id }

    @Test fun roundTripOrCleanInstallationPhase() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("equilyBackupPhase")
        require(phase == null || phase in listOf("export", "restore"))
        val backup = File(context.cacheDir, "camera/clean-install.equily").apply { parentFile!!.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "be.equily.app.files", backup)
        val marker = File(context.filesDir, "clean-install-marker")
        val sourceDirectory = File(context.filesDir, "clean-install-source")
        val sourceName = "clean-install-source.db"
        if (phase == "restore") {
            assertFalse("Source database survives uninstall", context.getDatabasePath(sourceName).exists())
            assertFalse("Source originals survive uninstall", sourceDirectory.exists())
            assertFalse("Marker survives uninstall", marker.exists())
            assertTrue("External backup missing", backup.isFile)
        } else {
            assertFalse(context.getDatabasePath(sourceName).exists())
            val source = LocalRepository(context, sourceName)
            try {
                val files = AttachmentStore(context, source, sourceDirectory)
                files.file(document).writeBytes(image)
                source.saveBatch(fixture())
                marker.writeText("synthetic test data only")
                BackupStore(context, source, files).export(uri, "synthetic restore passphrase".toCharArray())
            } finally { source.database.close() }
            if (phase == "export") return@runBlocking
        }
        val targetName = "clean-install-target-${UUID.randomUUID()}.db"
        val targetDirectory = File(context.filesDir, "clean-install-target")
        val target = LocalRepository(context, targetName)
        try {
            assertTrue(target.snapshot().records.isEmpty())
            val files = AttachmentStore(context, target, targetDirectory)
            BackupStore(context, target, files).restore(uri, "synthetic restore passphrase".toCharArray())
            verify(target, files)
        } finally { target.database.close() }
        val reopened = LocalRepository(context, targetName)
        try { verify(reopened, AttachmentStore(context, reopened, targetDirectory)) }
        finally {
            reopened.database.close()
            context.deleteDatabase(targetName)
            context.deleteDatabase(sourceName)
            sourceDirectory.deleteRecursively(); targetDirectory.deleteRecursively(); marker.delete(); backup.delete()
        }
    }
    private suspend fun verify(repository: LocalRepository, files: AttachmentStore) {
        val snapshot = repository.snapshot()
        assertEquals(fixture(), snapshot.records.filterNot { it.type == "audit" })
        assertEquals(fixture().size, snapshot.records.count { it.type == "audit" })
        assertArrayEquals(image, files.file(document).readBytes())
        assertEquals(39750L, Ledger.summarize(snapshot.charges, snapshot.movements).otherOwesSelf)
        assertEquals(0L, Ledger.summarize(snapshot.charges, snapshot.movements).outstanding)
        assertEquals("Child", snapshot.charges.first { it.id == "school" }.child)
        assertEquals("mortgage", snapshot.recurring.single().seedChargeId)
    }
}
