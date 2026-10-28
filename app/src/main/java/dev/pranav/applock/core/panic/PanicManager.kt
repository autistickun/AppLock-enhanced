package dev.pranav.applock.core.panic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.pranav.applock.core.utils.LogUtils
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.services.AppLockManager
import rikka.shizuku.Shizuku

/**
 * Handles panic uninstallation of protected apps when a panic PIN is entered.
 * Ensures ONLY the specific protected app is uninstalled.
 */
object PanicManager {
    private const val TAG = "PanicManager"

    @Volatile
    var activePanicUninstallPackage: String? = null
        private set

    /**
     * Executes the panic uninstall sequence for the specified protected app.
     * 1. Removes the app from locked apps repository.
     * 2. Clears unlock state in AppLockManager so the lock overlay closes.
     * 3. Redirects to the Home screen immediately.
     * 4. Triggers package uninstallation silently via Shizuku if available,
     *    or through the system package uninstaller intent.
     */
    fun executePanicUninstall(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        Log.w(TAG, "EMERGENCY: Panic PIN triggered for app: $packageName")
        LogUtils.e(TAG, "EMERGENCY: Panic PIN triggered for app: $packageName")

        activePanicUninstallPackage = packageName

        val appLockRepository = context.appLockRepository()

        // 1. Remove from locked apps repository and clean up any app-specific panic pin
        appLockRepository.removeLockedApp(packageName)
        appLockRepository.removeAppPanicPin(packageName)
        AppLockManager.clearAppUnlockState(packageName)
        AppLockManager.isLockScreenShown.set(false)

        // If the app was protected by Anti-Uninstall, remove protection so uninstall is permitted
        if (appLockRepository.isAppAntiUninstall(packageName)) {
            appLockRepository.removeAntiUninstallApp(packageName)
            try {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    dev.pranav.applock.core.utils.unblockUninstallForUser(packageName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unblock uninstall for panic package: $packageName", e)
            }
        }

        // 2. Return to the Home screen immediately for secrecy and safety
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate home during panic", e)
        }

        // 3. Attempt silent uninstallation via Shizuku first if granted; otherwise fallback to PackageInstaller
        Handler(Looper.getMainLooper()).postDelayed({
            val silentSuccess = trySilentUninstallWithShizuku(packageName)
            if (silentSuccess) {
                clearActivePanic()
            } else {
                try {
                    val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(uninstallIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch package deletion intent", e)
                }
            }
        }, 200)
    }

    private fun trySilentUninstallWithShizuku(packageName: String): Boolean {
        return try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false
            }
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", "pm uninstall $packageName"),
                null,
                "/"
            ) as? rikka.shizuku.ShizukuRemoteProcess
            process?.waitFor()
            val success = process?.exitValue() == 0
            if (success) {
                Log.i(TAG, "Successfully silent-uninstalled $packageName via Shizuku shell")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku silent uninstall error", e)
            false
        }
    }

    fun clearActivePanic() {
        activePanicUninstallPackage = null
    }
}
