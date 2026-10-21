package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.data.model.ProtectionDecision
import com.example.data.model.ScheduleTargetType
import com.example.data.repository.ProtectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class ProtectionDecisionEngine(
    private val context: Context,
    private val repository: ProtectionRepository
) {
    private val systemExemptPackages = mutableSetOf(
        "com.google.android.inputmethod.latin", // Keyboard
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.app.smartcapture",
        "com.sec.android.app.screenshot",
        "com.android.systemui.screenshot",
        "com.google.android.markup",
        "com.samsung.android.biometrics",
        "com.android.vpndialogs",
        "com.samsung.android.spay",
        "com.samsung.android.spayfw",
        "com.samsung.android.samsungpass",
        "com.samsung.android.app.spay",
        "com.samsung.android.rapps",
        "com.samsung.android.spaymini",
        "com.android.incallui",
        "com.google.android.incallui",
        "com.samsung.android.incallui",
        "android"
    )

    private var homeLauncherPackage: String? = null

    init {
        resolveHomeLauncherPackage()
    }

    private fun resolveHomeLauncherPackage() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo?.activityInfo != null) {
                homeLauncherPackage = resolveInfo.activityInfo.packageName
            }
        } catch (_: Exception) {}
    }

    /**
     * Evaluates whether the foreground package should be locked according to the priority rules.
     */
    suspend fun evaluate(packageName: String, calendar: Calendar = Calendar.getInstance()): ProtectionDecision = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) {
            return@withContext ProtectionDecision.Allow("Empty package name")
        }

        val settings = repository.settingsFlow.value

        // 0. System Exempt Packages (System UI, System Settings, Screenshots, Keyboards, System Overlays)
        if (packageName == "com.android.settings") {
            if (settings.isGlobalLockActive) {
                return@withContext ProtectionDecision.Block(
                    "Global Lock (Lock All) Active", packageName, "إعدادات النظام (Settings)"
                )
            }
            val tempSession = repository.getTemporaryUnlockSession(packageName)
            if (tempSession != null && !tempSession.isExpiredMonotonic()) {
                return@withContext ProtectionDecision.Allow("System Settings (Active Session)")
            }

            val appEntity = repository.protectedAppDao.getApp(packageName)
            val activeProfileId = settings.activeProfileId
            val isLockedInActiveProfile = if (activeProfileId != null) {
                repository.profileDao.getProfileAppsSync(activeProfileId)
                    .any { it.packageName == packageName && it.isLockedInProfile }
            } else false
            
            val isLocked = settings.isGlobalLockActive || 
                           settings.isSettingsProtectionEnabled ||
                           (appEntity != null && appEntity.isLocked) || 
                           isLockedInActiveProfile
                           
            if (isLocked) {
                return@withContext ProtectionDecision.Block("حماية إعدادات النظام وتطبيقات الجهاز", packageName, "إعدادات النظام (Settings)")
            } else {
                return@withContext ProtectionDecision.Allow("System Settings (Not Locked by User)")
            }
        } else if (packageName.contains("packageinstaller")) {
            if (settings.isGlobalLockActive) {
                return@withContext ProtectionDecision.Block(
                    "Global Lock (Lock All) Active", packageName, "مدير تثبيت التطبيقات"
                )
            }
            val tempSession = repository.getTemporaryUnlockSession(packageName)
            if (tempSession != null && !tempSession.isExpiredMonotonic()) {
                return@withContext ProtectionDecision.Allow("Package Installer (Active Session)")
            }
            if (settings.isSettingsProtectionEnabled) {
                return@withContext ProtectionDecision.Block("حماية تثبيت وإلغاء تثبيت التطبيقات", packageName, "مدير التثبيت وإلغاء التثبيت")
            }
        } else if (systemExemptPackages.contains(packageName) || packageName == "com.android.systemui") {
            return@withContext ProtectionDecision.Allow("System Exempt Package")
        }

        // 1. SELF_PROTECTION: App Guard Self Protection (Mandatory & Non-Overridable)
        if (packageName == context.packageName) {
            val selfSession = repository.getTemporaryUnlockSession(packageName)
            if (selfSession != null && !selfSession.isExpiredMonotonic()) {
                return@withContext ProtectionDecision.Allow("SELF_PROTECTION: App Guard Session Active")
            }
            return@withContext ProtectionDecision.Block("SELF_PROTECTION: حماية حارس التطبيقات", packageName, "حارس التطبيقات")
        }

        // 2. Launcher and Input Method Exemptions
        if (packageName == homeLauncherPackage) {
            return@withContext ProtectionDecision.Allow("System Launcher Home Screen")
        }
        if (packageName.contains("inputmethod") || packageName.contains("honeyboard")) {
            return@withContext ProtectionDecision.Allow("System Input Method Keyboard")
        }

        // 3. Master Protection Switch
        if (!settings.isProtectionEnabled) {
            return@withContext ProtectionDecision.Allow("Master protection is disabled")
        }

        // Global lock is fail-closed: it overrides existing temporary unlock sessions.
        if (settings.isGlobalLockActive) {
            val lockedName = repository.protectedAppDao.getApp(packageName)?.appName ?: packageName
            return@withContext ProtectionDecision.Block("Global Lock (Lock All) Active", packageName, lockedName)
        }

        // Resolve global schedule conflicts deterministically instead of relying on database row order.
        val activeSchedules = repository.scheduleDao.getEnabledSchedulesSync()
            .filter { ScheduleEngine.isScheduleActive(it, calendar) }
        val globalLockSchedule = activeSchedules.firstOrNull { it.targetType == ScheduleTargetType.GLOBAL_LOCK }
        if (globalLockSchedule != null) {
            val lockedName = repository.protectedAppDao.getApp(packageName)?.appName ?: packageName
            return@withContext ProtectionDecision.Block(
                "Active Global Lock Schedule: ${globalLockSchedule.name}", packageName, lockedName
            )
        }
        val globalUnlockSchedule = activeSchedules.firstOrNull { it.targetType == ScheduleTargetType.GLOBAL_UNLOCK }
        if (globalUnlockSchedule != null) {
            return@withContext ProtectionDecision.Allow("Active Global Unlock Schedule: ${globalUnlockSchedule.name}")
        }

        // 4. Temporary Unlock Session Check for this package. Global lock rules above take priority.
        val tempSession = repository.getTemporaryUnlockSession(packageName)
        if (tempSession != null && !tempSession.isExpiredMonotonic()) {
            return@withContext ProtectionDecision.Allow("Active Temporary Unlock Session")
        }

        val appEntity = repository.protectedAppDao.getApp(packageName)
        val appName = appEntity?.appName ?: packageName

        // 5. Scheduled profile rules. Global lock/unlock conflicts were resolved above.
        for (schedule in activeSchedules) {
            if (schedule.targetType == ScheduleTargetType.ACTIVATE_PROFILE) {
                val profileId = schedule.targetProfileId
                if (profileId != null) {
                    val profileApps = repository.profileDao.getProfileAppsSync(profileId)
                    val isAppLockedInProfile = profileApps.any {
                        it.packageName == packageName && it.isLockedInProfile
                    }
                    if (isAppLockedInProfile) {
                        return@withContext ProtectionDecision.Block(
                            "Scheduled Active Profile: ${schedule.name}", packageName, appName
                        )
                    }
                }
            }
        }

        // 7. Active Profile Check (Manual Active Profile)
        val activeProfileId = settings.activeProfileId
        if (activeProfileId != null) {
            val profileApps = repository.profileDao.getProfileAppsSync(activeProfileId)
            val profileAppMatch = profileApps.find { it.packageName == packageName }
            if (profileAppMatch != null) {
                if (profileAppMatch.isLockedInProfile) {
                    return@withContext ProtectionDecision.Block("Active Profile Policy", packageName, appName)
                } else {
                    return@withContext ProtectionDecision.Allow("Unlocked in Active Profile")
                }
            }
        }

        // 8. Default App Protection Check (from app list)
        if (appEntity != null && appEntity.isLocked) {
            return@withContext ProtectionDecision.Block("App is Protected in Master List", packageName, appName)
        }

        return@withContext ProtectionDecision.Allow("App is not protected")
    }
}
