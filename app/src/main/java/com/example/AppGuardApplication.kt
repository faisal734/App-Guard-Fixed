package com.example

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.data.repository.ProtectionRepository
import com.example.service.ScreenStateReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppGuardApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: ProtectionRepository
        private set

    private val screenStateReceiver = ScreenStateReceiver()

    override fun onCreate() {
        super.onCreate()
        repository = ProtectionRepository(this)

        // Register dynamic receiver for SCREEN_OFF events with Android 13/14+ support
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            screenFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}

