package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.db.AppGuardDatabase
import com.example.data.model.*
import com.example.data.security.PinSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ProtectionRepository(
    private val context: Context,
    private val database: AppGuardDatabase = AppGuardDatabase.getInstance(context),
    val pinSecurity: PinSecurity = PinSecurity(context)
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_guard_settings", Context.MODE_PRIVATE)

    // DAOs
    val protectedAppDao = database.protectedAppDao()
    val profileDao = database.profileDao()
    val scheduleDao = database.scheduleDao()
    val intruderLogDao = database.intruderLogDao()
    val hiddenNotificationDao = database.hiddenNotificationDao()

    // In-memory active unlock sessions (Key = packageName)
    private val temporaryUnlockSessions = ConcurrentHashMap<String, TemporaryUnlockSession>()

    // In-memory global temporary unlock
    @Volatile
    private var globalTemporaryUnlockUntil: Long = 0L

    // In-memory recent apps authentication session
    @Volatile
    private var recentAuthSession: RecentAuthSession = RecentAuthSession()

    // Settings StateFlow
    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppGuardSettings> = _settingsFlow.asStateFlow()

    // Real-time foreground package observation for diagnostics
    val currentForegroundPackage = MutableStateFlow<String>("")
    val previousForegroundPackage = MutableStateFlow<String>("")
    val currentStateMachineState = MutableStateFlow<String>("IDLE")

    init {
        // Automatically sync installed apps in background on startup
        val appScope = (context.applicationContext as? com.example.AppGuardApplication)?.applicationScope
        if (appScope != null) {
            appScope.launch(Dispatchers.IO) {
                refreshInstalledApps()
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                refreshInstalledApps()
            }
        }
    }

    private fun loadSettings(): AppGuardSettings {
        val unlockModeStr = prefs.getString("unlock_relock_mode", UnlockRelockMode.SCREEN_OFF.name) ?: UnlockRelockMode.SCREEN_OFF.name
        val unlockMode = try {
            UnlockRelockMode.valueOf(unlockModeStr)
        } catch (_: Exception) {
            UnlockRelockMode.SCREEN_OFF
        }

        val kbBehaviorStr = prefs.getString("keyboard_lock_behavior", KeyboardLockBehavior.ALWAYS_UNLOCKED.name) ?: KeyboardLockBehavior.ALWAYS_UNLOCKED.name
        val kbBehavior = try {
            KeyboardLockBehavior.valueOf(kbBehaviorStr)
        } catch (_: Exception) {
            KeyboardLockBehavior.ALWAYS_UNLOCKED
        }

        val notifHidingModeStr = prefs.getString("notification_hiding_mode", NotificationHidingMode.FOLLOW_RELOCK_STATE.name) ?: NotificationHidingMode.FOLLOW_RELOCK_STATE.name
        val notifHidingMode = try {
            NotificationHidingMode.valueOf(notifHidingModeStr)
        } catch (_: Exception) {
            NotificationHidingMode.FOLLOW_RELOCK_STATE
        }

        return AppGuardSettings(
            isProtectionEnabled = prefs.getBoolean("is_protection_enabled", true),
            isGlobalLockActive = prefs.getBoolean("is_global_lock_active", false),
            isBiometricEnabled = prefs.getBoolean("is_biometric_enabled", true),
            isShufflePinEnabled = prefs.getBoolean("is_shuffle_pin_enabled", false),
            isProtectSelfEnabled = prefs.getBoolean("is_protect_self_enabled", false),
            isSettingsProtectionEnabled = prefs.getBoolean("is_settings_protection_enabled", true),
            isNotificationHidingEnabled = prefs.getBoolean("is_notification_hiding_enabled", true),
            notificationHidingMode = notifHidingMode,
            unlockRelockMode = unlockMode,
            timeoutMinutes = prefs.getInt("timeout_minutes", 1),
            openCountLimit = prefs.getInt("open_count_limit", 1),
            isRecentAppsLockEnabled = prefs.getBoolean("is_recent_apps_lock_enabled", true),
            isRecentAppsAlwaysLocked = prefs.getBoolean("is_recent_apps_always_locked", false),
            isAutoLockNewAppsEnabled = prefs.getBoolean("is_auto_lock_new_apps_enabled", false),
            isCrashScreenEnabled = prefs.getBoolean("is_crash_screen_enabled", false),
            isIntruderDetectionEnabled = prefs.getBoolean("is_intruder_detection_enabled", false),
            intruderFailedAttemptsThreshold = prefs.getInt("intruder_failed_threshold", 3),
            activeProfileId = if (prefs.contains("active_profile_id")) prefs.getLong("active_profile_id", 0L).takeIf { it > 0 } else null,
            isDarkTheme = prefs.getBoolean("is_dark_theme", true),
            language = prefs.getString("language", "ar") ?: "ar",
            keyboardLockBehavior = kbBehavior
        )
    }

    fun updateSettings(update: (AppGuardSettings) -> AppGuardSettings) {
        val current = _settingsFlow.value
        val updated = update(current)
        _settingsFlow.value = updated

        // Force clear all temporary unlocks if any relock-related condition changes
        if (current.unlockRelockMode != updated.unlockRelockMode ||
            current.timeoutMinutes != updated.timeoutMinutes ||
            current.openCountLimit != updated.openCountLimit
        ) {
            clearAllTemporaryUnlocks()
        }

        prefs.edit().apply {
            putBoolean("is_protection_enabled", updated.isProtectionEnabled)
            putBoolean("is_global_lock_active", updated.isGlobalLockActive)
            putBoolean("is_biometric_enabled", updated.isBiometricEnabled)
            putBoolean("is_shuffle_pin_enabled", updated.isShufflePinEnabled)
            putBoolean("is_protect_self_enabled", updated.isProtectSelfEnabled)
            putBoolean("is_settings_protection_enabled", updated.isSettingsProtectionEnabled)
            putBoolean("is_notification_hiding_enabled", updated.isNotificationHidingEnabled)
            putString("notification_hiding_mode", updated.notificationHidingMode.name)
            putString("unlock_relock_mode", updated.unlockRelockMode.name)
            putInt("timeout_minutes", updated.timeoutMinutes)
            putInt("open_count_limit", updated.openCountLimit)
            putBoolean("is_recent_apps_lock_enabled", updated.isRecentAppsLockEnabled)
            putBoolean("is_recent_apps_always_locked", updated.isRecentAppsAlwaysLocked)
            putBoolean("is_auto_lock_new_apps_enabled", updated.isAutoLockNewAppsEnabled)
            putBoolean("is_crash_screen_enabled", updated.isCrashScreenEnabled)
            putBoolean("is_intruder_detection_enabled", updated.isIntruderDetectionEnabled)
            putInt("intruder_failed_threshold", updated.intruderFailedAttemptsThreshold)
            if (updated.activeProfileId != null) {
                putLong("active_profile_id", updated.activeProfileId)
            } else {
                remove("active_profile_id")
            }
            putBoolean("is_dark_theme", updated.isDarkTheme)
            putString("language", updated.language)
            putString("keyboard_lock_behavior", updated.keyboardLockBehavior.name)
            apply()
        }
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean("is_onboarding_complete", false)
    }

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean("is_onboarding_complete", complete).apply()
    }

    suspend fun refreshInstalledApps() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val installedApps = mutableListOf<ProtectedAppEntity>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName) continue // Skip self

            val appName = resolveInfo.loadLabel(pm).toString()
            val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val existing = protectedAppDao.getApp(pkg)
            if (existing != null) {
                // Keep existing lock status
                installedApps.add(existing.copy(appName = appName, isSystemApp = isSystem))
            } else {
                installedApps.add(
                    ProtectedAppEntity(
                        packageName = pkg,
                        appName = appName,
                        isLocked = false,
                        isSystemApp = isSystem
                    )
                )
            }
        }

        protectedAppDao.insertAll(installedApps)
    }

    // App locking actions
    suspend fun setAppLocked(packageName: String, isLocked: Boolean) = withContext(Dispatchers.IO) {
        protectedAppDao.setLocked(packageName, isLocked)
    }

    suspend fun setAppsLockedBulk(packageNames: List<String>, isLocked: Boolean) = withContext(Dispatchers.IO) {
        protectedAppDao.setLockedBulk(packageNames, isLocked)
    }

    suspend fun setAllAppsLocked(isLocked: Boolean) = withContext(Dispatchers.IO) {
        protectedAppDao.setLockedAll(isLocked)
    }

    // Global Lock & Unlock
    fun setGlobalLock(active: Boolean) {
        // A global lock must revoke already-issued temporary unlocks immediately.
        if (active) clearAllTemporaryUnlocks()
        updateSettings { it.copy(isGlobalLockActive = active) }
    }

    // Profile actions
    suspend fun activateProfile(profileId: Long?) = withContext(Dispatchers.IO) {
        profileDao.deactivateAllProfiles()
        if (profileId != null) {
            profileDao.activateProfile(profileId)
        }
        updateSettings { it.copy(activeProfileId = profileId) }
    }

    suspend fun duplicateProfile(profileId: Long) = withContext(Dispatchers.IO) {
        val source = profileDao.getProfileById(profileId) ?: return@withContext
        val newProfileId = profileDao.insertProfile(
            ProfileEntity(
                name = "${source.name} (نسخة)",
                icon = source.icon,
                isActive = false,
                isPreset = false
            )
        )
        val sourceApps = profileDao.getProfileAppsSync(profileId)
        val newApps = sourceApps.map { it.copy(profileId = newProfileId) }
        profileDao.insertProfileApps(newApps)
    }

    // Temporary Sessions
    fun grantTemporaryUnlock(packageName: String) {
        val settings = _settingsFlow.value
        val durationMillis = if (settings.unlockRelockMode == UnlockRelockMode.TIMEOUT) {
            settings.timeoutMinutes * 60 * 1000L
        } else {
            0L
        }
        val openCount = if (settings.unlockRelockMode == UnlockRelockMode.OPEN_COUNT) {
            settings.openCountLimit
        } else {
            1
        }
        val session = TemporaryUnlockSession(
            packageName = packageName,
            unlockedAt = System.currentTimeMillis(),
            unlockMode = settings.unlockRelockMode,
            durationMillis = durationMillis,
            expiresAt = if (durationMillis > 0L) System.currentTimeMillis() + durationMillis else Long.MAX_VALUE,
            openCountRemaining = openCount
        )
        temporaryUnlockSessions[packageName] = session
    }

    fun grantTemporaryUnlockExplicit(
        packageName: String,
        mode: UnlockRelockMode,
        timeoutMinutes: Int = 1,
        openCountLimit: Int = 1
    ) {
        val durationMillis = if (mode == UnlockRelockMode.TIMEOUT) timeoutMinutes * 60 * 1000L else 0L
        val session = TemporaryUnlockSession(
            packageName = packageName,
            unlockedAt = System.currentTimeMillis(),
            unlockMode = mode,
            durationMillis = durationMillis,
            expiresAt = if (durationMillis > 0L) System.currentTimeMillis() + durationMillis else Long.MAX_VALUE,
            openCountRemaining = openCountLimit
        )
        temporaryUnlockSessions[packageName] = session
    }

    fun getTemporaryUnlockSession(packageName: String): TemporaryUnlockSession? {
        val session = temporaryUnlockSessions[packageName] ?: return null
        if (session.isExpiredMonotonic()) {
            temporaryUnlockSessions.remove(packageName)
            return null
        }
        return session
    }

    fun isTemporaryUnlockValid(packageName: String): Boolean {
        return getTemporaryUnlockSession(packageName) != null
    }

    fun revokeTemporaryUnlock(packageName: String) {
        temporaryUnlockSessions.remove(packageName)
    }

    /**
     * Handles app exit and entry transitions to accurately enforce UNLOCK_ONCE and OPEN_COUNT.
     */
    fun onForegroundPackageChanged(previousPackage: String, currentPackage: String) {
        val selfPackage = context.packageName
        
        if (previousPackage.isBlank() || currentPackage.isBlank() || previousPackage == currentPackage) {
            return
        }

        // Ignore transient system packages/utilities (autofill, permission popups, keyboards, biometrics, etc.)
        val transientHelperPackages = setOf(
            "com.google.android.gms",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "android",
            "com.android.systemui",
            "com.android.vpndialogs",
            "com.samsung.android.biometrics",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin"
        )
        val isCurrentTransient = transientHelperPackages.contains(currentPackage) || 
                currentPackage.contains("inputmethod") || 
                currentPackage.contains("honeyboard") || 
                currentPackage.contains("biometrics") || 
                currentPackage.contains("credentialmanager") ||
                currentPackage.contains("packageinstaller")
        if (isCurrentTransient) {
            return
        }

        // 1. Instant Relock for App Guard itself upon leaving (strict independence)
        if (previousPackage == selfPackage && !currentPackage.contains("systemui")) {
            temporaryUnlockSessions.remove(selfPackage)
        }

        if (previousPackage.contains("systemui") || currentPackage.contains("systemui")) {
            return
        }

        // 2. Handle app exit from external previousPackage to currentPackage
        val prevSession = temporaryUnlockSessions[previousPackage]
        if (prevSession != null && previousPackage != selfPackage) {
            when (prevSession.unlockMode) {
                UnlockRelockMode.UNLOCK_ONCE -> {
                    temporaryUnlockSessions.remove(previousPackage)
                }
                UnlockRelockMode.OPEN_COUNT -> {
                    prevSession.openCountRemaining -= 1
                    if (prevSession.openCountRemaining <= 0) {
                        temporaryUnlockSessions.remove(previousPackage)
                    }
                }
                UnlockRelockMode.TIMEOUT -> {
                    if (prevSession.isExpiredMonotonic()) {
                        temporaryUnlockSessions.remove(previousPackage)
                    }
                }
                UnlockRelockMode.SCREEN_OFF -> {
                    // SCREEN_OFF sessions are strictly managed by screen-off receivers, so do nothing here
                }
            }
        }
    }

    fun consumeAppOpen(packageName: String) {
        val session = temporaryUnlockSessions[packageName]
        if (session != null) {
            if (session.unlockMode == UnlockRelockMode.OPEN_COUNT) {
                session.openCountRemaining -= 1
            }
            if (session.isExpiredMonotonic()) {
                temporaryUnlockSessions.remove(packageName)
            }
        }
    }

    fun clearAllTemporaryUnlocks() {
        temporaryUnlockSessions.clear()
        globalTemporaryUnlockUntil = 0L
        recentAuthSession = RecentAuthSession()
    }

    // Recent Apps Auth Session
    fun grantRecentAuthSession(durationMinutes: Int = 1) {
        recentAuthSession = RecentAuthSession(
            authenticatedAt = System.currentTimeMillis(),
            durationMillis = durationMinutes.coerceAtLeast(0) * 60 * 1000L,
            authenticatedElapsedAt = android.os.SystemClock.elapsedRealtime()
        )
    }

    fun grantRecentAuthSessionExplicit(durationMillis: Long) {
        recentAuthSession = RecentAuthSession(
            authenticatedAt = System.currentTimeMillis(),
            durationMillis = durationMillis.coerceAtLeast(0L),
            authenticatedElapsedAt = android.os.SystemClock.elapsedRealtime()
        )
    }

    fun clearRecentAuthSession() {
        recentAuthSession = RecentAuthSession()
    }

    fun isRecentAuthSessionValid(): Boolean {
        return recentAuthSession.isValid()
    }

    fun getRecentAuthSession(): RecentAuthSession = recentAuthSession

    fun getTemporaryUnlockSessionsMap(): Map<String, TemporaryUnlockSession> = temporaryUnlockSessions
}
