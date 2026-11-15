package com.assayflux.pockethost

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Compatibility host for AndroidX BiometricPrompt.
 *
 * MainActivity is a ComponentActivity so Compose ActivityResultRegistry launchers
 * never pass generated 32-bit request codes through FragmentActivity's 16-bit
 * legacy validation. Only this activity uses FragmentActivity, launched with a
 * fixed, valid legacy request code.
 */
class BiometricHostActivity : FragmentActivity() {
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                setResult(Activity.RESULT_OK)
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock AssayFlux Pocket Host")
            .setSubtitle("Authenticate to access this device's hosting controls.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(authenticators)
        } else {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
        }
        prompt.authenticate(builder.build())
    }
}
