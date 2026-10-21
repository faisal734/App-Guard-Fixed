package com.example.engine

import com.example.data.repository.ProtectionRepository

class RecentLockEngine(private val repository: ProtectionRepository) {

    /**
     * Known Android Overview/Recents surface signatures. Match only system UI or known
     * launcher packages to avoid false positives from third-party activities with similar names.
     *
     * Kept as a pure function so OEM signatures can be tested without a live Android service.
     */
    companion object {
        private val recentsClassKeywords = listOf(
            "recents",
            "quickstep",
            "overview",
            "taskview",
            "recenttasks",
            "recentstogglebox",
            "appswitch",
            "taskstack",
            "recentsview",
            "overviewstate",
            "recentscontainer",
            "recentapps",
            "recent_app",
            "recentappsactivity",
            "recentactivity",
            "tasksoverview",
            "overviewpanel",
            "recentstask"
        )

        private val recentsPackageNames = setOf(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.mi.android.globallauncher",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.huawei.android.launcher",
            "com.transsion.launcher",
            "com.android.launcher3",
            "com.motorola.launcher3",
            "com.oneplus.launcher",
            "com.nothing.launcher",
            "com.vivo.launcher"
        )

        fun isKnownRecentsSurface(packageName: String, className: String?): Boolean {
            val pkg = packageName.trim().lowercase()
            val cls = (className ?: "").trim().lowercase()
            if (pkg !in recentsPackageNames || cls.isBlank()) return false

            // Do not classify every System UI window as Recents; only recognizable overview classes.
            return recentsClassKeywords.any { cls.contains(it) } ||
                cls.endsWith("recentsactivity") ||
                cls.endsWith("fallbackrecentsactivity")
        }
    }

    fun isRecentsOverview(packageName: String, className: String?): Boolean {
        return repository.settingsFlow.value.isRecentAppsLockEnabled &&
            isKnownRecentsSurface(packageName, className)
    }

    fun shouldBlockRecents(): Boolean {
        val settings = repository.settingsFlow.value
        if (!settings.isRecentAppsLockEnabled) return false
        return !repository.isRecentAuthSessionValid()
    }
}

