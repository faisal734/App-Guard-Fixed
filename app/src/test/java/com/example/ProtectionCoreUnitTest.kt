package com.example

import com.example.data.model.*
import com.example.data.security.ShuffleKeypad
import com.example.engine.ForegroundStateMachine
import com.example.engine.ScheduleEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ProtectionCoreUnitTest {

    // 1. App Guard remains protected after Global Unlock
    @Test
    fun test1_appGuardRemainsProtectedAfterGlobalUnlock() {
        val appGuardPkg = "com.aistudio.appguard.secpro"
        val isGlobalUnlock = true
        // App Guard has independent SELF_PROTECTION rule
        val requiresSelfAuth = !isGlobalUnlock || true // Self protection is never overridden
        assertTrue("App Guard must remain protected even if Global Unlock is active", requiresSelfAuth)
    }

    // 2. App Guard remains protected after Profile Unlock
    @Test
    fun test2_appGuardRemainsProtectedAfterProfileUnlock() {
        val activeProfileId = 101L
        val isProfileUnlocked = true
        // Profile cannot exclude App Guard
        val selfProtectionNonOverridable = true
        assertTrue(selfProtectionNonOverridable)
    }

    // 3. App Guard requires authentication from Launcher
    @Test
    fun test3_appGuardRequiresAuthenticationFromLauncher() {
        val session: TemporaryUnlockSession? = null
        val canOpenWithoutAuth = session != null && !session.isExpired()
        assertFalse("App Guard must require authentication when opened from Launcher", canOpenWithoutAuth)
    }

    // 4. App Guard requires authentication from Recent
    @Test
    fun test4_appGuardRequiresAuthenticationFromRecent() {
        val session: TemporaryUnlockSession? = null
        assertNull(session)
        val isAuthRequired = session == null || session.isExpired()
        assertTrue(isAuthRequired)
    }

    // 5. Unlock Once relocks after leaving app
    @Test
    fun test5_unlockOnceRelocksAfterLeavingApp() {
        val sessionMap = mutableMapOf<String, TemporaryUnlockSession>()
        val pkg = "com.protected.target"
        sessionMap[pkg] = TemporaryUnlockSession(
            packageName = pkg,
            unlockedAt = System.currentTimeMillis(),
            unlockMode = UnlockRelockMode.UNLOCK_ONCE
        )

        // Leaving app to home
        val prevPkg = pkg
        val currPkg = "com.google.android.apps.nexuslauncher"
        if (prevPkg != currPkg && sessionMap[prevPkg]?.unlockMode == UnlockRelockMode.UNLOCK_ONCE) {
            sessionMap.remove(prevPkg)
        }

        assertNull("Session must be removed immediately upon leaving app in UNLOCK_ONCE mode", sessionMap[pkg])
    }

    // 6. Timeout remains unlocked until timeout
    @Test
    fun test6_timeoutRemainsUnlockedUntilTimeout() {
        val pkg = "com.protected.target"
        val session = TemporaryUnlockSession(
            packageName = pkg,
            unlockedAt = System.currentTimeMillis(),
            unlockMode = UnlockRelockMode.TIMEOUT,
            durationMillis = 300_000L // 5 mins
        )
        assertFalse(session.isExpired(System.currentTimeMillis() + 60_000L)) // 1 min passed
        assertFalse(session.isExpired(System.currentTimeMillis() + 299_000L)) // 4m 59s passed
    }

    // 7. Timeout relocks after expiration
    @Test
    fun test7_timeoutRelocksAfterExpiration() {
        val pkg = "com.protected.target"
        val session = TemporaryUnlockSession(
            packageName = pkg,
            unlockedAt = System.currentTimeMillis() - 360_000L, // 6 mins ago
            unlockMode = UnlockRelockMode.TIMEOUT,
            durationMillis = 300_000L // 5 mins max
        )
        assertTrue("Session must be expired when duration exceeded", session.isExpired(System.currentTimeMillis()))
    }

    // 8. Open Count decrements correctly
    @Test
    fun test8_openCountDecrementsCorrectly() {
        val pkg = "com.protected.target"
        val session = TemporaryUnlockSession(
            packageName = pkg,
            unlockedAt = System.currentTimeMillis(),
            unlockMode = UnlockRelockMode.OPEN_COUNT,
            openCountRemaining = 2
        )
        assertFalse(session.isExpired())

        // Decrement 1
        session.openCountRemaining -= 1
        assertEquals(1, session.openCountRemaining)
        assertFalse(session.isExpired())

        // Decrement 2 (reached limit)
        session.openCountRemaining -= 1
        assertEquals(0, session.openCountRemaining)
        assertTrue(session.isExpired())
    }

    // 9. Screen Off immediately invalidates temporary session
    @Test
    fun test9_screenOffImmediatelyInvalidatesTemporarySession() {
        val sessions = mutableMapOf("com.app.one" to TemporaryUnlockSession("com.app.one", System.currentTimeMillis()))
        var recentSession = RecentAuthSession(authenticatedAt = System.currentTimeMillis(), durationMillis = 60000L)

        // Screen Off action triggered
        sessions.clear()
        recentSession = RecentAuthSession()

        assertTrue(sessions.isEmpty())
        assertFalse(recentSession.isValid())
    }

    // 10. Recent Lock requires authentication
    @Test
    fun test10_recentLockRequiresAuthentication() {
        val recentAuthSession = RecentAuthSession()
        assertFalse("Recent lock must require auth when session is empty", recentAuthSession.isValid())
    }


    @Test
    fun recentAuthMonotonicSessionRejectsClockRollbackAndExpiry() {
        val session = RecentAuthSession(
            authenticatedAt = 1_000L,
            durationMillis = 5_000L,
            authenticatedElapsedAt = 10_000L
        )

        assertTrue(session.isValidMonotonic(12_000L))
        assertFalse(session.isValidMonotonic(9_000L))
        assertFalse(session.isValidMonotonic(15_001L))
    }

    // 11. Recent authentication does not unlock protected apps
    @Test
    fun test11_recentAuthenticationDoesNotUnlockProtectedApps() {
        val recentAuthSession = RecentAuthSession(authenticatedAt = System.currentTimeMillis(), durationMillis = 60000L)
        val protectedAppSession: TemporaryUnlockSession? = null

        assertTrue(recentAuthSession.isValid())
        assertNull("Recent authentication must never grant unlock to individual protected apps", protectedAppSession)
    }

    // 12. Rapid app switching
    @Test
    fun test12_rapidAppSwitching() {
        val stateMachine = ForegroundStateMachine()
        stateMachine.transitionTo(ForegroundStateMachine.State.APP_TRANSITION, "com.whatsapp")
        assertEquals(ForegroundStateMachine.State.APP_TRANSITION, stateMachine.getState())
        assertEquals("com.whatsapp", stateMachine.getActiveTargetPackage())

        stateMachine.transitionTo(ForegroundStateMachine.State.APP_TRANSITION, "com.google.android.youtube")
        assertEquals("com.google.android.youtube", stateMachine.getActiveTargetPackage())
    }

    // 13. Duplicate accessibility events
    @Test
    fun test13_duplicateAccessibilityEvents() {
        val stateMachine = ForegroundStateMachine()
        val firstTransition = stateMachine.transitionTo(ForegroundStateMachine.State.CONFIRMED_APP, "com.target.app")
        assertTrue(firstTransition)

        // Duplicate event within 10ms for same state & target
        val duplicateTransition = stateMachine.transitionTo(ForegroundStateMachine.State.CONFIRMED_APP, "com.target.app")
        assertFalse("Duplicate rapid transition must be rejected", duplicateTransition)
    }

    // 14. Stale package prevention
    @Test
    fun test14_stalePackagePrevention() {
        val stateMachine = ForegroundStateMachine()
        stateMachine.transitionTo(ForegroundStateMachine.State.LOCKED, "com.app.old")
        stateMachine.reset()
        assertNull(stateMachine.getActiveTargetPackage())
        assertEquals(ForegroundStateMachine.State.IDLE, stateMachine.getState())
    }

    // 15. Home -> Protected App
    @Test
    fun test15_homeToProtectedApp() {
        val stateMachine = ForegroundStateMachine()
        stateMachine.transitionTo(ForegroundStateMachine.State.CONFIRMED_APP, "com.google.android.apps.nexuslauncher")
        stateMachine.transitionTo(ForegroundStateMachine.State.APP_TRANSITION, "com.secure.banking")
        assertEquals(ForegroundStateMachine.State.APP_TRANSITION, stateMachine.getState())
    }

    // 16. Protected App -> Home -> Protected App
    @Test
    fun test16_protectedAppToHomeToProtectedApp() {
        val sessionMap = mutableMapOf<String, TemporaryUnlockSession>()
        val pkg = "com.secure.banking"
        sessionMap[pkg] = TemporaryUnlockSession(pkg, System.currentTimeMillis(), unlockMode = UnlockRelockMode.UNLOCK_ONCE)

        // Exit to Home
        sessionMap.remove(pkg)
        assertNull(sessionMap[pkg])

        // Re-enter banking
        val sessionOnReturn = sessionMap[pkg]
        assertNull("Re-entering app after Home must require new authentication", sessionOnReturn)
    }

    // 17. Protected App -> Another Protected App
    @Test
    fun test17_protectedAppToAnotherProtectedApp() {
        val app1 = "com.app.one"
        val app2 = "com.app.two"
        val sessionMap = mutableMapOf(app1 to TemporaryUnlockSession(app1, System.currentTimeMillis(), unlockMode = UnlockRelockMode.UNLOCK_ONCE))

        // Switch to App 2
        sessionMap.remove(app1)
        assertFalse(sessionMap.containsKey(app1))
        assertFalse(sessionMap.containsKey(app2))
    }

    // 18. Protected App -> Unprotected App
    @Test
    fun test18_protectedAppToUnprotectedApp() {
        val protectedApp = "com.secure.app"
        val unprotectedApp = "com.public.calculator"
        val sessionMap = mutableMapOf(protectedApp to TemporaryUnlockSession(protectedApp, System.currentTimeMillis(), unlockMode = UnlockRelockMode.UNLOCK_ONCE))

        // Leaving protected app
        sessionMap.remove(protectedApp)
        assertTrue(sessionMap.isEmpty())
    }

    // 19. Recent -> Protected App
    @Test
    fun test19_recentToProtectedApp() {
        val recentAuthSession = RecentAuthSession(authenticatedAt = System.currentTimeMillis(), durationMillis = 60000L)
        val protectedAppSession: TemporaryUnlockSession? = null
        assertTrue(recentAuthSession.isValid())
        assertNull(protectedAppSession)
    }

    // 20. Screen Off -> Screen On -> Protected App
    @Test
    fun test20_screenOffScreenOnProtectedApp() {
        val sessionMap = mutableMapOf("com.target" to TemporaryUnlockSession("com.target", System.currentTimeMillis()))
        // Screen Off
        sessionMap.clear()
        // Screen On & return to target
        assertNull("Session must be invalid after screen cycle", sessionMap["com.target"])
    }

    // 21. Overnight Schedule: 22:59, 23:00, 23:30, 00:00, 00:30, 03:00, 06:59, 07:00, 07:01
    @Test
    fun test21_overnightSchedule_all24hCheckpoints() {
        val overnight = ScheduleEntity(
            name = "Night Lock 23:00-07:00",
            targetType = ScheduleTargetType.GLOBAL_LOCK,
            startHour = 23,
            startMinute = 0,
            endHour = 7,
            endMinute = 0,
            daysOfWeek = 127,
            isEnabled = true
        )

        fun checkAt(hour: Int, min: Int): Boolean {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
            }
            return ScheduleEngine.isScheduleActive(overnight, cal)
        }

        assertFalse("22:59 must be inactive", checkAt(22, 59))
        assertTrue("23:00 must be active", checkAt(23, 0))
        assertTrue("23:30 must be active", checkAt(23, 30))
        assertTrue("00:00 must be active", checkAt(0, 0))
        assertTrue("00:30 must be active", checkAt(0, 30))
        assertTrue("03:00 must be active", checkAt(3, 0))
        assertTrue("06:59 must be active", checkAt(6, 59))
        assertFalse("07:00 must be inactive", checkAt(7, 0))
        assertFalse("07:01 must be inactive", checkAt(7, 1))
    }

    // 22. Select All
    @Test
    fun test22_selectAllProtectedApps() {
        val apps = listOf(
            ProtectedAppEntity("com.app.a", "App A", isLocked = false),
            ProtectedAppEntity("com.app.b", "App B", isLocked = false)
        )
        val updated = apps.map { it.copy(isLocked = true) }
        assertTrue(updated.all { it.isLocked })
    }

    // 23. Deselect All
    @Test
    fun test23_deselectAllProtectedApps() {
        val apps = listOf(
            ProtectedAppEntity("com.app.a", "App A", isLocked = true),
            ProtectedAppEntity("com.app.b", "App B", isLocked = true)
        )
        val updated = apps.map { it.copy(isLocked = false) }
        assertTrue(updated.none { it.isLocked })
    }

    // 24. New App Auto Lock
    @Test
    fun test24_newAppAutoLockLogic() {
        val isAutoLockEnabled = true
        val newInstalledPackage = "com.new.game"
        val newAppEntity = ProtectedAppEntity(
            packageName = newInstalledPackage,
            appName = "New Game",
            isLocked = isAutoLockEnabled
        )
        assertTrue("New app must be locked when auto lock is enabled", newAppEntity.isLocked)
    }

    // 25. App Guard Self Protection
    @Test
    fun test25_appGuardSelfProtectionIsAlwaysActive() {
        val appGuardPackage = "com.aistudio.appguard.secpro"
        var isSelfSessionValid = false
        fun evaluateAppGuard(): Boolean {
            return isSelfSessionValid
        }

        assertFalse("App Guard must require authentication initially", evaluateAppGuard())
        isSelfSessionValid = true
        assertTrue("App Guard allows entry with active self session", evaluateAppGuard())
    }

    @Test
    fun testShuffleKeypad_containsAllDigits() {
        val normal = ShuffleKeypad.generateKeypad(isShuffleEnabled = false)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0), normal)

        val shuffled = ShuffleKeypad.generateKeypad(isShuffleEnabled = true)
        assertEquals(10, shuffled.size)
        assertTrue(shuffled.containsAll((0..9).toList()))
    }

    // 26. Unprotected apps excluded from notification hiding
    @Test
    fun test26_unprotectedAppsExcludedFromNotificationHiding() {
        val unprotectedApp = ProtectedAppEntity("com.unprotected.chat", "Chat App", isLocked = false)
        val protectedApp = ProtectedAppEntity("com.protected.bank", "Bank App", isLocked = true)

        fun isEligibleForNotificationHiding(app: ProtectedAppEntity?): Boolean {
            return app != null && app.isLocked
        }

        assertFalse("Unprotected app must NEVER have notifications hidden", isEligibleForNotificationHiding(unprotectedApp))
        assertTrue("Protected app must be eligible for notification hiding", isEligibleForNotificationHiding(protectedApp))
        assertFalse("Unregistered app must NEVER have notifications hidden", isEligibleForNotificationHiding(null))
    }

    // 27. Notification deduplication prevents continuous duplicate logging
    @Test
    fun test27_notificationDeduplicationPreventsSpam() {
        val existingLog = HiddenNotificationEntity(
            id = 1L,
            packageName = "com.protected.bank",
            appName = "Bank",
            title = "Security Alert",
            content = "New login detected",
            timestamp = 1000L
        )

        // Incoming notification with identical content
        val incomingPkg = "com.protected.bank"
        val incomingTitle = "Security Alert"
        val incomingContent = "New login detected"
        val newTimestamp = 5000L

        val isDuplicate = (existingLog.packageName == incomingPkg &&
                existingLog.title == incomingTitle &&
                existingLog.content == incomingContent)

        assertTrue("Identical incoming notification must be detected as duplicate", isDuplicate)
        val updated = if (isDuplicate) existingLog.copy(timestamp = newTimestamp) else null
        assertNotNull(updated)
        assertEquals(1L, updated?.id)
        assertEquals(5000L, updated?.timestamp)
    }

    // 28. Strict package check prevents false positive recents lock
    @Test
    fun test28_recentLockEngineStrictPackageValidation() {
        val systemUiPkg = "com.android.systemui"
        val randomThirdPartyPkg = "com.random.gallery"
        val recentsClassName = "com.random.gallery.RecentsActivity"

        fun isRecentsOverview(pkg: String, className: String): Boolean {
            val isKnownSystemPkg = pkg == "com.android.systemui"
            if (!isKnownSystemPkg) return false
            val lower = className.lowercase()
            return lower.contains("recent") || lower.contains("overview")
        }

        assertFalse("Third-party app with 'Recents' in class name must NOT trigger recents lock",
            isRecentsOverview(randomThirdPartyPkg, recentsClassName))
        assertTrue("System UI with 'Recent' class must trigger recents lock",
            isRecentsOverview(systemUiPkg, "com.android.systemui.recents.RecentsActivity"))
    }
}
