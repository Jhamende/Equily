package be.equily.app

import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class DeviceCredentialTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun key(code: Int) {
        val time = SystemClock.uptimeMillis()
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0), true)
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(time, time + 20, KeyEvent.ACTION_UP, code, 0), true)
    }
    private fun confirmPin() {
        compose.waitUntil(60000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        compose.waitUntil(60000) { automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.settings:id/password_entry")?.any { it.isEditable && it.isEnabled } == true }
        val entry = automation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.android.settings:id/password_entry").first { it.isEditable && it.isEnabled }
        entry.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
        assertTrue(entry.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "246824") }))
        key(KeyEvent.KEYCODE_ENTER)
    }
    private fun nativeNode(label: String, directoryEntry: Boolean = false): android.view.accessibility.AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val pending = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                if (!directoryEntry) return node
                var ancestor = node.parent
                while (ancestor != null) {
                    if (ancestor.viewIdResourceName == "com.android.documentsui:id/dir_list") return node
                    ancestor = ancestor.parent
                }
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::add) }
        }
        return null
    }
    private fun nativeClick(label: String, directoryEntry: Boolean = false) {
        instrumentation.uiAutomation.waitForIdle(500, 30000)
        compose.waitUntil(60000) { nativeNode(label, directoryEntry) != null }
        val bounds = android.graphics.Rect()
        requireNotNull(nativeNode(label, directoryEntry)).getBoundsInScreen(bounds)
        assertFalse(bounds.isEmpty)
        val time = SystemClock.uptimeMillis()
        listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = android.view.MotionEvent.obtain(time, time + index * 20, action, bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        instrumentation.uiAutomation.waitForIdle(500, 30000)
    }

    @Test fun realAndroidCredentialCancellationAndReturnKeepDossierPrivate() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("equilySyntheticPin") == "246824")
        assumeTrue(compose.activity.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val model = ViewModelProvider(compose.activity)[EquilyModel::class.java]
        compose.waitUntil(60000) { model.ready.value && model.preferencesReady.value }
        try {
            compose.runOnIdle { model.lockEnabled(true) }
            compose.waitUntil(60000) { model.preferences.value.lock && !model.busy.value }
            compose.runOnIdle { model.locked.value = true }
            compose.onNodeWithText("Déverrouiller").performScrollTo().performClick()
            compose.waitUntil(60000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
            key(KeyEvent.KEYCODE_BACK)
            SystemClock.sleep(1000)
            key(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(60000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "be.equily.app" }
            assertTrue(model.locked.value)
            compose.onNodeWithText("Déverrouiller").assertExists().performClick()
            confirmPin()
            compose.waitUntil(60000) { !model.locked.value && compose.activity.hasWindowFocus() && instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "be.equily.app" }
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(model.locked.value)
            if (model.snapshot.value.dossier == null) {
                compose.onNodeWithText("Enregistrer").performScrollTo().performClick()
                compose.waitUntil(60000) { model.snapshot.value.dossier != null }
            }
            val file = File(compose.activity.getExternalFilesDir(null), "equily-saf-${UUID.randomUUID()}.png")
            val bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { drawColor(Color.WHITE); drawText("Total TTC 125,00 EUR", 40f, 240f, Paint().apply { color = Color.BLACK; textSize = 60f; isAntiAlias = true }) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
            instrumentation.uiAutomation.executeShellCommand("cp ${file.path} /sdcard/Download/${file.name}").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
            val copied = instrumentation.uiAutomation.executeShellCommand("ls /sdcard/Download/${file.name}").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes().toString(Charsets.UTF_8) }
            assertTrue(copied.contains(file.name))
            try {
                compose.waitUntil(60000) { model.message.value != R.string.success && compose.activity.hasWindowFocus() }
                compose.onNodeWithText("Scanner une facture").performScrollTo().performClick()
                compose.waitUntil(60000) { compose.onAllNodesWithText("Importer image / PDF").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Importer image / PDF").performClick()
                compose.waitUntil(60000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.documentsui" }
                nativeClick("More options")
                compose.waitUntil(60000) { nativeNode("Show internal storage") != null || nativeNode("Hide internal storage") != null }
                if (nativeNode("Show internal storage") != null) nativeClick("Show internal storage") else key(KeyEvent.KEYCODE_BACK)
                nativeClick("Show roots")
                nativeClick(android.os.Build.MODEL)
                nativeClick("Download", directoryEntry = true)
                nativeClick(file.name, directoryEntry = true)
                compose.waitUntil(180000) { model.scanCandidate != null && !model.busy.value }
                assertTrue(model.locked.value)
                compose.onNodeWithText("Vérifier la facture").assertDoesNotExist()
                compose.onNodeWithText("Déverrouiller").performClick()
                confirmPin()
                compose.waitUntil(60000) { !model.locked.value && compose.onAllNodesWithText("Vérifier la facture").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(12500L, model.scanCandidate?.cents)
            } finally {
                file.delete()
                instrumentation.uiAutomation.executeShellCommand("rm -f /sdcard/Download/${file.name}").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
                compose.runOnIdle { model.discardScan() }
            }
        } catch (failure: Throwable) {
            android.util.Log.e("EquilyCredential", "Synthetic active package: ${instrumentation.uiAutomation.rootInActiveWindow?.packageName}", failure)
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(compose.activity.cacheDir, "camera/equily-credential-failure.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            throw failure
        } finally {
            runBlocking { compose.activity.preferences.edit { it[androidx.datastore.preferences.core.booleanPreferencesKey("lock")] = false } }
        }
    }
}
