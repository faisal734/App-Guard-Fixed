package com.example.flutterbridge

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.example.AppGuardApplication
import com.example.ui.lock.LockOverlayActivity
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Flutter is the primary dashboard in integrated builds; Kotlin remains the security engine. */
class AppGuardFlutterActivity : FlutterActivity() {
    private var protectionBridge: AppGuardFlutterBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        protectionBridge = AppGuardFlutterBridge(applicationContext, flutterEngine)
    }

    override fun onResume() {
        super.onResume()
        val app = application as? AppGuardApplication ?: return
        if (!app.repository.pinSecurity.isPinSet()) return
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { app.repository.getTemporaryUnlockSession(packageName) }
            if (session == null || session.isExpired()) {
                val intent = Intent(this@AppGuardFlutterActivity, LockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, packageName)
                    putExtra(LockOverlayActivity.EXTRA_APP_NAME, getString(com.example.R.string.app_name))
                    putExtra(LockOverlayActivity.EXTRA_IS_RECENTS_LOCK, false)
                }
                startActivity(intent)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as? AppGuardApplication)?.repository?.revokeTemporaryUnlock(packageName)
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        protectionBridge?.dispose()
        protectionBridge = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
