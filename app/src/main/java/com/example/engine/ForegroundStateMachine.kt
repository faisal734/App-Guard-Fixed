package com.example.engine

/**
 * Robust Foreground State Machine preventing race conditions, duplicate lock screens,
 * lock loops, and stale target package state.
 */
class ForegroundStateMachine {

    enum class State {
        IDLE,
        APP_TRANSITION,
        CONFIRMED_APP,
        LOCK_PENDING,
        LOCKING,
        LOCKED,
        AUTHENTICATING,
        TEMP_UNLOCKED,
        TEMPORARILY_UNLOCKED,
        RELOCKING
    }

    private var currentState: State = State.IDLE
    private var activeTargetPackage: String? = null
    private var lastStateChangeTimestamp: Long = System.currentTimeMillis()

    @Synchronized
    fun getState(): State = currentState

    @Synchronized
    fun getActiveTargetPackage(): String? = activeTargetPackage

    @Synchronized
    fun transitionTo(newState: State, targetPackage: String? = null): Boolean {
        // Prevent rapid duplicate transitions
        val now = System.currentTimeMillis()
        if (currentState == newState && activeTargetPackage == targetPackage && (now - lastStateChangeTimestamp) < 100) {
            return false
        }

        currentState = newState
        activeTargetPackage = targetPackage
        lastStateChangeTimestamp = now
        return true
    }

    @Synchronized
    fun reset() {
        currentState = State.IDLE
        activeTargetPackage = null
        lastStateChangeTimestamp = System.currentTimeMillis()
    }
}
