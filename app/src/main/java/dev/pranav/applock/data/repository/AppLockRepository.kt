package dev.pranav.applock.data.repository

import android.content.Context
import dev.pranav.applock.data.manager.BackendServiceManager
import dev.pranav.applock.services.AppLockManager

/**
 * Main repository that coordinates between different specialized repositories and managers.
 * Provides a unified interface for all app lock functionality.
 */
class AppLockRepository(private val context: Context) {

    private val preferencesRepository = PreferencesRepository(context)
    private val lockedAppsRepository = LockedAppsRepository(context)
    private val backendServiceManager = BackendServiceManager()

    fun getLockedApps(): Set<String> = lockedAppsRepository.getLockedApps()
    fun addLockedApp(packageName: String) {
        lockedAppsRepository.addLockedApp(packageName)
        AppLockManager.clearAppUnlockState(packageName)
    }

    fun addMultipleLockedApps(packageNames: Set<String>) {
        lockedAppsRepository.addMultipleLockedApps(packageNames)
        packageNames.forEach(AppLockManager::clearAppUnlockState)
    }
    fun removeLockedApp(packageName: String) = lockedAppsRepository.removeLockedApp(packageName)
    fun isAppLocked(packageName: String): Boolean = lockedAppsRepository.isAppLocked(packageName)

    fun getTriggerExcludedApps(): Set<String> = lockedAppsRepository.getTriggerExcludedApps()
    fun addTriggerExcludedApp(packageName: String) =
        lockedAppsRepository.addTriggerExcludedApp(packageName)

    fun removeTriggerExcludedApp(packageName: String) =
        lockedAppsRepository.removeTriggerExcludedApp(packageName)

    fun isAppTriggerExcluded(packageName: String): Boolean =
        lockedAppsRepository.isAppTriggerExcluded(packageName)

    fun getAntiUninstallApps(): Set<String> = lockedAppsRepository.getAntiUninstallApps()
    fun addAntiUninstallApp(packageName: String) =
        lockedAppsRepository.addAntiUninstallApp(packageName)

    fun removeAntiUninstallApp(packageName: String) =
        lockedAppsRepository.removeAntiUninstallApp(packageName)

    fun isAppAntiUninstall(packageName: String): Boolean =
        lockedAppsRepository.isAppAntiUninstall(packageName)

    fun getPassword(): String? = preferencesRepository.getPassword()
    fun setPassword(password: String) = preferencesRepository.setPassword(password)
    fun validatePassword(inputPassword: String): Boolean =
        preferencesRepository.validatePassword(inputPassword)

    fun getPattern(): String? = preferencesRepository.getPattern()
    fun setPattern(pattern: String) = preferencesRepository.setPattern(pattern)
    fun validatePattern(inputPattern: String): Boolean =
        preferencesRepository.validatePattern(inputPattern)

    fun setLockType(lockType: String) = preferencesRepository.setLockType(lockType)
    fun getLockType(): String = preferencesRepository.getLockType()

    fun setBiometricAuthEnabled(enabled: Boolean) =
        preferencesRepository.setBiometricAuthEnabled(enabled)

    fun isBiometricAuthEnabled(): Boolean = preferencesRepository.isBiometricAuthEnabled()

    fun setUseMaxBrightness(enabled: Boolean) = preferencesRepository.setUseMaxBrightness(enabled)
    fun shouldUseMaxBrightness(): Boolean = preferencesRepository.shouldUseMaxBrightness()
    fun setDisableHaptics(enabled: Boolean) = preferencesRepository.setDisableHaptics(enabled)
    fun shouldDisableHaptics(): Boolean = preferencesRepository.shouldDisableHaptics()
    fun setShowSystemApps(enabled: Boolean) = preferencesRepository.setShowSystemApps(enabled)
    fun shouldShowSystemApps(): Boolean = preferencesRepository.shouldShowSystemApps()

    fun setAntiUninstallEnabled(enabled: Boolean) =
        preferencesRepository.setAntiUninstallEnabled(enabled)

    fun isAntiUninstallEnabled(): Boolean = preferencesRepository.isAntiUninstallEnabled()
    fun setProtectEnabled(enabled: Boolean) = preferencesRepository.setProtectEnabled(enabled)
    fun isProtectEnabled(): Boolean = preferencesRepository.isProtectEnabled()

