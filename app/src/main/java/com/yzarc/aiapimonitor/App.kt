package com.yzarc.aiapimonitor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.os.Build
import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.data.repository.BalanceRepository
import com.yzarc.aiapimonitor.service.BalanceWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    lateinit var database: AppDatabase
    lateinit var repository: BalanceRepository
    lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        database = AppDatabase.getInstance(this)
        repository = BalanceRepository(database, prefs)
        createNotificationChannel()
        scheduleBackgroundRefresh()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("balance_alert", "余额提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "AI API 余额不足提醒" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun scheduleBackgroundRefresh() {
        BalanceWorker.schedule(this)
    }
}
