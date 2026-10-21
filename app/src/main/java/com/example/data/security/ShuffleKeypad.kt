package com.example.data.security

/**
 * Generates keypad digit layouts, either standard [1..9, 0] or shuffled random order.
 */
object ShuffleKeypad {
    val STANDARD_KEYPAD: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)

    fun generateKeypad(isShuffleEnabled: Boolean): List<Int> {
        return if (isShuffleEnabled) {
            (0..9).shuffled()
        } else {
            STANDARD_KEYPAD
        }
    }
}
