package be.equily.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import be.equily.app.data.*
import be.equily.app.domain.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DocumentAndReportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun malformedAndOverLimitOriginalsAreRejectedBeforeAnyWrite() = runBlocking {
        val repository = LocalRepository(context, "invalid-documents-${UUID.randomUUID()}.db")
        val root = File(context.cacheDir, "camera/invalid-${UUID.randomUUID()}").apply { mkdirs() }
        val files = AttachmentStore(context, repository, File(root, "originals"))
        try {
            val malformed = File(root, "invalid.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0, 0, 0, 0, 0, 0)) }
            val malformedUri = FileProvider.getUriForFile(context, "be.equily.app.files", malformed)
            assertTrue(runCatching { files.import(malformedUri, null) }.isFailure)
            PDFBoxResourceLoader.init(context)
            val tooManyPages = File(root, "too-many.pdf")
            PDDocument().use { pdf -> repeat(101) { pdf.addPage(PDPage()) }; pdf.save(tooManyPages) }
            val pdfUri = FileProvider.getUriForFile(context, "be.equily.app.files", tooManyPages)
            assertTrue(runCatching { files.import(pdfUri, null) }.isFailure)
            assertTrue(repository.snapshot().records.isEmpty())
            assertTrue(files.directory.listFiles().orEmpty().isEmpty())
        } finally { repository.database.close(); root.deleteRecursively() }
    }

    @Test fun embeddedAndImagePdfAreReadOfflineAndJpegOrientationIsPreserved() = runBlocking {
        val repository = LocalRepository(context, "documents-${UUID.randomUUID()}.db")
        val root = File(context.cacheDir, "document-test-${UUID.randomUUID()}").apply { mkdirs() }
        val files = AttachmentStore(context, repository, root)
        try {
            PDFBoxResourceLoader.init(context)
            val textPdf = File(root, "text.pdf")
            PDDocument().use { pdf ->
                val page = PDPage()
                pdf.addPage(page)
                PDPageContentStream(pdf, page).use { content ->
                    content.beginText(); content.setFont(PDType1Font.HELVETICA, 24f); content.newLineAtOffset(50f, 700f)
                    content.showText("ECOLE - Total TTC 125,00 EUR"); content.endText()
                }
                pdf.save(textPdf)
            }
            val embedded = Document(UUID.randomUUID().toString(), null, "application/pdf", sha256(textPdf), textPdf.length())
            textPdf.copyTo(files.file(embedded))
            assertEquals(12500L, files.extract(embedded).cents)
            val bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawText("ECOLE", 40f, 100f, Paint().apply { color = Color.BLACK; textSize = 60f; isAntiAlias = true })
            canvas.drawText("Total TTC 125,00 EUR", 40f, 240f, Paint().apply { color = Color.BLACK; textSize = 60f; isAntiAlias = true })
            val imagePdf = File(root, "image.pdf")
            val pdf = PdfDocument()
            try {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(1200, 400, 1).create())
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(page)
                imagePdf.outputStream().use { pdf.writeTo(it) }
            } finally { pdf.close() }
            val scanned = Document(UUID.randomUUID().toString(), null, "application/pdf", sha256(imagePdf), imagePdf.length())
            imagePdf.copyTo(files.file(scanned))
            assertEquals(12500L, files.extract(scanned).cents)
            val mixedPdf = File(root, "mixed.pdf")
            PDDocument.load(textPdf).use { mixed ->
                PDPageContentStream(mixed, mixed.getPage(0)).use { content ->
                    content.beginText(); content.setFont(PDType1Font.HELVETICA, 24f); content.newLineAtOffset(50f, 700f)
                    content.showText("Informations et conditions de la facture"); content.endText()
                }
                PDDocument.load(imagePdf).use { image -> mixed.importPage(image.getPage(0)); mixed.save(mixedPdf) }
            }
            val mixed = Document(UUID.randomUUID().toString(), null, "application/pdf", sha256(mixedPdf), mixedPdf.length())
            mixedPdf.copyTo(files.file(mixed))
            assertEquals(12500L, files.extract(mixed).cents)
            val jpeg = File(root, "oriented.jpg")
            jpeg.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
            ExifInterface(jpeg).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
            val rotated = Document(UUID.randomUUID().toString(), null, "image/jpeg", sha256(jpeg), jpeg.length())
            jpeg.copyTo(files.file(rotated))
            val preview = files.preview(rotated, 0).first
            assertEquals(400, preview.width); assertEquals(1200, preview.height); preview.recycle()
            assertEquals(rotated.hash, sha256(files.file(rotated)))
        } finally { repository.database.close(); root.deleteRecursively() }
    }

    @Test fun annualReportsLocalizeAndDoNotDoubleCountCrossPeriodAllocations() {
        val date = LocalDate.of(2026, 10, 10)
        val first = Charge(description = "School", cents = 12500, date = date, category = "school", child = "Child")
        val outside = Charge(description = "Outside", cents = 10000, date = date.plusYears(1))
        val payment = Movement(kind = PaymentKind.SUPPLIER, cents = 22500, date = date.plusYears(1), source = Party.SELF, allocations = mapOf(first.id to 12500, outside.id to 10000))
        val snapshot = Snapshot(listOf(Codec.record(Dossier(categoryNames = mapOf("school" to "École personnalisée"))), Codec.record(first), Codec.record(outside), Codec.record(payment)))
        val reports = Reports(context)
        val rows = reports.rows(snapshot, YearMonth.of(2026, 1), true, "fr")
        assertTrue(rows.flatten().contains("École personnalisée"))
        assertTrue(rows.any { it == listOf("À payer aux fournisseurs", "0.00", "Solde financé entre personnes", "62.50") })
        assertEquals(1, rows.count { it.firstOrNull() == payment.id })
        assertTrue(reports.rows(snapshot, YearMonth.of(2026, 10), false, "en").flatten().contains("Description"))
        val pdfFile = File(context.cacheDir, "camera/report-${UUID.randomUUID()}.pdf").apply { parentFile!!.mkdirs() }
        val csvFile = File(context.cacheDir, "camera/report-${UUID.randomUUID()}.csv")
        try {
            val dense = first.copy(description = "École et activités ".repeat(600))
            val denseSnapshot = Snapshot(listOf(Codec.record(dense)))
            reports.write(FileProvider.getUriForFile(context, "be.equily.app.files", pdfFile), denseSnapshot, YearMonth.from(date), true, "disclaimer", language = "nl")
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { renderer -> assertTrue(renderer.pageCount >= 2) } }
            reports.write(FileProvider.getUriForFile(context, "be.equily.app.files", csvFile), snapshot, YearMonth.from(date), false, "disclaimer", language = "fr")
            assertTrue(csvFile.readText().startsWith("\uFEFF\"Equily\""))
            assertTrue(csvFile.readText().contains("125.00"))
        } finally { pdfFile.delete(); csvFile.delete() }
    }

    @Test fun legacyJsonCategoriesAndInvoiceMetadataRoundTripWithoutChangingOldSplits() = runBlocking {
        val repository = LocalRepository(context, "legacy-${UUID.randomUUID()}.db")
        try {
            val charge = Charge(description = "Old school", cents = 12500, date = LocalDate.of(2025, 1, 1), supplier = "School", invoiceNumber = "42", invoiceReference = "reference")
            val oldDossier = Codec.record(Dossier()).let { it.copy(body = org.json.JSONObject(it.body).apply { remove("categoryNames"); remove("hiddenCategories") }.toString()) }
            repository.saveBatch(listOf(oldDossier, Codec.record(charge)))
            assertTrue(repository.snapshot().dossier!!.categoryNames.isEmpty())
            val dossier = repository.snapshot().dossier!!.copy(categoryNames = mapOf("school" to "Custom school", "custom_test" to "Custom"), hiddenCategories = setOf("medical"), selfBasisPoints = 7000)
            repository.save(Codec.record(dossier))
            assertEquals(dossier, Codec.decode(Codec.encode(repository.snapshot().records)).dossier)
            assertEquals(charge, repository.snapshot().charges.single())
            val agreement = Agreement(UUID.randomUUID().toString(), "Rule", "school", 7000, LocalDate.of(2026, 1, 1), "")
            repository.saveAgreement(agreement)
            val before = repository.snapshot().records
            assertTrue(runCatching { repository.saveAgreement(agreement.copy(id = UUID.randomUUID().toString())) }.isFailure)
            assertEquals(before, repository.snapshot().records)
            repository.saveAgreement(agreement.copy(selfBasisPoints = 6000))
            assertEquals(5000, repository.snapshot().charges.single().selfBasisPoints)
            assertEquals(6000, repository.snapshot().split("school", LocalDate.of(2026, 2, 1)))
            val current = repository.snapshot().records
            val ancient = RecurringRule(UUID.randomUUID().toString(), "Invalid horizon", 100, LocalDate.of(1, 1, 1), Frequency.MONTHLY, 5000, "general")
            assertTrue(runCatching { repository.save(Codec.record(ancient)) }.isFailure)
            assertEquals(current, repository.snapshot().records)
        } finally { repository.database.close() }
    }
}
