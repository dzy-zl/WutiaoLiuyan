package com.wutiaoliuyan.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wutiaoliuyan.app.WutiaoLiuyanApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as WutiaoLiuyanApp
        val scheduler = ReminderScheduler(context)
        app.repository.db.allPendingReminderTasks().forEach(scheduler::schedule)
    }
}
