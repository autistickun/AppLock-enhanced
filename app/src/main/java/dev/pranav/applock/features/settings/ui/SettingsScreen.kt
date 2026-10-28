package dev.pranav.applock.features.settings.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import dev.pranav.applock.R
import dev.pranav.applock.core.broadcast.DeviceAdmin
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.core.utils.LogUtils
import dev.pranav.applock.core.utils.hasUsagePermission
import dev.pranav.applock.core.utils.isAccessibilityServiceEnabled
import dev.pranav.applock.core.utils.openAccessibilitySettings
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.features.admin.AdminDisableActivity
import dev.pranav.applock.services.ShizukuAppLockService
import dev.pranav.applock.services.UsageLockService
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.pranav.applock.core.update.UpdateDialog
import dev.pranav.applock.core.update.UpdateInfo
import dev.pranav.applock.core.update.UpdateManager
import dev.pranav.applock.core.update.UpdateResult
import dev.pranav.applock.core.utils.isTranssionDevice
import dev.pranav.applock.core.utils.launchInfinixAutoStartSettings
import dev.pranav.applock.core.utils.openAppInfoSettings
import kotlinx.coroutines.launch
import android.os.Build
import dev.pranav.applock.ui.icons.*
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val appLockRepository = remember { AppLockRepository(context) }

    var showUnlockTimeDialog by remember { mutableStateOf(false) }

    val shizukuPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_screen_shizuku_permission_granted),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.settings_screen_shizuku_permission_required_desc),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var autoUnlock by remember { mutableStateOf(appLockRepository.isAutoUnlockEnabled()) }
    var useMaxBrightness by remember { mutableStateOf(appLockRepository.shouldUseMaxBrightness()) }
    var useBiometricAuth by remember { mutableStateOf(appLockRepository.isBiometricAuthEnabled()) }
    var unlockTimeDuration by remember { mutableIntStateOf(appLockRepository.getUnlockTimeDuration()) }
    var antiUninstallEnabled by remember { mutableStateOf(appLockRepository.isAntiUninstallEnabled()) }
    var disableHapticFeedback by remember { mutableStateOf(appLockRepository.shouldDisableHaptics()) }
    var loggingEnabled by remember { mutableStateOf(appLockRepository.isLoggingEnabled()) }

    var streamProtection by remember { mutableStateOf(appLockRepository.isStreamProtectionEnabled()) }
    var showPanicPinDialog by remember { mutableStateOf(false) }
    var panicPinConfigured by remember { mutableStateOf(appLockRepository.hasGlobalPanicPin()) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDeviceAdminDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    val biometricManager = remember { BiometricManager.from(context) }
    val isBiometricAvailable = remember {
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    if (showPanicPinDialog) {
        PanicPinDialog(
            hasConfiguredPin = panicPinConfigured,
            onDismiss = { showPanicPinDialog = false },
            onSave = { newPin ->
                val isPasswordMatch = appLockRepository.validatePassword(newPin)
                val isPatternMatch = appLockRepository.validatePattern(newPin)
                if (isPasswordMatch || isPatternMatch) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.panic_pin_must_be_different),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    appLockRepository.setGlobalPanicPin(newPin)
                    panicPinConfigured = true
                    showPanicPinDialog = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.panic_pin_set_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onClear = {
                appLockRepository.clearGlobalPanicPin()
                panicPinConfigured = false
                showPanicPinDialog = false
                Toast.makeText(
                    context,
                    context.getString(R.string.panic_pin_cleared),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    availableUpdate?.let { info ->
        UpdateDialog(
            updateInfo = info,
            onDismiss = { availableUpdate = null }
        )
    }

    if (showUnlockTimeDialog) {
        UnlockTimeDurationDialog(
            currentDuration = unlockTimeDuration,
            onDismiss = { showUnlockTimeDialog = false },
            onConfirm = { newDuration ->
                unlockTimeDuration = newDuration
                appLockRepository.setUnlockTimeDuration(newDuration)
                showUnlockTimeDialog = false
            }
        )
    }

    if (showPermissionDialog) {
        PermissionRequiredDialog(
            onDismiss = { showPermissionDialog = false },
            onConfirm = {
                showPermissionDialog = false
                showDeviceAdminDialog = true
            }
        )
    }

    if (showDeviceAdminDialog) {
        DeviceAdminDialog(
            onDismiss = { showDeviceAdminDialog = false },
            onConfirm = {
                showDeviceAdminDialog = false
                val component = ComponentName(context, DeviceAdmin::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        context.getString(R.string.main_screen_device_admin_explanation)
                    )
                }
                context.startActivity(intent)
            }
        )
    }

    if (showAccessibilityDialog) {
        AccessibilityDialog(
            onDismiss = { showAccessibilityDialog = false },
            onConfirm = {
                showAccessibilityDialog = false
                openAccessibilitySettings(context)
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val component = ComponentName(context, DeviceAdmin::class.java)
                if (!dpm.isAdminActive(component)) {
                    showDeviceAdminDialog = true
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_screen_back_cd)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val packageInfo = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (_: Exception) {
                        null
                    }
                }
                val versionName = packageInfo?.versionName ?: "Unknown"
                Text(
                    text = stringResource(R.string.settings_screen_version_template, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }

            item {
                SectionTitle(text = stringResource(R.string.settings_screen_lock_screen_customization_title))
            }

            item {
                SettingsGroup(
                    items = listOf(
                        ToggleSettingItem(
                            icon = BrightnessHigh,
                            title = stringResource(R.string.settings_screen_max_brightness_title),
                            subtitle = stringResource(R.string.settings_screen_max_brightness_desc),
                            checked = useMaxBrightness,
                            enabled = true,
                            onCheckedChange = { isChecked ->
                                useMaxBrightness = isChecked
                                appLockRepository.setUseMaxBrightness(isChecked)
                            }
                        ),
                        ToggleSettingItem(
                            icon = if (useBiometricAuth) Fingerprint else FingerprintOff,
                            title = stringResource(R.string.settings_screen_biometric_auth_title),
                            subtitle = if (isBiometricAvailable)
                                stringResource(R.string.settings_screen_biometric_auth_desc_available)
                            else
                                stringResource(R.string.settings_screen_biometric_auth_desc_unavailable),
                            checked = useBiometricAuth && isBiometricAvailable,
                            enabled = isBiometricAvailable,
                            onCheckedChange = { isChecked ->
                                useBiometricAuth = isChecked
                                appLockRepository.setBiometricAuthEnabled(isChecked)
                            }
                        ),
                        ToggleSettingItem(
                            icon = Icons.Default.Vibration,
                            title = stringResource(R.string.settings_screen_haptic_feedback_title),
                            subtitle = stringResource(R.string.settings_screen_haptic_feedback_desc),
                            checked = disableHapticFeedback,
                            enabled = true,
                            onCheckedChange = { isChecked ->
                                disableHapticFeedback = isChecked
                                appLockRepository.setDisableHaptics(isChecked)
                            }
                        ),
                        ToggleSettingItem(
                            icon = Icons.Default.ShieldMoon,
                            title = stringResource(R.string.settings_screen_auto_unlock_title),
                            subtitle = stringResource(R.string.settings_screen_auto_unlock_desc),
                            checked = autoUnlock,
                            enabled = true,
                            onCheckedChange = { isChecked ->
                                autoUnlock = isChecked
                                appLockRepository.setAutoUnlockEnabled(isChecked)
                            }
                        ),
                        ToggleSettingItem(
                            icon = Icons.Default.VisibilityOff,
                            title = stringResource(R.string.stream_protection_title),
                            subtitle = stringResource(R.string.stream_protection_desc),
                            checked = streamProtection,
                            enabled = true,
                            onCheckedChange = { isChecked ->
                                streamProtection = isChecked
                                appLockRepository.setStreamProtectionEnabled(isChecked)
                            }
                        )
                    )
                )
            }

            item {
                SectionTitle(text = stringResource(R.string.settings_screen_security_title))
            }

            item {
                SettingsGroup(
                    items = listOf(
                        ActionSettingItem(
                            icon = Icons.Default.Lock,
                            title = stringResource(R.string.settings_screen_change_pin_title),
                            subtitle = stringResource(R.string.settings_screen_change_pin_desc),
                            onClick = { navController.navigate(Screen.ChangePassword.route) }
                        ),
                        ActionSettingItem(
                            icon = Icons.Default.Warning,
                            title = stringResource(R.string.panic_pin_title),
                            subtitle = if (panicPinConfigured)
                                stringResource(R.string.panic_pin_status_configured) + " • " + stringResource(R.string.panic_pin_desc)
                            else
                                stringResource(R.string.panic_pin_status_not_configured) + " • " + stringResource(R.string.panic_pin_desc),
                            onClick = { showPanicPinDialog = true }
                        ),
                        ActionSettingItem(
                            icon = Timer,
                            title = stringResource(R.string.settings_screen_unlock_duration_title),
                            subtitle = if (unlockTimeDuration > 0) {
                                if (unlockTimeDuration > 10_000) "Until screen off"
                                else stringResource(
                                    R.string.settings_screen_unlock_duration_summary_minutes,
                                    unlockTimeDuration
                                )
                            } else stringResource(R.string.settings_screen_unlock_duration_summary_immediate),
                            onClick = { showUnlockTimeDialog = true }
                        ),
                        ToggleSettingItem(
                            icon = Icons.Default.Lock,
                            title = stringResource(R.string.settings_screen_anti_uninstall_title),
                            subtitle = stringResource(R.string.settings_screen_anti_uninstall_desc),
                            checked = antiUninstallEnabled,
                            enabled = true,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    val dpm =
                                        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                    val component = ComponentName(context, DeviceAdmin::class.java)
                                    val hasDeviceAdmin = dpm.isAdminActive(component)
                                    val hasAccessibility = context.isAccessibilityServiceEnabled()

                                    when {
                                        !hasDeviceAdmin && !hasAccessibility -> {
                                            showPermissionDialog = true
                                        }
                                        !hasDeviceAdmin -> {
                                            showDeviceAdminDialog = true
                                        }
                                        !hasAccessibility -> {
                                            showAccessibilityDialog = true
                                        }
                                        else -> {
                                            antiUninstallEnabled = true
                                            appLockRepository.setAntiUninstallEnabled(true)
                                        }
                                    }
                                } else {
                                    context.startActivity(
                                        Intent(context, AdminDisableActivity::class.java)
                                    )
                                }
                            }
                        )
                    )
                )
            }

            item {
                SectionTitle(text = stringResource(R.string.settings_screen_advanced_title))
            }

            item {
                SettingsGroup(
                    items = listOf(
                        ActionSettingItem(
                            icon = Icons.Outlined.Security,
                            title = stringResource(R.string.settings_Screen_export_audit),
                            subtitle = stringResource(R.string.settings_screen_export_audit_desc),
                            onClick = {
                                val uri = LogUtils.exportAuditLogs()
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share audit logs")
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_screen_export_logs_error),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ),
                        ActionSettingItem(
                            icon = Icons.Outlined.BugReport,
                            title = stringResource(R.string.settings_screen_export_logs_title),
                            subtitle = stringResource(R.string.settings_screen_export_logs_desc),
                            onClick = {
                                val uri = LogUtils.exportLogs()
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share logs")
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_screen_export_logs_error),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ),
                        ToggleSettingItem(
                            icon = Icons.Default.Troubleshoot,
                            title = "Logging",
                            subtitle = "Enable debug logging for troubleshooting",
                            checked = loggingEnabled,
                            enabled = true,
                            onCheckedChange = { isChecked ->
                                loggingEnabled = isChecked
                                appLockRepository.setLoggingEnabled(isChecked)
                                LogUtils.setLoggingEnabled(isChecked)
                            }
                        )
                    )
                )
            }

            item {
                BackendSelectionCard(
                    appLockRepository = appLockRepository,
                    context = context,
                    shizukuPermissionLauncher = shizukuPermissionLauncher
                )
            }

            item {
                SectionTitle(text = stringResource(R.string.device_compatibility_title))
            }

            item {
                val items = remember(context) {
                    val list = mutableListOf<ActionSettingItem>()
                    list.add(
                        ActionSettingItem(
                            icon = Icons.Default.PhoneAndroid,
                            title = context.getString(R.string.infinix_xos_title),
                            subtitle = context.getString(R.string.infinix_xos_desc),
                            onClick = {
                                launchInfinixAutoStartSettings(context)
                            }
                        )
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        list.add(
                            ActionSettingItem(
                                icon = Icons.Default.Info,
                                title = context.getString(R.string.android_13_restricted_settings_title),
                                subtitle = context.getString(R.string.android_13_restricted_settings_desc),
                                onClick = {
                                    openAppInfoSettings(context)
                                }
                            )
                        )
                    }
                    list
                }
                SettingsGroup(items = items)
            }

            item {
                SectionTitle(text = stringResource(R.string.updates_section_title))
            }

            item {
                SettingsGroup(
                    items = listOf(
                        ActionSettingItem(
                            icon = Icons.Default.Refresh,
                            title = stringResource(R.string.check_for_updates_title),
                            subtitle = if (isCheckingUpdates) {
                                stringResource(R.string.checking_for_updates_toast)
                            } else {
                                stringResource(R.string.check_for_updates_desc)
                            },
                            onClick = {
                                if (!isCheckingUpdates) {
                                    isCheckingUpdates = true
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.checking_for_updates_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    coroutineScope.launch {
                                        when (val result = UpdateManager.checkForUpdate(context)) {
                                            is UpdateResult.Available -> {
                                                isCheckingUpdates = false
                                                availableUpdate = result.updateInfo
                                            }
                                            is UpdateResult.UpToDate -> {
                                                isCheckingUpdates = false
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.up_to_date_toast),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            is UpdateResult.Error -> {
                                                isCheckingUpdates = false
                                                Toast.makeText(
                                                    context,
                                                    "Update check failed: ${result.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    )
                )
            }

            item {
                LinksSection()
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp, top = 4.dp)
    )
}

sealed class SettingItemType {
    data class Toggle(
        val icon: ImageVector,
        val title: String,
        val subtitle: String,
        val checked: Boolean,
        val enabled: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ): SettingItemType()

    data class Action(
        val icon: ImageVector,
        val title: String,
        val subtitle: String,
        val onClick: () -> Unit
    ): SettingItemType()
}

data class ToggleSettingItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val enabled: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

data class ActionSettingItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
fun SettingsGroup(
    items: List<Any>
) {
    Column {
        items.forEachIndexed { index, item ->
            SettingsCard(index = index, listSize = items.size) {
                when (item) {
                    is ToggleSettingItem -> {
                        ToggleSettingRow(
                            icon = item.icon,
                            title = item.title,
                            subtitle = item.subtitle,
                            checked = item.checked,
                            enabled = item.enabled,
                            onCheckedChange = item.onCheckedChange
                        )
                    }

                    is ActionSettingItem -> {
                        ActionSettingRow(
                            icon = item.icon,
                            title = item.title,
                            subtitle = item.subtitle,
                            onClick = item.onClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    index: Int,
    listSize: Int,
    content: @Composable () -> Unit
) {
    val shape = when {
        listSize == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )

        index == listSize - 1 -> RoundedCornerShape(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )

        else -> RoundedCornerShape(6.dp)
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(
            initialScale = 0.95f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = shape
        ) {
            content()
        }
    }
}

@Composable
fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable(enabled = enabled) { if (enabled) onCheckedChange(!checked) }
            .padding(vertical = 2.dp, horizontal = 4.dp),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        },
        trailingContent = {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 4.dp),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        },
        trailingContent = {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
fun UnlockTimeDurationDialog(
    currentDuration: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val durations = listOf(0, 1, 5, 15, 30, 60, Integer.MAX_VALUE)
    var selectedDuration by remember { mutableIntStateOf(currentDuration) }

    if (!durations.contains(selectedDuration)) {
        selectedDuration = durations.minByOrNull { abs(it - currentDuration) } ?: 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_screen_unlock_duration_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_screen_unlock_duration_dialog_description_new))
                durations.forEach { duration ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDuration = duration }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDuration == duration,
                            onClick = { selectedDuration = duration }
                        )
                        Text(
                            text = when (duration) {
                                0 -> stringResource(R.string.settings_screen_unlock_duration_dialog_option_immediate)
                                1 -> stringResource(
                                    R.string.settings_screen_unlock_duration_dialog_option_minute,
                                    duration
                                )
                                60 -> stringResource(R.string.settings_screen_unlock_duration_dialog_option_hour)
                                Integer.MAX_VALUE -> "Until Screen Off"
                                else -> stringResource(
                                    R.string.settings_screen_unlock_duration_summary_minutes,
                                    duration
                                )
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDuration) }) {
                Text(stringResource(R.string.confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
fun BackendSelectionCard(
    appLockRepository: AppLockRepository,
    context: Context,
    shizukuPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    var selectedBackend by remember { mutableStateOf(appLockRepository.getBackendImplementation()) }

    Column {
        SectionTitle(text = stringResource(R.string.settings_screen_backend_implementation_title))

        Column {
            BackendImplementation.entries.forEachIndexed { index, backend ->
                SettingsCard(
                    index = index,
                    listSize = BackendImplementation.entries.size
                ) {
                    BackendSelectionItem(
                        backend = backend,
                        isSelected = selectedBackend == backend,
                        onClick = {
                            when (backend) {
                                BackendImplementation.SHIZUKU -> {
                                    if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED) {
                                        if (Shizuku.isPreV11()) {
                                            shizukuPermissionLauncher.launch(ShizukuProvider.PERMISSION)
                                        } else if (Shizuku.pingBinder()) {
                                            Shizuku.requestPermission(423)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.settings_screen_shizuku_not_running_toast),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        selectedBackend = backend
                                        appLockRepository.setBackendImplementation(
                                            BackendImplementation.SHIZUKU
                                        )
                                        context.startService(
                                            Intent(context, ShizukuAppLockService::class.java)
                                        )
                                    }
                                }
                                BackendImplementation.USAGE_STATS -> {
                                    if (!context.hasUsagePermission()) {
                                        val intent =
                                            Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.settings_screen_usage_permission_toast),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@BackendSelectionItem
                                    }
                                    selectedBackend = backend
                                    appLockRepository.setBackendImplementation(BackendImplementation.USAGE_STATS)
                                    context.startService(
                                        Intent(context, UsageLockService::class.java)
                                    )
                                }
                                BackendImplementation.ACCESSIBILITY -> {
                                    if (!context.isAccessibilityServiceEnabled()) {
                                        openAccessibilitySettings(context)
                                        return@BackendSelectionItem
                                    }
                                    selectedBackend = backend
                                    appLockRepository.setBackendImplementation(BackendImplementation.ACCESSIBILITY)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackendSelectionItem(
    backend: BackendImplementation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 2.dp, horizontal = 4.dp),
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getBackendDisplayName(backend),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                if (backend == BackendImplementation.SHIZUKU) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ) {
                        Text(
                            text = stringResource(R.string.settings_screen_backend_implementation_shizuku_advanced),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = getBackendDescription(backend),
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getBackendIcon(backend),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        },
        trailingContent = {
            Box(
                contentAlignment = Alignment.Center
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

private fun getBackendDisplayName(backend: BackendImplementation): String {
    return when (backend) {
        BackendImplementation.ACCESSIBILITY -> "Accessibility Service"
        BackendImplementation.USAGE_STATS -> "Usage Statistics"
        BackendImplementation.SHIZUKU -> "Shizuku Service"
    }
}

private fun getBackendDescription(backend: BackendImplementation): String {
    return when (backend) {
        BackendImplementation.ACCESSIBILITY -> "Standard method that works on most devices"
        BackendImplementation.USAGE_STATS -> "Experimental method using app usage statistics"
        BackendImplementation.SHIZUKU -> "Advanced method using Shizuku and internal APIs"
    }
}

private fun getBackendIcon(backend: BackendImplementation): ImageVector {
    return when (backend) {
        BackendImplementation.ACCESSIBILITY -> Accessibility
        BackendImplementation.USAGE_STATS -> Icons.Default.QueryStats
        BackendImplementation.SHIZUKU -> Icons.Default.AutoAwesome
    }
}

@Composable
fun PermissionRequiredDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_screen_permission_required_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_screen_permission_required_dialog_text_1))
                Text(stringResource(R.string.settings_screen_permission_required_dialog_text_2))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.grant_permission_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
fun DeviceAdminDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_screen_device_admin_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_screen_device_admin_dialog_text_1))
                Text(stringResource(R.string.settings_screen_device_admin_dialog_text_2))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.enable_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
fun AccessibilityDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_screen_accessibility_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_screen_accessibility_dialog_text_1))
                Text(stringResource(R.string.settings_screen_accessibility_dialog_text_2))
                Text(stringResource(R.string.settings_screen_accessibility_dialog_text_3))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.enable_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
fun LinksSection() {
    val context = LocalContext.current

    Column {
        SectionTitle(text = "Links")

        Column {
            SettingsCard(index = 0, listSize = 2) {
                LinkItem(
                    title = "Source Code",
                    icon = Icons.Outlined.Code,
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/autistickun/AppLock-enhanced".toUri()
                        )
                        context.startActivity(intent)
                    }
                )
            }

            SettingsCard(index = 1, listSize = 2) {
                LinkItem(
                    title = "Report Issue",
                    icon = Icons.Outlined.BugReport,
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/autistickun/AppLock-enhanced/issues".toUri()
                        )
                        context.startActivity(intent)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/autistickun/AppLock-enhanced".toUri()
                    )
                    context.startActivity(intent)
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Made with ❤️ by autistickun",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AppLock Enhanced • Free & Open Source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LinkItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 4.dp),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        },
        trailingContent = {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
fun PanicPinDialog(
    hasConfiguredPin: Boolean,
    title: String = stringResource(R.string.panic_pin_set_dialog_title),
    description: String = stringResource(R.string.panic_pin_desc),
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 16 && it.all { c -> c.isDigit() }) {
                            pin = it
                            errorMessage = null
                        }
                    },
                    label = { Text(stringResource(R.string.panic_pin_input_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.length <= 16 && it.all { c -> c.isDigit() }) {
                            confirmPin = it
                            errorMessage = null
                        }
                    },
                    label = { Text(stringResource(R.string.panic_pin_confirm_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length < 4) {
                        errorMessage = "PIN must be at least 4 digits"
                    } else if (pin != confirmPin) {
                        errorMessage = "PINs do not match"
                    } else {
                        onSave(pin)
                    }
                }
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasConfiguredPin) {
                    TextButton(onClick = onClear) {
                        Text(
                            text = stringResource(R.string.clear_panic_pin_button),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        }
    )
}