    fun setUnlockTimeDuration(minutes: Int) = preferencesRepository.setUnlockTimeDuration(minutes)
    fun getUnlockTimeDuration(): Int = preferencesRepository.getUnlockTimeDuration()
    fun setAutoUnlockEnabled(enabled: Boolean) = preferencesRepository.setAutoUnlockEnabled(enabled)
    fun isAutoUnlockEnabled(): Boolean = preferencesRepository.isAutoUnlockEnabled()

    fun setBackendImplementation(backend: BackendImplementation) =
        preferencesRepository.setBackendImplementation(backend)

    fun getBackendImplementation(): BackendImplementation =
        preferencesRepository.getBackendImplementation()

    fun isShowCommunityLink(): Boolean = preferencesRepository.isShowCommunityLink()
    fun setCommunityLinkShown(shown: Boolean) = preferencesRepository.setCommunityLinkShown(shown)
    fun isShowDonateLink(): Boolean = preferencesRepository.isShowDonateLink(context)
    fun setShowDonateLink(show: Boolean) = preferencesRepository.setShowDonateLink(context, show)

    fun isLoggingEnabled(): Boolean = preferencesRepository.isLoggingEnabled()
    fun setLoggingEnabled(enabled: Boolean) = preferencesRepository.setLoggingEnabled(enabled)

    // Stream Protection
    fun isStreamProtectionEnabled(): Boolean = preferencesRepository.isStreamProtectionEnabled()
    fun setStreamProtectionEnabled(enabled: Boolean) = preferencesRepository.setStreamProtectionEnabled(enabled)

    // Global Panic PIN
    fun setGlobalPanicPin(pin: String) = preferencesRepository.setGlobalPanicPin(pin)
    fun getGlobalPanicPin(): String? = preferencesRepository.getGlobalPanicPin()
    fun clearGlobalPanicPin() = preferencesRepository.clearGlobalPanicPin()
    fun hasGlobalPanicPin(): Boolean = preferencesRepository.hasGlobalPanicPin()
    fun validateGlobalPanicPin(input: String): Boolean = preferencesRepository.validateGlobalPanicPin(input)

    // App-Specific Panic PIN
    fun setAppPanicPin(packageName: String, pin: String) = preferencesRepository.setAppPanicPin(packageName, pin)
    fun getAppPanicPin(packageName: String): String? = preferencesRepository.getAppPanicPin(packageName)
    fun removeAppPanicPin(packageName: String) = preferencesRepository.removeAppPanicPin(packageName)
    fun hasAppPanicPin(packageName: String): Boolean = preferencesRepository.hasAppPanicPin(packageName)
    fun validateAppPanicPin(packageName: String, input: String): Boolean = preferencesRepository.validateAppPanicPin(packageName, input)
    fun validatePanicPin(input: String, packageName: String?): Boolean = preferencesRepository.validatePanicPin(input, packageName)
    fun setAppSpecificPanicPin(packageName: String, pin: String) = setAppPanicPin(packageName, pin)
    fun clearAppSpecificPanicPin(packageName: String) = removeAppPanicPin(packageName)
    fun hasAppSpecificPanicPin(packageName: String): Boolean = hasAppPanicPin(packageName)

    // Update Checker
    fun getLastUpdateCheckTime(): Long = preferencesRepository.getLastUpdateCheckTime()
    fun setLastUpdateCheckTime(time: Long) = preferencesRepository.setLastUpdateCheckTime(time)
    fun getCustomUpdateUrl(): String? = preferencesRepository.getCustomUpdateUrl()
    fun setCustomUpdateUrl(url: String?) = preferencesRepository.setCustomUpdateUrl(url)

    fun setActiveBackend(backend: BackendImplementation) =
        backendServiceManager.setActiveBackend(backend)

    companion object {
        private const val TAG = "AppLockRepository"

        fun shouldStartService(repository: AppLockRepository, serviceClass: Class<*>): Boolean {
            return repository.backendServiceManager.shouldStartService(
                serviceClass,
                repository.getBackendImplementation()
            )
        }
    }
}

enum class BackendImplementation {
    ACCESSIBILITY,
    USAGE_STATS,
    SHIZUKU
}
