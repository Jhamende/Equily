package be.equily.app.data

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import java.io.File
import java.util.UUID

class OfflineOcr(context: Context) : Closeable {
    private val engine = TessBaseAPI()

    private companion object {
        val installation = Any()
        val models = mapOf(
            "eng" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
            "fra" to "ced037562e8c80c13122dece28dd477d399af80911a28791a66a63ac1e3445ca",
            "nld" to "ced0e5e046a84c908a6aa7accbef9a232c4a5d9a8276691b81c6ee64d02963f6",
        )
    }

    init {
        try {
            val root = File(context.noBackupFilesDir, "ocr-v1")
            synchronized(installation) {
                val directory = File(root, "tessdata")
                check(directory.isDirectory || directory.mkdirs())
                models.forEach { (language, hash) ->
                    val destination = File(directory, "$language.traineddata")
                    if (!destination.exists() || sha256(destination) != hash) {
                        val temporary = File(directory, ".${UUID.randomUUID()}")
                        try {
                            context.assets.open("tessdata/$language.traineddata").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                            require(sha256(temporary) == hash)
                            check(temporary.renameTo(destination))
                        } finally { temporary.delete() }
                    }
                }
            }
            check(engine.init(root.path, "fra+nld+eng", TessBaseAPI.OEM_LSTM_ONLY))
            engine.setDebug(false)
            engine.setVariable("debug_file", "/dev/null")
            engine.setVariable("user_defined_dpi", "300")
        } catch (failure: Throwable) { engine.recycle(); throw failure }
    }

    fun recognize(bitmap: Bitmap): String {
        engine.setImage(bitmap)
        return try { engine.getUTF8Text().orEmpty() } finally { engine.clear() }
    }

    override fun close() { engine.recycle() }
}
