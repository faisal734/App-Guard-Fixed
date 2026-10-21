package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.os.SystemClock
import com.example.AppGuardApplication
import com.example.engine.ForegroundAppResolver
import com.example.engine.ForegroundStateMachine
import com.example.engine.ProtectionDecisionEngine
import com.example.engine.RecentLockEngine
import com.example.ui.lock.LockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.engine.UsageStatsHelper

class AppGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var resolver: ForegroundAppResolver
    private val stateMachine = ForegroundStateMachine()
    private lateinit var usageStatsHelper: UsageStatsHelper
    private var fallbackJob: Job? = null
    private val eventProcessingMutex = Mutex()
    @Volatile private var lastAccessibilityEventAt: Long = 0L

    companion object {
        var isServiceRunning: Boolean = false
            private set
    }

    private lateinit var decisionEngine: ProtectionDecisionEngine
    private lateinit var recentLockEngine: RecentLockEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        val app = application as? AppGuardApplication ?: return
        val repository = app.repository
        decisionEngine = ProtectionDecisionEngine(this, repository)
        recentLockEngine = RecentLockEngine(repository)

        resolver = ForegroundAppResolver(
            context = this,
            appGuardPackage = packageName,
            repository = repository,
            decisionEngine = decisionEngine,
            recentLockEngine = recentLockEngine,
            stateMachine = stateMachine
        )

        usageStatsHelper = UsageStatsHelper(this)
        startFallbackPoller()
    }

    private fun startFallbackPoller() {
        fallbackJob?.cancel()
        fallbackJob = serviceScope.launch {
            while (isServiceRunning) {
                val recentsIsActive = stateMachine.getActiveTargetPackage() == "com.android.systemui.recents"
                // Poll more frequently only while the Recents shield is active, so short auth
                // sessions expire promptly without adding background work during normal use.
                delay(if (recentsIsActive) 500L else 2000L)

                if (recentsIsActive && ::recentLockEngine.isInitialized &&
                    recentLockEngine.shouldBlockRecents() &&
                    (!LockOverlayActivity.isOverlayActive || !LockOverlayActivity.activeIsRecents)
                ) {
                    eventProcessingMutex.withLock {
                        launchLockOverlay(
                            targetPackage = "com.android.systemui.recents",
                            appName = "التطبيقات الحديثة (Recent Apps)",
                            isRecentsLock = true
                        )
                    }
                    continue
                }

                // Accessibility events are the primary signal. Poll only when that signal has
                // gone quiet, keeping the fallback useful without continuously duplicating work.
                val now = SystemClock.elapsedRealtime()
                if (lastAccessibilityEventAt != 0L && now - lastAccessibilityEventAt < 4000L) continue
                val pkg = usageStatsHelper.getForegroundPackageFallback()
                if (pkg != null && ::resolver.isInitialized) {
                    eventProcessingMutex.withLock {
                        val action = resolver.resolvePackageFallback(pkg)
                        when (action) {
                            is ForegroundAppResolver.ResolverAction.ShowLockOverlay -> {
                                launchLockOverlay(
                                    targetPackage = action.targetPackage,
                                    appName = action.appName,
                                    isRecentsLock = action.isRecentsLock
                                )
                            }
                            else -> { }
                        }
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::resolver.isInitialized) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // The framework may recycle its event after this callback. Work with our own copy.
        val eventCopy = AccessibilityEvent.obtain(event)
        lastAccessibilityEventAt = SystemClock.elapsedRealtime()
        serviceScope.launch {
            try {
                eventProcessingMutex.withLock {
                    val action = resolver.resolveEvent(eventCopy)
                    when (action) {
                        is ForegroundAppResolver.ResolverAction.ShowLockOverlay -> {
                            launchLockOverlay(
                                targetPackage = action.targetPackage,
                                appName = action.appName,
                                isRecentsLock = action.isRecentsLock
                            )
                        }
                        is ForegroundAppResolver.ResolverAction.DismissLockOverlay -> Unit
                        is ForegroundAppResolver.ResolverAction.NoAction -> inspectMultiWindowsAndPip()
                    }
                }
            } finally {
                eventCopy.recycle()
            }
        }
    }

    private suspend fun inspectMultiWindowsAndPip() {
        val app = application as? AppGuardApplication ?: return
        val repository = app.repository
        val settings = repository.settingsFlow.value
        if (!settings.isProtectionEnabled) return

        val activeWindows = try { windows } catch (_: Exception) { null } ?: return
        if (activeWindows.size <= 1) return

        for (windowInfo in activeWindows) {
            if (windowInfo.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                val rootNode = try { windowInfo.root } catch (_: Exception) { null }
                val pkg = rootNode?.packageName?.toString() ?: continue
                if (pkg == packageName || pkg == "com.android.systemui") continue

                val decision = decisionEngine.evaluate(pkg)
                if (decision is com.example.data.model.ProtectionDecision.Block) {
                    launchLockOverlay(
                        targetPackage = decision.targetPackage,
                        appName = decision.appName,
                        isRecentsLock = false
                    )
                    break
                }
            }
        }
    }

    private suspend fun launchLockOverlay(targetPackage: String, appName: String, isRecentsLock: Boolean) {
        // Prevent redundant overlay launch if already actively shown for target
        if (LockOverlayActivity.isOverlayActive &&
            LockOverlayActivity.activePackage == targetPackage &&
            LockOverlayActivity.activeIsRecents == isRecentsLock) {
            return
        }

        withContext(Dispatchers.Main) {
            val intent = Intent(this@AppGuardAccessibilityService, LockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(LockOverlayActivity.EXTRA_APP_NAME, appName)
                putExtra(LockOverlayActivity.EXTRA_IS_RECENTS_LOCK, isRecentsLock)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        fallbackJob?.cancel()
        serviceScope.cancel()
    }
}
