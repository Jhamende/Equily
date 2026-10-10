package be.equily.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import be.equily.app.domain.BackupCrypto
import be.equily.app.domain.InvoiceCandidate
import be.equily.app.domain.InvoiceParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= limit) { "File too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

class AttachmentStore(private val context: Context, private val repository: LocalRepository, val directory: File = File(context.filesDir, "attachments")) {
    init { directory.mkdirs() }
    fun file(document: Document) = File(directory, document.hash)
    suspend fun preview(document: Document, pageIndex: Int): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
        if (document.mime == "application/pdf") {
            ParcelFileDescriptor.open(file(document), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount in 1..100 && pageIndex in 0 until renderer.pageCount)
                    renderer.openPage(pageIndex).use { page ->
                        val scale = minOf(2.0, 1600.0 / maxOf(page.width, page.height))
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap to renderer.pageCount
                    }
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file(document).path, bounds)
            require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            requireNotNull(BitmapFactory.decodeFile(file(document).path, BitmapFactory.Options().apply { inSampleSize = sample })) to 1
        }
    }
    suspend fun remove(document: Document) = withContext(Dispatchers.IO) {
        repository.removeDocument(document.id) { hash -> File(directory, hash).delete() }
    }
    suspend fun import(uri: Uri, chargeId: String?): Document = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readLimited(25_000_000) }
        val mime = when {
            bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray()) -> "application/pdf"
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4e.toByte() -> "image/png"
            bytes.size > 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> "image/jpeg"
            else -> error("Unsupported file")
        }
        val hash = sha256(bytes)
        val duplicate = repository.snapshot().documents.firstOrNull { it.hash == hash && it.chargeId == chargeId }
        if (duplicate != null) return@withContext duplicate
        val destination = File(directory, hash)
        val document = Document(UUID.randomUUID().toString(), chargeId, mime, hash, bytes.size.toLong())
        repository.saveBatch(listOf(Codec.record(document))) {
            if (!destination.exists()) {
                val temporary = File(directory, ".${UUID.randomUUID()}")
                temporary.writeBytes(bytes)
                check(temporary.renameTo(destination))
            }
        }
        document
    }
    suspend fun extract(document: Document): InvoiceCandidate = withContext(Dispatchers.IO) {
        val original = file(document)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = if (document.mime == "application/pdf") {
                PDFBoxResourceLoader.init(context)
                val embedded = PDDocument.load(original).use { pdf ->
                    require(pdf.numberOfPages in 1..100)
                    PDFTextStripper().apply { endPage = 10 }.getText(pdf)
                }
                if (embedded.trim().length >= 20) embedded else {
                    ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            require(renderer.pageCount in 1..100)
                            val texts = mutableListOf<String>()
                            for (index in 0 until minOf(renderer.pageCount, 10)) {
                                renderer.openPage(index).use { page ->
                                    val scale = minOf(2.0, 2048.0 / maxOf(page.width, page.height))
                                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                    try {
                                        bitmap.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        texts += recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                                    } finally { bitmap.recycle() }
                                }
                            }
                            texts.joinToString("\n")
                        }
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(original.path, bounds)
                require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                val bitmap = requireNotNull(BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = sample }))
                try { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text } finally { bitmap.recycle() }
            }
            InvoiceParser.parse(text)
        } finally { recognizer.close() }
    }
}

class BackupStore(private val context: Context, private val repository: LocalRepository, private val attachments: AttachmentStore) {
    suspend fun export(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        try {
            repository.mutex.withLock {
                val current = repository.snapshot()
                current.validate()
                require(current.documents.distinctBy { it.hash }.sumOf { it.size } < 75_000_000) { "Backup exceeds local memory safety limit" }
                val zip = ByteArrayOutputStream()
                ZipOutputStream(zip).use { output ->
                    output.putNextEntry(ZipEntry("records.json"))
                    output.write(Codec.encode(current.records))
                    output.closeEntry()
                    for (document in current.documents.distinctBy { it.hash }) {
                        val bytes = attachments.file(document).readBytes()
                        require(sha256(bytes) == document.hash)
                        output.putNextEntry(ZipEntry("attachments/${document.hash}")); output.write(bytes); output.closeEntry()
                    }
                }
                val encrypted = BackupCrypto.encrypt(zip.toByteArray(), passphrase)
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(encrypted) }
            }
        } finally { passphrase.fill('\u0000') }
    }
    suspend fun restore(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val encrypted = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readLimited(100_000_000) }
            val clear = BackupCrypto.decrypt(encrypted, passphrase)
            val names = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(ByteArrayInputStream(clear)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && (entry.name == "records.json" || entry.name.matches(Regex("attachments/[a-f0-9]{64}"))))
                    require(names.add(entry.name))
                    require(names.size <= 100001)
                    val bytes = zip.readLimited(if (entry.name == "records.json") 20_000_000 else 25_000_000)
                    total += bytes.size
                    require(total <= 95_000_000)
                    File(staging, entry.name.substringAfterLast('/')).writeBytes(bytes)
                    zip.closeEntry()
                }
            }
            val current = Codec.decode(File(staging, "records.json").readBytes())
            require(names == current.documents.map { "attachments/${it.hash}" }.toSet() + "records.json")
            for (document in current.documents) {
                val bytes = File(staging, document.hash).readBytes()
                require(bytes.size.toLong() == document.size && sha256(bytes) == document.hash)
            }
            repository.mutex.withLock {
                for (document in current.documents.distinctBy { it.hash }) {
                    val destination = attachments.file(document)
                    if (destination.exists()) require(sha256(destination.readBytes()) == document.hash)
                    else check(File(staging, document.hash).renameTo(destination))
                }
                repository.database.withTransaction {
                    repository.database.records().clear()
                    repository.database.records().insert(current.records)
                }
                val retained = current.documents.map { it.hash }.toSet()
                attachments.directory.listFiles()?.filter { it.name !in retained }?.forEach { it.delete() }
            }
        } finally { passphrase.fill('\u0000'); staging.deleteRecursively() }
    }
}
