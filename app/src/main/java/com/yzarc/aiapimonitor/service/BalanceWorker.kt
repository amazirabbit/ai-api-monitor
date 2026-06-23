package com.yzarc.aiapimonitor.service

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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

/**
 * 后台余额刷新 Worker
 *
 * 重构后职责更清晰：
 * 1. 刷新所有账号余额（自动保存快照）
 * 2. 更新桌面小部件
 * 3. 余额过低时发送通知
 * 4. 清理过期快照
 */
class BalanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("BalanceWorker", "后台刷新开始...")
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val db = AppDatabase.getInstance(applicationContext)
        val repo = BalanceRepository(db, prefs)

        return try {
            val balances = repo.refreshAllBalances()
            val totalBalance = balances.sumOf { it.totalBalance }

            // 更新 Widget（传递各账号余额）
            updateWidget(totalBalance, balances)

            // 检查余额告警
            checkAndNotify(totalBalance, balances)

            // 清理旧快照
            repo.cleanOldSnapshots()

            Log.d("BalanceWorker", "后台刷新完成, 总余额: ¥${"%.2f".format(totalBalance)}")
            Result.success()
        } catch (e: Exception) {
            Log.e("BalanceWorker", "后台刷新失败: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun updateWidget(totalBalance: Double, balances: List<com.yzarc.aiapimonitor.model.BalanceInfo>) {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val repo = BalanceRepository(AppDatabase.getInstance(applicationContext), prefs)

        // 获取账号信息并构建 Widget 数据
        val accounts = repo.getAccounts()
        val widgetAccounts = balances.mapNotNull { balance ->
            val account = accounts.find { it.id == balance.accountId }
            if (account != null) {
                WidgetCacheManager.WidgetAccount(
                    id = account.id,
                    name = account.displayName,
                    platform = account.platform,
                    balance = balance.totalBalance,
                    currency = balance.currency
                )
            } else null
        }

        // 保存到缓存
        repo.widgetCacheManager.saveWidgetData(totalBalance, widgetAccounts)

        // 更新所有三种尺寸的 Glance Widget
        BalanceWidgetSmall.updateAllWidgets(applicationContext)
        BalanceWidgetMedium.updateAllWidgets(applicationContext)
        BalanceWidgetLarge.updateAllWidgets(applicationContext)

        // 保留原有 Widget 更新（兼容性）
        BalanceWidgetProvider.updateAllWidgets(applicationContext)
    }

    private fun checkAndNotify(totalBalance: Double, balances: List<com.yzarc.aiapimonitor.model.BalanceInfo>) {
        for (balance in balances) {
            val level = when {
                balance.totalBalance < 10 -> "urgent"
                balance.totalBalance < 20 -> "critical"
                balance.totalBalance < 50 -> "warning"
                else -> null
            }
            if (level != null && !recentlyNotified(balance.accountId, level)) {
                sendNotification(balance, level)
                markNotified(balance.accountId, level)
            }
        }
    }

    private fun recentlyNotified(accountId: Int, level: String): Boolean {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lastTime = prefs.getLong("notify_${accountId}_${level}", 0L)
        return System.currentTimeMillis() - lastTime < 6 * 3600 * 1000
    }

    private fun markNotified(accountId: Int, level: String) {
        applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putLong("notify_${accountId}_${level}", System.currentTimeMillis())
            .apply()
    }

    private fun sendNotification(balance: com.yzarc.aiapimonitor.model.BalanceInfo, level: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val title = when (level) {
            "urgent" -> "🚨 API余额告急"
            "critical" -> "⚠️ API余额严重不足"
            "warning" -> "⚡ API余额偏低"
            else -> return
        }

        val body = when (level) {
            "urgent" -> "余额仅剩 ${balance.format()}，请立即充值！"
            "critical" -> "余额仅剩 ${balance.format()}，建议尽快充值"
            "warning" -> "余额 ${balance.format()}，请注意关注"
            else -> return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "balance_alert")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(notificationId(balance.accountId, level), notification)
    }

    companion object {
        private const val WORK_NAME = "balance_refresh"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BalanceWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d("BalanceWorker", "WorkManager 已调度: 每30分钟刷新")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun notificationId(accountId: Int, level: String): Int {
            val levelId = when (level) {
                "urgent" -> 1
                "critical" -> 2
                "warning" -> 3
                else -> 4
            }
            return accountId * 100 + levelId
        }
    }
}