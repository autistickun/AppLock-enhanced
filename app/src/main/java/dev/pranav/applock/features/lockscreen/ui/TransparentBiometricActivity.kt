package dev.pranav.applock.features.lockscreen.ui

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.services.AppLockManager

class TransparentBiometricActivity : FragmentActivity() {
    private val TAG = "TransparentBiometric"
    private var lockedPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockedPackageName = intent.getStringExtra("locked_package")

        // Stream Protection: Black out during screen recordings / shares if enabled
        if (applicationContext.appLockRepository().isStreamProtectionEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        setContent {
            // Truly transparent surface so the lock overlay and PIN keypad remain visible underneath
            Box(modifier = Modifier.fillMaxSize())
        }

        AppLockManager.reportBiometricAuthStarted()

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    AppLockManager.reportBiometricAuthFinished()
                    // Finished without unlocking; overlay remains active with PIN pad ready
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    AppLockManager.reportBiometricAuthFinished()
                    AppLockManager.isLockScreenShown.set(false)
                    lockedPackageName?.let {
                        AppLockManager.temporarilyUnlockAppWithBiometrics(it)
                    }
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed (fingerprint not recognized)")
                }
            })

        val appName = try {
            if (!lockedPackageName.isNullOrEmpty()) {
                val pm = packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(lockedPackageName!!, 0)).toString()
            } else {
                getString(R.string.this_app)
            }
        } catch (_: Exception) {
            getString(R.string.this_app)
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app_title, appName))
            .setSubtitle(getString(R.string.confirm_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin_button))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setConfirmationRequired(false)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Biometric failed to start", e)
            AppLockManager.reportBiometricAuthFinished()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            AppLockManager.reportBiometricAuthFinished()
        }
    }
}
