package com.example.service

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.example.AppGuardApplication
import com.example.data.model.ProtectedAppEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Screen State Broadcast Receiver.
 * Relocks apps on ACTION_SCREEN_OFF when Screen-Off Relock is enabled.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            val app = context.applicationContext as? AppGuardApplication ?: return
            val settings = app.repository.settingsFlow.value
            if (settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.SCREEN_OFF) {
                app.repository.clearAllTemporaryUnlocks()
            }
        }
    }
}

/**
 * Newly Installed App Receiver.
 * Adds new apps to protected apps if Auto-Lock Newly Installed Apps is enabled.
 */
class PackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            val app = context.applicationContext as? AppGuardApplication ?: return
            val pendingResult = goAsync()

            app.applicationScope.launch(Dispatchers.IO) {
                try {
                    val settings = app.repository.settingsFlow.value
                    val pm = context.packageManager
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        val shouldLock = settings.isAutoLockNewAppsEnabled
                        val entity = ProtectedAppEntity(
                            packageName = packageName,
                            appName = appName,
                            isLocked = shouldLock,
                            isSystemApp = isSystem
                        )
                        app.repository.protectedAppDao.insertOrUpdate(entity)
                    } catch (_: Exception) {}
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

/**
 * Boot Completed Receiver.
 * Restores services and schedules upon reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val app = context.applicationContext as? AppGuardApplication ?: return
            val pendingResult = goAsync()
            app.applicationScope.launch(Dispatchers.IO) {
                try {
                    app.repository.refreshInstalledApps()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

/**
 * Official Device Administrator Receiver for Anti-Uninstall defense.
 */
class AppGuardDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "تحذير: إلغاء صلاحية مسؤول الجهاز سيوقف ميزة الحماية من إزالة تثبيت حارس التطبيقات."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
