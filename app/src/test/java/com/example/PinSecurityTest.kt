package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.PinSecurity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PinSecurityTest {

    private fun newSecurity(): PinSecurity {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_guard_sec_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        return PinSecurity(context)
    }

    @Test
    fun `rejects malformed PINs`() {
        val security = newSecurity()
        assertFalse(security.setPin("123"))
        assertFalse(security.setPin("12a4"))
        assertFalse(security.setPin("1234567890123"))
        assertTrue(security.setPin("7391"))
    }

    @Test
    fun `cooldown is enforced in verifier not only in UI`() {
        val security = newSecurity()
        assertTrue(security.setPin("7391"))
        assertFalse(security.verifyPin("0000"))
        assertFalse(security.verifyPin("0000"))
        assertFalse(security.verifyPin("0000"))

        assertTrue(security.getRemainingCooldownSeconds() > 0L)
        // A correct PIN cannot bypass an active cooldown through a direct method call.
        assertFalse(security.verifyPin("7391"))
    }
}
