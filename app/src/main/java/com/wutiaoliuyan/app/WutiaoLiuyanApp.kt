package com.wutiaoliuyan.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.wutiaoliuyan.app.data.AppRepository

class WutiaoLiuyanApp : Application() {
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        repository.db.emptyOldTrash(30)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("tasks", "任务提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "五条六眼任务到期提醒"
        })
    }
}
