package be.equily.app

import android.os.Bundle
import android.os.Build
import android.app.KeyguardManager
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : FragmentActivity() {
    private lateinit var model: EquilyModel
    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) model.locked.value = false
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        model = ViewModelProvider(this)[EquilyModel::class.java]
        setContent { EquilyApp(model, ::unlock, ::canLock) }
    }
    private fun canLock(): Boolean = if (Build.VERSION.SDK_INT < 30) getSystemService(KeyguardManager::class.java).isDeviceSecure else BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    private fun unlock() {
        if (!canLock()) {
            model.message.value = R.string.lock_setup
            startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
            return
        }
        if (Build.VERSION.SDK_INT < 30) {
            val intent = getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.unlock))
            if (intent != null) credential.launch(intent)
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { model.locked.value = false }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode !in listOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON)) model.message.value = R.string.error
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Equily")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build())
    }
    override fun onStop() {
        if (::model.isInitialized && model.preferences.value.lock) model.locked.value = true
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.refresh()
    }
}
