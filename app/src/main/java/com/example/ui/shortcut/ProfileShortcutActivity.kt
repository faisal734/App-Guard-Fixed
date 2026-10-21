package com.example.ui.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.ui.lock.LockOverlayActivity

/**
 * Trampoline activity triggered when user clicks a Profile Shortcut from the Android home screen.
 * It immediately forwards to LockOverlayActivity requesting authentication before activating the profile.
 */
class ProfileShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: ""

        if (profileId != -1L) {
            val lockIntent = Intent(this, LockOverlayActivity::class.java).apply {
                putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, packageName)
                putExtra(LockOverlayActivity.EXTRA_APP_NAME, profileName)
                putExtra(LockOverlayActivity.EXTRA_WIDGET_ACTION, LockOverlayActivity.ACTION_ACTIVATE_PROFILE)
                putExtra(LockOverlayActivity.EXTRA_PROFILE_ID, profileId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(lockIntent)
        }
        finish()
    }

    companion object {
        const val ACTION_ACTIVATE_PROFILE_SHORTCUT = "com.example.action.ACTIVATE_PROFILE_SHORTCUT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
    }
}
