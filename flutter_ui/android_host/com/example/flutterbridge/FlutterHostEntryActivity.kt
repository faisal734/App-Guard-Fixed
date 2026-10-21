package com.example.flutterbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Safe native entry point: does not depend on Flutter in the default build. */
class FlutterHostEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val flutterActivity = Class.forName("com.example.flutterbridge.AppGuardFlutterActivity")
            startActivity(Intent(this, flutterActivity))
        } catch (_: ClassNotFoundException) {
            Toast.makeText(this, "واجهة Flutter غير مدمجة بعد. نفّذ إعداد المرحلة الرابعة أولًا.", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
