package com.example.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.AppGuardApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ui.lock.LockOverlayActivity

class LockAllWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TRIGGER_LOCK_ALL = "com.example.action.TRIGGER_LOCK_ALL"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val intent = Intent(context, LockAllWidgetProvider::class.java).apply {
                action = ACTION_TRIGGER_LOCK_ALL
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_lock_all).apply {
                setOnClickPendingIntent(R.id.widget_lock_all_container, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TRIGGER_LOCK_ALL) {
            val app = context.applicationContext as? AppGuardApplication
            app?.let {
                val pendingResult = goAsync()
                it.applicationScope.launch(Dispatchers.IO) {
                    try {
                        it.repository.setAllAppsLocked(true)
                        it.repository.setGlobalLock(true)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

class UnlockAllWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val intent = Intent(context, LockOverlayActivity::class.java).apply {
                putExtra(LockOverlayActivity.EXTRA_WIDGET_ACTION, LockOverlayActivity.WIDGET_ACTION_UNLOCK_ALL)
                putExtra(LockOverlayActivity.EXTRA_APP_NAME, "إلغاء قفل الحماية للكل")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_unlock_all).apply {
                setOnClickPendingIntent(R.id.widget_unlock_all_container, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}

class ProfileQuickWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_profile).apply {
                setOnClickPendingIntent(R.id.widget_profile_toggle_btn, pendingIntent)
                setOnClickPendingIntent(R.id.widget_profile_container, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
