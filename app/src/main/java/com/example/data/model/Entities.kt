package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an installed application and its protection status in App Guard.
 */
@Entity(tableName = "protected_apps")
data class ProtectedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean = false,
    val isSystemApp: Boolean = false,
    val openCount: Int = 0,
    val customTimeoutMinutes: Int? = null,
    val lastUnlockedTimestamp: Long = 0L
)

/**
 * Security Profile (e.g. Home, Work, Study, Sleep, Gaming).
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "🛡",
    val isActive: Boolean = false,
    val isPreset: Boolean = false
)

/**
 * Mapping between Profile and Protected Apps.
 */
@Entity(tableName = "profile_apps", primaryKeys = ["profileId", "packageName"])
data class ProfileAppCrossRef(
    val profileId: Long,
    val packageName: String,
    val isLockedInProfile: Boolean = true
)

/**
 * Schedule target types.
 */
enum class ScheduleTargetType {
    GLOBAL_LOCK,
    GLOBAL_UNLOCK,
    ACTIVATE_PROFILE,
    DEACTIVATE_PROFILE
}

/**
 * Automated Schedule Entity.
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetType: ScheduleTargetType,
    val targetProfileId: Long? = null,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: Int = 127, // 7-bit mask: Sun(1) to Sat(64), 127 = Every day
    val isEnabled: Boolean = true
)

/**
 * Intruder attempt log.
 */
@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val targetPackage: String,
    val targetAppName: String,
    val attemptCount: Int,
    val photoPath: String? = null
)

/**
 * Intercepted & Hidden Notification Entity for protected applications.
 */
@Entity(tableName = "hidden_notifications")
data class HiddenNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

