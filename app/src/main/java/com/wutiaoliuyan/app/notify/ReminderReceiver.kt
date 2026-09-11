package com.wutiaoliuyan.app.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wutiaoliuyan.app.MainActivity
import com.wutiaoliuyan.app.R
import com.wutiaoliuyan.app.WutiaoLiuyanApp
import com.wutiaoliuyan.app.model.TaskStatus
import com.wutiaoliuyan.app.widget.WidgetUpdater

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("task_id", -1)
        if (id <= 0) return
        val app = context.applicationContext as WutiaoLiuyanApp
        val db = app.repository.db
        when (intent.action) {
            "complete" -> {
                db.markStatus(id, TaskStatus.DONE)
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.toInt())
                WidgetUpdater.updateAll(context)
            }
            "snooze" -> {
                ReminderScheduler(context).scheduleIn(id, 30)
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.toInt())
            }
            else -> {
                val task = db.getTask(id) ?: return
                if (task.status == TaskStatus.DONE || task.deletedAt != null) return
                val open = PendingIntent.getActivity(context, id.toInt(), Intent(context, MainActivity::class.java).putExtra("open_task_id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val complete = PendingIntent.getBroadcast(context, id.toInt() + 100000, Intent(context, ReminderReceiver::class.java).setAction("complete").putExtra("task_id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val snooze = PendingIntent.getBroadcast(context, id.toInt() + 200000, Intent(context, ReminderReceiver::class.java).setAction("snooze").putExtra("task_id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(context, "tasks")
                    .setSmallIcon(R.drawable.ic_app)
                    .setContentTitle(task.title)
                    .setContentText(listOf(task.date, task.time, task.detail).filter(String::isNotBlank).joinToString(" · ").take(120))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .addAction(0, "完成", complete)
                    .addAction(0, "延期 30 分钟", snooze)
                    .build()
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id.toInt(), notification)
            }
        }
    }
}
