package com.wutiaoliuyan.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wutiaoliuyan.app.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(task: Task) {
        if (!task.reminderEnabled || task.date.isBlank() || task.status.name == "DONE") return
        val date = runCatching { LocalDate.parse(task.date) }.getOrNull() ?: return
        val due = if (task.time.isBlank()) {
            LocalDateTime.of(date.minusDays(1), LocalTime.of(20, 0))
        } else {
            val time = runCatching { LocalTime.parse(task.time) }.getOrNull() ?: return
            LocalDateTime.of(date, time).minusMinutes(task.reminderMinutesBefore.toLong())
        }
        val millis = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis <= System.currentTimeMillis()) return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(task.id))
    }

    fun scheduleIn(taskId: Long, minutes: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000, pending(taskId))
    }

    fun cancel(taskId: Long) { alarms.cancel(pending(taskId)) }

    private fun pending(taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        Intent(context, ReminderReceiver::class.java).putExtra("task_id", taskId).setAction("notify"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
