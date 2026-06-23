package com.yzarc.aiapimonitor.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.yzarc.aiapimonitor.MainActivity
import com.yzarc.aiapimonitor.R
import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.data.repository.BalanceRepository
import com.yzarc.aiapimonitor.data.repository.WidgetCacheManager
import java.util.concurrent.TimeUnit

class BalanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d("BalanceWorker", "background refresh start")
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val db = AppDatabase.getInstance(applicationContext)
        val repo = BalanceRepository(db, prefs)
        return try {
            val balances = repo.refreshAllBalances()
            val totalBalance = balances.sumOf { it.totalBalance }
            updateWidget(totalBalance, balances)
            checkAndNotify(totalBalance, balances)
            repo.cleanOldSnapshots()
            Log.d("BalanceWorker", "refresh done, total: \${totalBalance}")
            Result.success()
        } catch (e: Exception) { Log.e("BalanceWorker", "refresh failed: \${e.message}"); Result.retry() }
    }

    private suspend fun updateWidget(totalBalance: Double, balances: List<com.yzarc.aiapimonitor.model.BalanceInfo>) {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val repo = BalanceRepository(AppDatabase.getInstance(applicationContext), prefs)
        val accounts = repo.getAccounts()
        val widgetAccounts = balances.mapNotNull { balance -> val account = accounts.find { it.id == balance.accountId }; if (account != null) WidgetCacheManager.WidgetAccount(id = account.id, name = account.displayName, platform = account.platform, balance = balance.totalBalance, currency = balance.currency) else null }
        repo.widgetCacheManager.saveWidgetData(totalBalance, widgetAccounts)
        BalanceWidgetSmall.updateAllWidgets(applicationContext)
        BalanceWidgetMedium.updateAllWidgets(applicationContext)
        BalanceWidgetLarge.updateAllWidgets(applicationContext)
    }

    private fun checkAndNotify(totalBalance: Double, balances: List<com.yzarc.aiapimonitor.model.BalanceInfo>) {
        for (balance in balances) {
            val level = when { balance.totalBalance < 10 -> "urgent"; balance.totalBalance < 20 -> "critical"; balance.totalBalance < 50 -> "warning"; else -> null }
            if (level != null && !recentlyNotified(balance.accountId, level)) { sendNotification(balance, level); markNotified(balance.accountId, level) }
        }
    }

    private fun recentlyNotified(accountId: Int, level: String): Boolean { val lastTime = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getLong("notify_\${accountId}_\${level}", 0L); return System.currentTimeMillis() - lastTime < 6 * 3600 * 1000 }
    private fun markNotified(accountId: Int, level: String) { applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putLong("notify_\${accountId}_\${level}", System.currentTimeMillis()).apply() }

    private fun sendNotification(balance: com.yzarc.aiapimonitor.model.BalanceInfo, level: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return }
        val title = when (level) { "urgent" -> "API余额告急"; "critical" -> "API余额严重不足"; "warning" -> "API余额偏低"; else -> return }
        val body = when (level) { "urgent" -> "余额仅剩 \${balance.format()}"; "critical" -> "余额仅剩 \${balance.format()}"; "warning" -> "余额 \${balance.format()}"; else -> return }
        val intent = Intent(applicationContext, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "balance_alert").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title).setContentText(body).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pendingIntent).build()
        NotificationManagerCompat.from(applicationContext).notify(notificationId(balance.accountId, level), notification)
    }

    companion object {
        private const val WORK_NAME = "balance_refresh"
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<BalanceWorker>(30, TimeUnit.MINUTES).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            Log.d("BalanceWorker", "WorkManager scheduled: every 30min")
        }
        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
        private fun notificationId(accountId: Int, level: String): Int { val levelId = when (level) { "urgent" -> 1; "critical" -> 2; "warning" -> 3; else -> 4 }; return accountId * 100 + levelId }
    }
}
