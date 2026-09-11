package com.wutiaoliuyan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.wutiaoliuyan.app.MainActivity
import com.wutiaoliuyan.app.R
import com.wutiaoliuyan.app.WutiaoLiuyanApp

object WidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(TodayWidgetProvider::class.java, InboxWidgetProvider::class.java).forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_value)
            ids.forEach { id -> if (cls == TodayWidgetProvider::class.java) TodayWidgetProvider.update(context, manager, id) else InboxWidgetProvider.update(context, manager, id) }
        }
    }
}

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { update(context, manager, it) } }
    companion object {
        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val count = (context.applicationContext as WutiaoLiuyanApp).repository.db.todayCount()
            val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_value, count.toString())
                setOnClickPendingIntent(R.id.widget_value, openApp(context))
            }
            manager.updateAppWidget(id, views)
        }
    }
}

class InboxWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { update(context, manager, it) } }
    companion object {
        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val db = (context.applicationContext as WutiaoLiuyanApp).repository.db
            val views = RemoteViews(context.packageName, R.layout.widget_inbox).apply {
                setTextViewText(R.id.widget_value, "Inbox ${db.inboxCount()} · 今日 ${db.todayCount()}")
                setOnClickPendingIntent(R.id.widget_value, openApp(context))
            }
            manager.updateAppWidget(id, views)
        }
    }
}

private fun openApp(context: Context) = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
