package com.example.data.model

import android.os.SystemClock
 
/**
 * Unlock and Relock Modes (mutually exclusive single choice).
 */
enum class UnlockRelockMode {
    UNLOCK_ONCE,
    TIMEOUT,
    OPEN_COUNT,
    SCREEN_OFF
}

/**
 * Keyboard lock behavior modes.
 */
enum class KeyboardLockBehavior {
    FOLLOW_RELOCK,
    ALWAYS_UNLOCKED
}

/**
 * Notification hiding behavior modes.
 */
enum class NotificationHidingMode {
    FOLLOW_RELOCK_STATE, // 1- يتبع خيارات إعادة قفل الشاشة
    ALWAYS_ACTIVE,       // 2- مفعل دائماً
    ALWAYS_OFF           // 3- مقفل دائماً
}

/**
 * Global App Guard Configuration Settings.
 */
data class AppGuardSettings(
    val isProtectionEnabled: Boolean = true,
    val isGlobalLockActive: Boolean = false,
    val isBiometricEnabled: Boolean = true,
    val isShufflePinEnabled: Boolean = false,
    val isProtectSelfEnabled: Boolean = false,
    val isSettingsProtectionEnabled: Boolean = true,
    val isNotificationHidingEnabled: Boolean = true,
    val notificationHidingMode: NotificationHidingMode = NotificationHidingMode.FOLLOW_RELOCK_STATE,
    val unlockRelockMode: UnlockRelockMode = UnlockRelockMode.SCREEN_OFF,
    val timeoutMinutes: Int = 1, // 1, 5, 15, 30, 60
    val openCountLimit: Int = 1, // 1, 2, 3, 5, 10
    val isRecentAppsLockEnabled: Boolean = true,
    val isRecentAppsAlwaysLocked: Boolean = false,
    val isAutoLockNewAppsEnabled: Boolean = false,
    val isCrashScreenEnabled: Boolean = false,
    val isIntruderDetectionEnabled: Boolean = false,
    val intruderFailedAttemptsThreshold: Int = 3,
    val activeProfileId: Long? = null,
    val isDarkTheme: Boolean = true,
    val language: String = "ar", // "ar" or "en"
    val keyboardLockBehavior: KeyboardLockBehavior = KeyboardLockBehavior.ALWAYS_UNLOCKED
)

/**
 * Temporary Unlock Session state for a package.
 */
data class TemporaryUnlockSession(
    val packageName: String,
    val unlockedAt: Long = System.currentTimeMillis(),
    val unlockMode: UnlockRelockMode = UnlockRelockMode.UNLOCK_ONCE,
    val durationMillis: Long = 0L,
    val expiresAt: Long = if (durationMillis > 0L) unlockedAt + durationMillis else Long.MAX_VALUE,
    var openCountRemaining: Int = 1,
    /** Monotonic timestamps prevent wall-clock changes from extending an unlock session. */
    val unlockedElapsedAt: Long = SystemClock.elapsedRealtime(),
    val expiresElapsedAt: Long = if (durationMillis > 0L) unlockedElapsedAt + durationMillis else Long.MAX_VALUE
) {
    /** Legacy wall-clock evaluator retained for deterministic unit tests and compatibility. */
    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (unlockMode == UnlockRelockMode.OPEN_COUNT && openCountRemaining <= 0) return true
        if (durationMillis > 0L && currentTime - unlockedAt >= durationMillis) return true
        return when (unlockMode) {
            UnlockRelockMode.UNLOCK_ONCE -> false // Managed strictly by app exit transition
            UnlockRelockMode.TIMEOUT -> durationMillis > 0L && currentTime >= expiresAt
            UnlockRelockMode.OPEN_COUNT -> openCountRemaining <= 0
            UnlockRelockMode.SCREEN_OFF -> false // Managed strictly by screen-off events
        }
    }

    /** Production expiry check uses monotonic elapsed time, immune to wall-clock edits. */
    fun isExpiredMonotonic(): Boolean {
        if (unlockMode == UnlockRelockMode.OPEN_COUNT && openCountRemaining <= 0) return true
        return when (unlockMode) {
            UnlockRelockMode.UNLOCK_ONCE -> false
            UnlockRelockMode.TIMEOUT -> durationMillis > 0L && SystemClock.elapsedRealtime() >= expiresElapsedAt
            UnlockRelockMode.OPEN_COUNT -> openCountRemaining <= 0
            UnlockRelockMode.SCREEN_OFF -> false
        }
    }
}

/**
 * Recent Apps Authentication Session.
 */
data class RecentAuthSession(
    /** Wall-clock timestamp retained for compatibility with existing stored/test records. */
    val authenticatedAt: Long = 0L,
    val durationMillis: Long = 60_000L, // 1 minute grace period for Recent overview navigation
    /** Set by production grant paths; monotonic time cannot be extended by changing device time. */
    val authenticatedElapsedAt: Long = 0L
) {
    fun isValid(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (authenticatedElapsedAt > 0L) {
            return isValidMonotonic(SystemClock.elapsedRealtime())
        }
        // Legacy/test-created sessions use wall time. Reject negative deltas to avoid treating
        // a clock rollback as an indefinitely valid session.
        val delta = currentTime - authenticatedAt
        return authenticatedAt > 0L && delta >= 0L && delta <= durationMillis
    }

    /** Deterministic helper for unit tests and monotonic production checks. */
    fun isValidMonotonic(currentElapsedTime: Long): Boolean {
        val delta = currentElapsedTime - authenticatedElapsedAt
        return authenticatedElapsedAt > 0L && delta >= 0L && delta <= durationMillis
    }
}

/**
 * Protection decision result returned by ProtectionDecisionEngine.
 */
sealed class ProtectionDecision {
    data class Allow(val reason: String) : ProtectionDecision()
    data class Block(val reason: String, val targetPackage: String, val appName: String) : ProtectionDecision()
}

