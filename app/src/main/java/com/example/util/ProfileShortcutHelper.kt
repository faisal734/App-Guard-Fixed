package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.R
import com.example.data.model.ProfileEntity
import com.example.ui.shortcut.ProfileShortcutActivity

object ProfileShortcutHelper {

    fun createProfileShortcut(context: Context, profile: ProfileEntity) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, context.getString(R.string.shortcut_not_supported), Toast.LENGTH_SHORT).show()
            return
        }

        val shortcutIntent = Intent(context, ProfileShortcutActivity::class.java).apply {
            action = ProfileShortcutActivity.ACTION_ACTIVATE_PROFILE_SHORTCUT
            putExtra(ProfileShortcutActivity.EXTRA_PROFILE_ID, profile.id)
            putExtra(ProfileShortcutActivity.EXTRA_PROFILE_NAME, profile.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val icon = createShortcutIcon(context, profile)

        val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "profile_shortcut_${profile.id}")
            .setShortLabel(profile.name)
            .setLongLabel(context.getString(R.string.shortcut_activate_label, profile.name))
            .setIcon(icon)
            .setIntent(shortcutIntent)
            .build()

        ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
        Toast.makeText(context, context.getString(R.string.shortcut_created_toast), Toast.LENGTH_SHORT).show()
    }

    private fun createShortcutIcon(context: Context, profile: ProfileEntity): IconCompat {
        return try {
            val size = 108
            val density = context.resources.displayMetrics.density
            val pxSize = (size * density).toInt().coerceAtLeast(144)
            val bitmap = Bitmap.createBitmap(pxSize, pxSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Circle background
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1E293B.toInt() // Dark theme slate
            }
            canvas.drawCircle(pxSize / 2f, pxSize / 2f, pxSize / 2f - 4, paint)

            // Border stroke
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF3B82F6.toInt() // Primary accent blue
                style = Paint.Style.STROKE
                strokeWidth = 5f * density
            }
            canvas.drawCircle(pxSize / 2f, pxSize / 2f, pxSize / 2f - 6, borderPaint)

            // Profile Icon / Emoji
            val emoji = profile.icon.ifBlank { "🛡" }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = pxSize * 0.45f
                textAlign = Paint.Align.CENTER
            }
            val yOffset = (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(emoji, pxSize / 2f, pxSize / 2f - yOffset, textPaint)

            IconCompat.createWithBitmap(bitmap)
        } catch (_: Exception) {
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        }
    }
}
