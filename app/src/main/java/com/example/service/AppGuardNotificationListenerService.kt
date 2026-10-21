package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.example.AppGuardApplication
import com.example.data.model.HiddenNotificationEntity
import com.example.data.model.NotificationHidingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppGuardNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentNotificationsCache = ConcurrentHashMap<String, Long>()

    companion object {
        private const val DEDUPLICATION_WINDOW_MS = 10000L // 10 seconds debounce window
        private const val MAX_CACHE_ENTRIES = 200

        var isRunning: Boolean = false
            private set

        fun isNotificationAccessGranted(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
            return false
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isRunning = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isRunning = false
        recentNotificationsCache.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        recentNotificationsCache.clear()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Skip ongoing, sticky, or non-clearable system/service notifications (e.g. music playback, persistent foreground services, downloads)
        if (sbn.isOngoing || !sbn.isClearable) {
            return
        }
        val flags = sbn.notification.flags
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
            (flags and Notification.FLAG_NO_CLEAR) != 0) {
            return
        }

        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") {
            return // Never hide self or core system notifications
        }

        val app = application as? AppGuardApplication ?: return
        val repository = app.repository
        val settings = repository.settingsFlow.value

        if (!settings.isProtectionEnabled) {
            return
        }

        // Check Notification Hiding Mode
        val mode = settings.notificationHidingMode
        if (mode == NotificationHidingMode.ALWAYS_OFF || !settings.isNotificationHidingEnabled) {
            return
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) 
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim() ?: ""

        // If notification has no meaningful content, skip
        if (title.isBlank() && text.isBlank()) {
            return
        }

        serviceScope.launch {
            // 2. Strict check: If app is NOT protected, exit immediately without interfering
            val isProtected = isAppProtected(pkg, repository)
            if (!isProtected) {
                return@launch
            }

            val shouldHide = shouldHideNotification(pkg, mode, repository)
            if (!shouldHide) {
                return@launch
            }

            // 3. Deduplication Check: avoid duplicate notifications spamming the database
            val existing = repository.hiddenNotificationDao.findNotification(pkg, title, text)
            if (existing != null) {
                // Update timestamp of existing notification record rather than creating a duplicate row
                repository.hiddenNotificationDao.updateNotification(existing.copy(timestamp = sbn.postTime))
                dismissStatusBarNotification(sbn)
                return@launch
            }

            val cacheKey = "$pkg::$title::$text"
            val now = System.currentTimeMillis()
            val lastSeen = recentNotificationsCache[cacheKey]

            if (lastSeen != null && (now - lastSeen) < DEDUPLICATION_WINDOW_MS) {
                dismissStatusBarNotification(sbn)
                recentNotificationsCache[cacheKey] = now
                return@launch
            }

            recentNotificationsCache[cacheKey] = now
            pruneCacheIfNeeded(now)

            // Resolve app name
            val appEntity = repository.protectedAppDao.getApp(pkg)
            val appName = appEntity?.appName ?: try {
                val pm = packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                pkg
            }

            // 4. Save unique notification to Hidden Notifications vault
            val hiddenNotification = HiddenNotificationEntity(
                packageName = pkg,
                appName = appName,
                title = title,
                content = text,
                timestamp = sbn.postTime,
                isRead = false
            )
            repository.hiddenNotificationDao.insertNotification(hiddenNotification)

            // 5. Hide & Cancel notification immediately from status bar
            dismissStatusBarNotification(sbn)
        }
    }

    private fun dismissStatusBarNotification(sbn: StatusBarNotification) {
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {
            try {
                cancelNotification(sbn.packageName, sbn.tag, sbn.id)
            } catch (_: Exception) {}
        }
    }

    private fun pruneCacheIfNeeded(now: Long) {
        if (recentNotificationsCache.size > MAX_CACHE_ENTRIES) {
            val iterator = recentNotificationsCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > 30000L) { // Purge entries older than 30s
                    iterator.remove()
                }
            }
        }
    }

    private suspend fun shouldHideNotification(
        packageName: String,
        mode: NotificationHidingMode,
        repository: com.example.data.repository.ProtectionRepository
    ): Boolean {
        // 1. Check if the app is a protected app
        val isProtected = isAppProtected(packageName, repository)
        if (!isProtected) return false

        // 2. Apply Mode rule
        return when (mode) {
            NotificationHidingMode.ALWAYS_OFF -> false
            NotificationHidingMode.ALWAYS_ACTIVE -> true
            NotificationHidingMode.FOLLOW_RELOCK_STATE -> {
                // Check if currently unlocked by the authorized user
                val session = repository.getTemporaryUnlockSession(packageName)
                val isCurrentlyUnlocked = session != null && !session.isExpired()
                !isCurrentlyUnlocked
            }
        }
    }

    private suspend fun isAppProtected(packageName: String, repository: com.example.data.repository.ProtectionRepository): Boolean {
        // Strict requirement: Unprotected apps are NEVER hidden!
        val appEntity = repository.protectedAppDao.getApp(packageName)
        if (appEntity == null || !appEntity.isLocked) {
            return false
        }

        val settings = repository.settingsFlow.value
        val activeProfileId = settings.activeProfileId
        if (activeProfileId != null) {
            val profileApps = repository.profileDao.getProfileAppsSync(activeProfileId)
            val profileMatch = profileApps.find { it.packageName == packageName }
            if (profileMatch != null) {
                return profileMatch.isLockedInProfile
            }
        }

        return true
    }
}
