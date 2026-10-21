package com.example.engine

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.example.data.model.ProtectionDecision
import com.example.data.model.KeyboardLockBehavior
import com.example.data.repository.ProtectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unified Foreground App Resolver.
 * Resolves active foreground applications, handles window state transitions,
 * prevents duplicate events, race conditions, stale target packages, and locking loops.
 */
class ForegroundAppResolver(
    private val context: android.content.Context,
    private val appGuardPackage: String,
    private val repository: ProtectionRepository,
    private val decisionEngine: ProtectionDecisionEngine,
    private val recentLockEngine: RecentLockEngine,
    private val stateMachine: ForegroundStateMachine
) {
    sealed class ResolverAction {
        data class ShowLockOverlay(
            val targetPackage: String,
            val appName: String,
            val isRecentsLock: Boolean
        ) : ResolverAction()

        data class DismissLockOverlay(
            val expectedPackage: String
        ) : ResolverAction()

        data object NoAction : ResolverAction()
    }

    private val _currentForegroundPackage = MutableStateFlow("")
    val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

    private val _previousForegroundPackage = MutableStateFlow("")
    val previousForegroundPackage: StateFlow<String> = _previousForegroundPackage.asStateFlow()

    private val _pendingTargetPackage = MutableStateFlow<String?>(null)
    val pendingTargetPackage: StateFlow<String?> = _pendingTargetPackage.asStateFlow()

    private val _lockedTargetPackage = MutableStateFlow<String?>(null)
    val lockedTargetPackage: StateFlow<String?> = _lockedTargetPackage.asStateFlow()

    private var lastHandledPackage: String = ""
    private var lastHandledClass: String = ""
    private var lastEventElapsed: Long = 0L

    private val ignoredOverlayClasses = setOf(
        "com.example.ui.lock.LockOverlayActivity",
        "LockOverlayActivity",
        "BiometricPrompt",
        "FingerprintDialog",
        "BiometricDialog",
        "BiometricPromptCompat",
        "CredentialManager"
    )

    private val inputMethodPackages = setOf(
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.syntellia.fleksy.keyboard",
        "com.touchtype.swiftkey"
    )

    private val systemExemptHeadlessPackages = setOf(
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
        "android"
    )

    private fun getActiveIMEs(): Set<String> {
        return try {
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val imes = imm?.enabledInputMethodList ?: emptyList()
            imes.map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Resolves the accessibility event and returns the appropriate action.
     */
    suspend fun resolveEvent(event: AccessibilityEvent): ResolverAction {
        val eventType = event.eventType
        val rawPackage = event.packageName?.toString() ?: return ResolverAction.NoAction
        val rawClass = event.className?.toString() ?: ""
        val lowerClass = rawClass.lowercase()
        val nowElapsed = SystemClock.elapsedRealtime()

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return ResolverAction.NoAction
        }

        // Do not trigger lock overlay while device Keyguard (screen lock) is active to prevent window flicker (e.g. Alarms, Calls)
        val keyguardManager = context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            return ResolverAction.NoAction
        }

        // Prevent duplicate lock overlay launches and timing race conditions
        val currentState = stateMachine.getState()
        if ((currentState == ForegroundStateMachine.State.LOCKED || currentState == ForegroundStateMachine.State.AUTHENTICATING) &&
            stateMachine.getActiveTargetPackage() == rawPackage) {
            return ResolverAction.NoAction
        }

        // 1. Ignore input methods & virtual keyboards (Proposal 1 + Proposal 3)
        val kbBehavior = repository.settingsFlow.value.keyboardLockBehavior
        if (kbBehavior == KeyboardLockBehavior.ALWAYS_UNLOCKED) {
            val activeIMEs = getActiveIMEs()
            if (activeIMEs.contains(rawPackage) || 
                inputMethodPackages.contains(rawPackage) || 
                rawPackage.contains("inputmethod") || 
                rawPackage.contains("honeyboard") || 
                rawPackage.contains("keyboard") ||
                lowerClass.contains("inputmethod") ||
                lowerClass.contains("keyboard") ||
                lowerClass.contains("softinput") ||
                lowerClass.contains("ime")
            ) {
                return ResolverAction.NoAction
            }
        }

        // 2. Ignore headless system tools, screenshots, and Samsung Wallet overlays
        if (systemExemptHeadlessPackages.contains(rawPackage) || rawPackage.contains("screenshot") || rawPackage.contains("smartcapture") || rawPackage.contains("spay")) {
            return ResolverAction.NoAction
        }

        // 3. Ignore Biometric Prompt dialogs and LockOverlayActivity
        if (ignoredOverlayClasses.any { rawClass.contains(it) } || rawPackage.contains("biometrics")) {
            // Do not override target package, just transition state using current active target
            val currentTarget = stateMachine.getActiveTargetPackage() ?: rawPackage
            stateMachine.transitionTo(ForegroundStateMachine.State.AUTHENTICATING, currentTarget)
            return ResolverAction.NoAction
        }

        // 4. Suppress self-protection loops (App Guard should only be evaluated if it's the MainActivity)
        if (rawPackage == appGuardPackage) {
            if (!rawClass.contains("MainActivity")) {
                val currentTarget = stateMachine.getActiveTargetPackage() ?: rawPackage
                stateMachine.transitionTo(ForegroundStateMachine.State.AUTHENTICATING, currentTarget)
                return ResolverAction.NoAction
            }
        }

        // 4. Ignore floating notifications, toast overlays, and non-activity popups (e.g. Athkar reminders)
        val isFloatingOrPopup = !event.isFullScreen && (
            lowerClass.contains("toast") ||
            lowerClass.contains("notification") ||
            lowerClass.contains("popup") ||
            lowerClass.contains("tooltip") ||
            lowerClass.contains("snackbar") ||
            lowerClass.contains("floating") ||
            lowerClass.contains("headsup") ||
            lowerClass.contains("banner") ||
            lowerClass.contains("overlay") ||
            lowerClass.contains("widget") ||
            lowerClass.contains("alert")
        )
        if (isFloatingOrPopup && !recentLockEngine.isRecentsOverview(rawPackage, rawClass)) {
            return ResolverAction.NoAction
        }

        // 3. Debounce identical package & class rapid duplicate events (<120ms)
        if (rawPackage == lastHandledPackage && rawClass == lastHandledClass && (nowElapsed - lastEventElapsed) < 120) {
            return ResolverAction.NoAction
        }

        lastEventElapsed = nowElapsed
        val prevPkg = lastHandledPackage
        lastHandledPackage = rawPackage
        lastHandledClass = rawClass

        // 4. Update foreground package flows and trigger session transitions
        if (prevPkg != rawPackage) {
            _previousForegroundPackage.value = prevPkg
            _currentForegroundPackage.value = rawPackage
            repository.previousForegroundPackage.value = prevPkg
            repository.currentForegroundPackage.value = rawPackage

            // Enforce UNLOCK_ONCE and OPEN_COUNT rules on app boundaries
            repository.onForegroundPackageChanged(prevPkg, rawPackage)
        }

        // 5. Recent / Overview Inspection
        if (recentLockEngine.isRecentsOverview(rawPackage, rawClass)) {
            if (recentLockEngine.shouldBlockRecents()) {
                _pendingTargetPackage.value = "com.android.systemui.recents"
                _lockedTargetPackage.value = "com.android.systemui.recents"
                stateMachine.transitionTo(ForegroundStateMachine.State.LOCKING, "com.android.systemui.recents")
                return ResolverAction.ShowLockOverlay(
                    targetPackage = "com.android.systemui.recents",
                    appName = "التطبيقات الحديثة (Overview / Recent Apps)",
                    isRecentsLock = true
                )
            } else {
                return ResolverAction.NoAction
            }
        }

        // 6. Evaluate Target Application through Protection Decision Engine
        _pendingTargetPackage.value = rawPackage
        stateMachine.transitionTo(ForegroundStateMachine.State.APP_TRANSITION, rawPackage)

        val decision = decisionEngine.evaluate(rawPackage)
        return when (decision) {
            is ProtectionDecision.Block -> {
                _lockedTargetPackage.value = decision.targetPackage
                stateMachine.transitionTo(ForegroundStateMachine.State.LOCKED, decision.targetPackage)
                repository.currentStateMachineState.value = "LOCKED: ${decision.reason}"
                ResolverAction.ShowLockOverlay(
                    targetPackage = decision.targetPackage,
                    appName = decision.appName,
                    isRecentsLock = false
                )
            }
            is ProtectionDecision.Allow -> {
                _lockedTargetPackage.value = null
                stateMachine.transitionTo(ForegroundStateMachine.State.CONFIRMED_APP, rawPackage)
                repository.currentStateMachineState.value = "ALLOWED: ${decision.reason}"
                ResolverAction.NoAction
            }
        }
    }

    suspend fun resolvePackageFallback(rawPackage: String): ResolverAction {
        val nowElapsed = SystemClock.elapsedRealtime()

        // Do not trigger lock overlay while device Keyguard is active
        val keyguardManager = context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            return ResolverAction.NoAction
        }

        // Prevent duplicate lock overlay launches and timing race conditions from fallback poller
        val currentState = stateMachine.getState()
        if ((currentState == ForegroundStateMachine.State.LOCKED || currentState == ForegroundStateMachine.State.AUTHENTICATING) &&
            stateMachine.getActiveTargetPackage() == rawPackage) {
            return ResolverAction.NoAction
        }

        val kbBehavior = repository.settingsFlow.value.keyboardLockBehavior
        if (kbBehavior == KeyboardLockBehavior.ALWAYS_UNLOCKED) {
            val activeIMEs = getActiveIMEs()
            if (activeIMEs.contains(rawPackage) || 
                inputMethodPackages.contains(rawPackage) || 
                rawPackage.contains("inputmethod") || 
                rawPackage.contains("honeyboard") || 
                rawPackage.contains("keyboard")
            ) {
                return ResolverAction.NoAction
            }
        }

        if (systemExemptHeadlessPackages.contains(rawPackage) || rawPackage.contains("screenshot") || rawPackage.contains("smartcapture") || rawPackage.contains("spay")) {
            return ResolverAction.NoAction
        }

        if (rawPackage == appGuardPackage) {
            val currentTarget = stateMachine.getActiveTargetPackage()
            if (currentTarget != null && currentTarget != rawPackage) {
                // If it's already authenticating or locking, ignore fallback override
                if (stateMachine.getState() == ForegroundStateMachine.State.AUTHENTICATING) {
                    return ResolverAction.NoAction
                }
            }
        }

        if (rawPackage == lastHandledPackage) {
            // Already handled this package, check if it's currently allowed
            val session = repository.getTemporaryUnlockSession(rawPackage)
            if (session != null && !session.isExpired()) {
                return ResolverAction.NoAction
            }
            // If session expired while on screen, we could re-lock, but usually we let the user continue.
            // However, timeout policy might require re-lock. The decision engine will handle it.
        }

        val prevPkg = lastHandledPackage
        lastHandledPackage = rawPackage
        lastHandledClass = "" // Unknown class via fallback
        lastEventElapsed = nowElapsed

        if (prevPkg != rawPackage) {
            _previousForegroundPackage.value = prevPkg
            _currentForegroundPackage.value = rawPackage
            repository.previousForegroundPackage.value = prevPkg
            repository.currentForegroundPackage.value = rawPackage
            repository.onForegroundPackageChanged(prevPkg, rawPackage)
        }

        // Avoid overriding recents lock if it's active
        if (stateMachine.getActiveTargetPackage() == "com.android.systemui.recents") {
            return ResolverAction.NoAction
        }

        _pendingTargetPackage.value = rawPackage
        stateMachine.transitionTo(ForegroundStateMachine.State.APP_TRANSITION, rawPackage)
        val decision = decisionEngine.evaluate(rawPackage)

        return when (decision) {
            is ProtectionDecision.Block -> {
                _lockedTargetPackage.value = decision.targetPackage
                stateMachine.transitionTo(ForegroundStateMachine.State.LOCKED, decision.targetPackage)
                repository.currentStateMachineState.value = "LOCKED (Fallback): ${decision.reason}"
                ResolverAction.ShowLockOverlay(
                    targetPackage = decision.targetPackage,
                    appName = decision.appName,
                    isRecentsLock = false
                )
            }
            is ProtectionDecision.Allow -> {
                if (_lockedTargetPackage.value == rawPackage) {
                    _lockedTargetPackage.value = null
                }
                stateMachine.transitionTo(ForegroundStateMachine.State.CONFIRMED_APP, rawPackage)
                repository.currentStateMachineState.value = "ALLOWED (Fallback): ${decision.reason}"
                ResolverAction.NoAction
            }
        }
    }

    fun onUnlockGranted(packageName: String) {
        _lockedTargetPackage.value = null
        _pendingTargetPackage.value = null
        stateMachine.transitionTo(ForegroundStateMachine.State.TEMPORARILY_UNLOCKED, packageName)
    }

    fun onScreenOff() {
        _lockedTargetPackage.value = null
        _pendingTargetPackage.value = null
        stateMachine.transitionTo(ForegroundStateMachine.State.RELOCKING)
    }
}
