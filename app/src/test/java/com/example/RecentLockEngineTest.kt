package com.example

import com.example.engine.RecentLockEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentLockEngineTest {

    @Test
    fun detectsAndroidSystemUiRecentsSurface() {
        assertTrue(
            RecentLockEngine.isKnownRecentsSurface(
                "com.android.systemui",
                "com.android.systemui.recents.RecentsActivity"
            )
        )
    }

    @Test
    fun detectsSamsungOverviewSurface() {
        assertTrue(
            RecentLockEngine.isKnownRecentsSurface(
                "com.android.systemui",
                "com.android.systemui.recents.OverviewPanel"
            )
        )
    }

    @Test
    fun doesNotTreatThirdPartyActivityAsRecents() {
        assertFalse(
            RecentLockEngine.isKnownRecentsSurface(
                "com.example.gallery",
                "com.example.gallery.RecentsActivity"
            )
        )
    }

    @Test
    fun doesNotLockOrdinarySystemUiWindows() {
        assertFalse(
            RecentLockEngine.isKnownRecentsSurface(
                "com.android.systemui",
                "com.android.systemui.statusbar.phone.NotificationShadeWindowView"
            )
        )
    }

    @Test
    fun handlesMissingClassNameSafely() {
        assertFalse(
            RecentLockEngine.isKnownRecentsSurface(
                "com.android.systemui",
                null
            )
        )
    }
}
