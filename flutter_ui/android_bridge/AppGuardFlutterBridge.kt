// Integration template for the Flutter Android host. Keep this source outside
// app/src/main until Flutter Gradle embedding is configured, so the existing
// native Android build remains intact during the staged migration.
package com.example.flutterbridge

import android.content.Context
import com.example.AppGuardApplication
import com.example.data.model.ProtectedAppEntity
import com.example.data.repository.ProtectionRepository
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppGuardFlutterBridge(
    context: Context,
    flutterEngine: FlutterEngine
) : MethodChannel.MethodCallHandler {
    private val appContext = context.applicationContext
    private val repository: ProtectionRepository =
        (appContext as AppGuardApplication).repository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val channel = MethodChannel(
        flutterEngine.dartExecutor.binaryMessenger,
        CHANNEL_NAME
    )

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "isProtectionBridgeReady" -> result.success(true)
            "getDashboardStatus" -> {
                val settings = repository.settingsFlow.value
                result.success(mapOf(
                    "protectionEnabled" to settings.isProtectionEnabled,
                    "globalLockActive" to settings.isGlobalLockActive,
                    "recentAppsLockEnabled" to settings.isRecentAppsLockEnabled,
                    "recentAppsAlwaysLocked" to settings.isRecentAppsAlwaysLocked
                ))
            }
            "getInstalledApps" -> runAsync(result) {
                repository.protectedAppDao.getAllApps().first().map(::appMap)
            }
            "getProfiles" -> runAsync(result) {
                repository.profileDao.getAllProfiles().first().map { profile ->
                    mapOf(
                        "id" to profile.id,
                        "name" to profile.name,
                        "icon" to profile.icon,
                        "isActive" to profile.isActive,
                        "isPreset" to profile.isPreset
                    )
                }
            }
            "activateProfile" -> {
                val profileId = call.argument<Number>("profileId")?.toLong()
                runAsync(result) {
                    if (profileId != null && repository.profileDao.getProfileById(profileId) == null) {
                        false
                    } else {
                        repository.activateProfile(profileId)
                        true
                    }
                }
            }
            "getSchedules" -> runAsync(result) {
                repository.scheduleDao.getAllSchedules().first().map { schedule ->
                    mapOf(
                        "id" to schedule.id,
                        "name" to schedule.name,
                        "targetType" to schedule.targetType.name,
                        "targetProfileId" to schedule.targetProfileId,
                        "startHour" to schedule.startHour,
                        "startMinute" to schedule.startMinute,
                        "endHour" to schedule.endHour,
                        "endMinute" to schedule.endMinute,
                        "daysOfWeek" to schedule.daysOfWeek,
                        "isEnabled" to schedule.isEnabled
                    )
                }
            }
            "setScheduleEnabled" -> {
                val id = call.argument<Number>("id")?.toLong()
                val enabled = call.argument<Boolean>("enabled")
                if (id == null || enabled == null || id <= 0L) {
                    result.error("INVALID_ARGUMENT", "A valid id and enabled value are required", null)
                } else runAsync(result) {
                    val exists = repository.scheduleDao.getAllSchedules().first().any { it.id == id }
                    if (!exists) false else {
                        repository.scheduleDao.setScheduleEnabled(id, enabled)
                        true
                    }
                }
            }
            "setAppLocked" -> {
                val packageName = call.argument<String>("packageName")
                val locked = call.argument<Boolean>("locked")
                if (packageName.isNullOrBlank() || locked == null) {
                    result.error("INVALID_ARGUMENT", "packageName and locked are required", null)
                } else {
                    runAsync(result) {
                        val exists = repository.protectedAppDao.getApp(packageName) != null
                        if (!exists) false else {
                            repository.setAppLocked(packageName, locked)
                            true
                        }
                    }
                }
            }
            "setProtectionEnabled" -> {
                val enabled = call.argument<Boolean>("enabled")
                if (enabled == null) {
                    result.error("INVALID_ARGUMENT", "enabled is required", null)
                } else {
                    repository.updateSettings { it.copy(isProtectionEnabled = enabled) }
                    result.success(true)
                }
            }
            "setRecentAppsLockEnabled" -> {
                val enabled = call.argument<Boolean>("enabled")
                if (enabled == null) {
                    result.error("INVALID_ARGUMENT", "enabled is required", null)
                } else {
                    repository.updateSettings { it.copy(isRecentAppsLockEnabled = enabled) }
                    result.success(true)
                }
            }
            "setRecentAppsAlwaysLocked" -> {
                val enabled = call.argument<Boolean>("enabled")
                if (enabled == null) {
                    result.error("INVALID_ARGUMENT", "enabled is required", null)
                } else {
                    repository.updateSettings { it.copy(isRecentAppsAlwaysLocked = enabled) }
                    result.success(true)
                }
            }
            "refreshInstalledApps" -> runAsync(result) {
                repository.refreshInstalledApps()
                true
            }
            else -> result.notImplemented()
        }
    }

    private fun appMap(app: ProtectedAppEntity): Map<String, Any> = mapOf(
        "packageName" to app.packageName,
        "appName" to app.appName,
        "isLocked" to app.isLocked,
        "isSystemApp" to app.isSystemApp
    )

    private fun runAsync(result: MethodChannel.Result, block: suspend () -> Any?) {
        scope.launch {
            try {
                val value = withContext(Dispatchers.IO) { block() }
                result.success(value)
            } catch (error: Exception) {
                result.error("NATIVE_OPERATION_FAILED", error.message ?: "Operation failed", null)
            }
        }
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
        scope.cancel()
    }

    companion object {
        const val CHANNEL_NAME = "com.example.app_guard/protection"
    }
}
