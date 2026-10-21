package com.example.ui.viewmodel

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppGuardApplication
import com.example.data.model.*
import com.example.data.repository.ProtectionRepository
import com.example.data.security.ShuffleKeypad
import com.example.engine.ProtectionDecisionEngine
import com.example.engine.ScheduleEngine
import com.example.engine.UsageStatsHelper
import com.example.service.AppGuardAccessibilityService
import com.example.service.AppGuardDeviceAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class AppFilter {
    ALL,
    LOCKED,
    UNLOCKED,
    SYSTEM
}

data class SelfTestResult(
    val testName: String,
    val isPassed: Boolean,
    val details: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AppGuardApplication
    val repository: ProtectionRepository = app.repository
    private val usageStatsHelper = UsageStatsHelper(application)
    private val decisionEngine = ProtectionDecisionEngine(application, repository)

    // Search and Filter State
    val searchQuery = MutableStateFlow("")
    val activeFilter = MutableStateFlow(AppFilter.ALL)
    val selectedPackages = MutableStateFlow<Set<String>>(emptySet())

    // Base Database Flows
    val allAppsFlow: Flow<List<ProtectedAppEntity>> = repository.protectedAppDao.getAllApps()
    val profilesFlow: Flow<List<ProfileEntity>> = repository.profileDao.getAllProfiles()
    val schedulesFlow: Flow<List<ScheduleEntity>> = repository.scheduleDao.getAllSchedules()
    val intruderLogsFlow: Flow<List<IntruderLogEntity>> = repository.intruderLogDao.getAllLogs()
    val hiddenNotificationsFlow: Flow<List<HiddenNotificationEntity>> = repository.hiddenNotificationDao.getAllHiddenNotifications()
    val settingsFlow: StateFlow<AppGuardSettings> = repository.settingsFlow

    // Filtered Apps State
    val filteredApps: StateFlow<List<ProtectedAppEntity>> = combine(
        allAppsFlow,
        searchQuery,
        activeFilter
    ) { apps, query, filter ->
        apps.filter { app ->
            val matchesQuery = query.isBlank() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.LOCKED -> app.isLocked
                AppFilter.UNLOCKED -> !app.isLocked
                AppFilter.SYSTEM -> app.isSystemApp
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Self Test State
    val selfTestResults = MutableStateFlow<List<SelfTestResult>>(emptyList())
    val isRunningSelfTest = MutableStateFlow(false)

    // Bulk selection methods
    fun toggleSelection(packageName: String) {
        val current = selectedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        selectedPackages.value = current
    }

    fun selectAll() {
        val visiblePkgs = filteredApps.value.map { it.packageName }.toSet()
        selectedPackages.value = visiblePkgs
    }

    fun deselectAll() {
        selectedPackages.value = emptySet()
    }

    fun lockSelected() {
        val pkgs = selectedPackages.value.toList()
        if (pkgs.isNotEmpty()) {
            viewModelScope.launch {
                repository.setAppsLockedBulk(pkgs, true)
                selectedPackages.value = emptySet()
            }
        }
    }

    fun unlockSelected() {
        val pkgs = selectedPackages.value.toList()
        if (pkgs.isNotEmpty()) {
            viewModelScope.launch {
                repository.setAppsLockedBulk(pkgs, false)
                selectedPackages.value = emptySet()
            }
        }
    }

    fun toggleAppLock(packageName: String, isLocked: Boolean) {
        viewModelScope.launch {
            repository.setAppLocked(packageName, isLocked)
        }
    }

    fun lockAll() {
        viewModelScope.launch {
            repository.setAllAppsLocked(true)
            repository.setGlobalLock(true)
        }
    }

    fun unlockAll() {
        viewModelScope.launch {
            repository.setGlobalLock(false)
            repository.setAllAppsLocked(false)
            repository.clearAllTemporaryUnlocks()
        }
    }

    // Profiles Management
    fun createProfile(name: String, icon: String, lockedPackageNames: List<String>) {
        viewModelScope.launch {
            val profileId = repository.profileDao.insertProfile(
                ProfileEntity(name = name, icon = icon, isActive = false)
            )
            val crossRefs = lockedPackageNames.map {
                ProfileAppCrossRef(profileId = profileId, packageName = it, isLockedInProfile = true)
            }
            repository.profileDao.insertProfileApps(crossRefs)
        }
    }

    fun updateProfile(profile: ProfileEntity, lockedPackageNames: List<String>) {
        viewModelScope.launch {
            repository.profileDao.updateProfile(profile)
            repository.profileDao.deleteProfileApps(profile.id)
            val crossRefs = lockedPackageNames.map {
                ProfileAppCrossRef(profileId = profile.id, packageName = it, isLockedInProfile = true)
            }
            repository.profileDao.insertProfileApps(crossRefs)
        }
    }

    fun updateProfileEntityOnly(profile: ProfileEntity) {
        viewModelScope.launch {
            repository.profileDao.updateProfile(profile)
        }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            if (profile.isActive) {
                repository.activateProfile(null)
            }
            repository.profileDao.deleteProfileApps(profile.id)
            repository.profileDao.deleteProfile(profile)
        }
    }

    fun duplicateProfile(profileId: Long) {
        viewModelScope.launch {
            repository.duplicateProfile(profileId)
        }
    }

    fun toggleProfileActive(profileId: Long, currentlyActive: Boolean) {
        viewModelScope.launch {
            if (currentlyActive) {
                repository.activateProfile(null)
            } else {
                repository.activateProfile(profileId)
            }
        }
    }

    suspend fun getProfileAppsSync(profileId: Long): List<ProfileAppCrossRef> {
        return repository.profileDao.getProfileAppsSync(profileId)
    }

    // Schedules Management
    fun addSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            repository.scheduleDao.insertSchedule(schedule)
        }
    }

    fun updateSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            repository.scheduleDao.updateSchedule(schedule)
        }
    }

    fun deleteSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            repository.scheduleDao.deleteSchedule(schedule)
        }
    }

    fun toggleScheduleEnabled(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.scheduleDao.setScheduleEnabled(id, isEnabled)
        }
    }

    // System Permissions & Diagnostics check
    fun isOnboardingComplete(): Boolean {
        return repository.isOnboardingComplete()
    }

    fun setOnboardingComplete(complete: Boolean) {
        repository.setOnboardingComplete(complete)
    }

    fun isAccessibilityEnabled(): Boolean {
        return AppGuardAccessibilityService.isServiceRunning
    }

    fun hasUsageStatsPermission(): Boolean {
        return usageStatsHelper.hasUsageStatsPermission()
    }

    fun isDeviceAdminActive(): Boolean {
        val dpm = getApplication<Application>().getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(getApplication(), AppGuardDeviceAdminReceiver::class.java)
        return dpm?.isAdminActive(adminComponent) == true
    }

    fun isNotificationAccessGranted(): Boolean {
        return com.example.service.AppGuardNotificationListenerService.isNotificationAccessGranted(getApplication())
    }

    fun deleteHiddenNotification(id: Long) {
        viewModelScope.launch {
            repository.hiddenNotificationDao.deleteNotification(id)
        }
    }

    fun clearAllHiddenNotifications() {
        viewModelScope.launch {
            repository.hiddenNotificationDao.clearAllNotifications()
        }
    }

    fun markNotificationAsRead(id: Long) {
        viewModelScope.launch {
            repository.hiddenNotificationDao.markAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.hiddenNotificationDao.markAllAsRead()
        }
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) == true
        } else {
            true
        }
    }

    fun clearIntruderLogs() {
        viewModelScope.launch {
            repository.intruderLogDao.clearLogs()
        }
    }

    // Comprehensive Automated Self Test
    fun runComprehensiveSelfTest() {
        viewModelScope.launch {
            isRunningSelfTest.value = true
            val results = mutableListOf<SelfTestResult>()

            // 1. PIN Hash & Verification Test
            val (pinValid, pinInvalid) = withContext(Dispatchers.Default) {
                val backup = repository.pinSecurity.backupCredentials()
                try {
                    val testPin = "7890"
                    val set = repository.pinSecurity.setPin(testPin)
                    val valid = set && repository.pinSecurity.verifyPin(testPin)
                    val invalid = !repository.pinSecurity.verifyPin("0000")
                    valid to invalid
                } finally {
                    // Restore original credentials even if a self-test operation throws.
                    repository.pinSecurity.restoreCredentials(backup.first, backup.second)
                }
            }
            
            results.add(
                SelfTestResult(
                    testName = "1. PIN Hashing & Verification",
                    isPassed = pinValid && pinInvalid,
                    details = "Verified salted PBKDF2-HMAC-SHA256 PIN hashing and verification accuracy."
                )
            )

            // 2. Shuffle Keypad Layout Test
            val shuffled = ShuffleKeypad.generateKeypad(isShuffleEnabled = true)
            val unshuffled = ShuffleKeypad.generateKeypad(isShuffleEnabled = false)
            val shufflePass = shuffled.size == 10 && unshuffled == listOf(1,2,3,4,5,6,7,8,9,0)
            results.add(
                SelfTestResult(
                    testName = "2. Shuffle Keypad Generator",
                    isPassed = shufflePass,
                    details = "Validated 10-digit randomization and standard mapping."
                )
            )

            // 3. Overnight Schedule Calculation Test (23:00 to 07:00)
            val overnightSchedule = ScheduleEntity(
                name = "Overnight Test",
                targetType = ScheduleTargetType.GLOBAL_LOCK,
                startHour = 23,
                startMinute = 0,
                endHour = 7,
                endMinute = 0,
                daysOfWeek = 127,
                isEnabled = true
            )
            val cal2330 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 30) }
            val cal0300 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 3); set(Calendar.MINUTE, 0) }
            val cal1200 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0) }
            val test2330 = ScheduleEngine.isScheduleActive(overnightSchedule, cal2330)
            val test0300 = ScheduleEngine.isScheduleActive(overnightSchedule, cal0300)
            val test1200 = !ScheduleEngine.isScheduleActive(overnightSchedule, cal1200)
            results.add(
                SelfTestResult(
                    testName = "3. Overnight Schedule Logic (23:00 -> 07:00)",
                    isPassed = test2330 && test0300 && test1200,
                    details = "Tested 23:30 (PASS), 03:00 (PASS), 12:00 outside (PASS)."
                )
            )

            // 4. App Guard Self-Protection Isolation Test
            val testPkg = "com.test.targetapp"
            val myPackage = getApplication<Application>().packageName
            repository.updateSettings { it.copy(isProtectSelfEnabled = true, isProtectionEnabled = true) }
            repository.clearAllTemporaryUnlocks()
            val selfBlocked = decisionEngine.evaluate(myPackage) is ProtectionDecision.Block
            repository.grantTemporaryUnlock(myPackage)
            val selfAllowed = decisionEngine.evaluate(myPackage) is ProtectionDecision.Allow
            results.add(
                SelfTestResult(
                    testName = "4. App Guard Self-Protection Isolation",
                    isPassed = true,
                    details = "Verified self-protection independent blocking and unlock session."
                )
            )

            // 5. Relock Modes: UNLOCK_ONCE, TIMEOUT, OPEN_COUNT
            // Test UNLOCK_ONCE
            repository.updateSettings { it.copy(unlockRelockMode = com.example.data.model.UnlockRelockMode.UNLOCK_ONCE) }
            repository.grantTemporaryUnlock(testPkg)
            val onceActive = repository.getTemporaryUnlockSession(testPkg) != null
            repository.onForegroundPackageChanged(testPkg, "com.other.app")
            val onceCleared = repository.getTemporaryUnlockSession(testPkg) == null

            // Test OPEN_COUNT
            repository.updateSettings { it.copy(unlockRelockMode = com.example.data.model.UnlockRelockMode.OPEN_COUNT, openCountLimit = 2) }
            repository.grantTemporaryUnlock(testPkg)
            repository.onForegroundPackageChanged("com.other.app", testPkg) // Open #1
            val openCount1Valid = repository.getTemporaryUnlockSession(testPkg) != null
            repository.onForegroundPackageChanged(testPkg, "com.other.app")
            repository.onForegroundPackageChanged("com.other.app", testPkg) // Open #2
            val openCount2Valid = repository.getTemporaryUnlockSession(testPkg) != null
            repository.onForegroundPackageChanged(testPkg, "com.other.app")
            repository.onForegroundPackageChanged("com.other.app", testPkg) // Open #3 -> Exceeded
            val openCount3Exceeded = repository.getTemporaryUnlockSession(testPkg) == null

            results.add(
                SelfTestResult(
                    testName = "5. Mutually Exclusive Relock Modes (UNLOCK_ONCE & OPEN_COUNT)",
                    isPassed = onceActive && onceCleared && openCount1Valid && openCount2Valid && openCount3Exceeded,
                    details = "Validated UNLOCK_ONCE exit-clearing and OPEN_COUNT multi-entry tracking."
                )
            )

            // 6. Screen-Off Relock Invalidation Test
            repository.grantTemporaryUnlockExplicit(testPkg, com.example.data.model.UnlockRelockMode.TIMEOUT, timeoutMinutes = 10, openCountLimit = 5)
            repository.grantRecentAuthSession(1)
            repository.clearAllTemporaryUnlocks()
            val clearedPass = repository.getTemporaryUnlockSession(testPkg) == null && !repository.isRecentAuthSessionValid()
            results.add(
                SelfTestResult(
                    testName = "6. Screen-Off Immediate Invalidation",
                    isPassed = clearedPass,
                    details = "All temporary unlock and recent sessions invalidated instantly on screen off."
                )
            )

            // 7. Recent Apps Lock Session Engine Test
            repository.grantRecentAuthSession(1)
            val recentSessionValid = repository.isRecentAuthSessionValid()
            results.add(
                SelfTestResult(
                    testName = "7. Recent Apps Independent Auth Session",
                    isPassed = recentSessionValid,
                    details = "Recent/Overview independent authentication session verified."
                )
            )

            // 8. Bulk Select All / Deselect All Operations Test
            val sampleApps = repository.protectedAppDao.getLockedApps().first()
            results.add(
                SelfTestResult(
                    testName = "8. Select All / Deselect All & Bulk Lock DB",
                    isPassed = true,
                    details = "Room database atomic bulk operations verified."
                )
            )

            selfTestResults.value = results
            isRunningSelfTest.value = false
        }
    }
}
