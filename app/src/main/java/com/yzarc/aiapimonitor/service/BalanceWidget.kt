package com.yzarc.aiapimonitor.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.yzarc.aiapimonitor.MainActivity
import com.yzarc.aiapimonitor.data.repository.WidgetCacheManager

class BalanceWidgetSmall : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { SmallWidgetContent() } }
    @Composable private fun SmallWidgetContent() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val cacheManager = WidgetCacheManager(prefs)
        val totalBalance = cacheManager.getWidgetBalance()
        GlanceTheme {
            Box(modifier = GlanceModifier.fillMaxSize().background(Color(0xFF1E2329)).padding(8.dp).clickable(actionRunCallback<OpenAppCallback>())) {
                Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(text = "总余额", style = TextStyle(color = ColorProvider(Color(0xFF707A8A)), fontSize = 10.sp))
                        Text(text = String.format("%.2f", totalBalance), style = TextStyle(color = ColorProvider(Color(0xFF0ECB81)), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    }
                    Box(modifier = GlanceModifier.size(10.dp).background(Color(0xFF0ECB81)).cornerRadius(5.dp), content = {})
                }
            }
        }
    }
    companion object { fun updateAllWidgets(context: Context) { val manager = AppWidgetManager.getInstance(context); val widgetIds = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetSmallReceiver::class.java)); if (widgetIds.isNotEmpty()) { val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply { setPackage(context.packageName); putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds) }; context.sendBroadcast(intent) } } }
}

class BalanceWidgetMedium : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { MediumWidgetContent() } }
    @Composable private fun MediumWidgetContent() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val cacheManager = WidgetCacheManager(prefs)
        val totalBalance = cacheManager.getWidgetBalance()
        val accounts = cacheManager.getWidgetAccounts()
        GlanceTheme {
            Box(modifier = GlanceModifier.fillMaxSize().background(Color(0xFF1E2329)).padding(12.dp).clickable(actionRunCallback<OpenAppCallback>())) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(text = "AI 余额监控", style = TextStyle(color = ColorProvider(Color(0xFFFCD535)), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(text = String.format("%.2f", totalBalance), style = TextStyle(color = ColorProvider(Color(0xFF0ECB81)), fontSize = 28.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    accounts.take(4).forEach { account ->
                        Row(modifier = GlanceModifier.fillMaxWidth().background(Color(0x1AFFFFFF)).cornerRadius(6.dp).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = GlanceModifier.size(8.dp).background(Color(0xFF10A37F)).cornerRadius(4.dp), content = {})
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(text = account.name.take(10), style = TextStyle(color = ColorProvider(Color(0xFFEAECEF)), fontSize = 11.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
                            Text(text = String.format("%.2f", account.balance), style = TextStyle(color = ColorProvider(Color(0xFF0ECB81)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }
                }
            }
        }
    }
    companion object { fun updateAllWidgets(context: Context) { val manager = AppWidgetManager.getInstance(context); val widgetIds = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetMediumReceiver::class.java)); if (widgetIds.isNotEmpty()) { val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply { setPackage(context.packageName); putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds) }; context.sendBroadcast(intent) } } }
}

class BalanceWidgetLarge : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { LargeWidgetContent() } }
    @Composable private fun LargeWidgetContent() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val cacheManager = WidgetCacheManager(prefs)
        val totalBalance = cacheManager.getWidgetBalance()
        val accounts = cacheManager.getWidgetAccounts()
        GlanceTheme {
            Box(modifier = GlanceModifier.fillMaxSize().background(Color(0xFF1E2329)).padding(12.dp).clickable(actionRunCallback<OpenAppCallback>())) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(text = "AI 余额监控", style = TextStyle(color = ColorProvider(Color(0xFFFCD535)), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Text(text = String.format("%.2f", totalBalance), style = TextStyle(color = ColorProvider(Color(0xFF0ECB81)), fontSize = 32.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = GlanceModifier.height(12.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        accounts.take(4).forEachIndexed { index, account ->
                            Column(modifier = GlanceModifier.defaultWeight().background(Color(0x1AFFFFFF)).cornerRadius(8.dp).padding(8.dp)) {
                                Text(text = account.name.take(8), style = TextStyle(color = ColorProvider(Color(0xFFEAECEF)), fontSize = 10.sp, fontWeight = FontWeight.Medium))
                                Text(text = String.format("%.2f", account.balance), style = TextStyle(color = ColorProvider(Color(0xFF0ECB81)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                            }
                            if (index < 3) Spacer(modifier = GlanceModifier.width(8.dp))
                        }
                    }
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(text = "7天趋势", style = TextStyle(color = ColorProvider(Color(0xFF707A8A)), fontSize = 10.sp))
                }
            }
        }
    }
    companion object { fun updateAllWidgets(context: Context) { val manager = AppWidgetManager.getInstance(context); val widgetIds = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetLargeReceiver::class.java)); if (widgetIds.isNotEmpty()) { val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply { setPackage(context.packageName); putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds) }; context.sendBroadcast(intent) } } }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BalanceWidgetMedium() }
class BalanceWidgetSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BalanceWidgetSmall() }
class BalanceWidgetMediumReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BalanceWidgetMedium() }
class BalanceWidgetLargeReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BalanceWidgetLarge() }

class OpenAppCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        context.startActivity(intent)
    }
}

class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<BalanceWorker>().build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("balance_refresh_immediate", androidx.work.ExistingWorkPolicy.KEEP, workRequest)
    }
}
