package com.yzarc.aiapimonitor.service

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

/**
 * 桌面余额小部件 - Glance API 实现
 *
 * 设计风格：Binance 深色主题
 * - 深色背景：#1e2329
 * - 主色调：#fcd535（黄色）
 * - 交易绿：#0ecb81（正常）
 * - 交易红：#f6465d（危险）
 * - 文字：#eaecef（主文字）、#707a8a（次要文字）
 *
 * 支持三种尺寸：
 * - BalanceWidgetSmall (2x1): 只显示总余额 + 健康指示灯
 * - BalanceWidgetMedium (4x2): 总余额 + 各账号余额列表（默认）
 * - BalanceWidgetLarge (4x3): 总余额 + 健康度 + 账号网格 + 趋势
 */

// ============================================================
// 2x1 小组件 - 只显示总余额 + 健康指示灯
// ============================================================
class BalanceWidgetSmall : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            SmallWidgetContent()
        }
    }

    @Composable
    private fun SmallWidgetContent() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val cacheManager = WidgetCacheManager(prefs)

        val totalBalance = cacheManager.getWidgetBalance()
        val updateTime = cacheManager.getWidgetUpdateTime()

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E2329))
                    .padding(8.dp)
                    .clickable(actionRunCallback<OpenAppCallback>())
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：总余额
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "总余额",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF707A8A)),
                                fontSize = 10.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "¥",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF707A8A)),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = formatSmallBalance(totalBalance),
                                style = TextStyle(
                                    color = ColorProvider(getBalanceColor(totalBalance)),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = formatUpdateTimeShort(updateTime),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF707A8A)),
                                fontSize = 9.sp
                            )
                        )
                    }

                    // 右侧：健康指示灯
                    Box(
                        modifier = GlanceModifier
                            .size(10.dp)
                            .background(getHealthColor(totalBalance))
                            .cornerRadius(5.dp),
                        content = {}
                    )
                }
            }
        }
    }

    private fun formatSmallBalance(balance: Double): String {
        return if (balance >= 10000) {
            "%.1f".format(balance / 1000) + "k"
        } else if (balance >= 1000) {
            "%.1f".format(balance)
        } else {
            "%.2f".format(balance)
        }
    }

    private fun formatUpdateTimeShort(updateTime: Long): String {
        if (updateTime == 0L) return "等待刷新"

        val now = System.currentTimeMillis()
        val diff = now - updateTime
        val minutes = diff / 60000

        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 1440 -> "${minutes / 60}小时前"
            else -> "超过1天"
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetSmallReceiver::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    setPackage(context.packageName)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

// ============================================================
// 4x2 中小组件 - 总余额 + 各账号余额列表（默认）
// ============================================================
class BalanceWidgetMedium : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            MediumWidgetContent()
        }
    }

    @Composable
    private fun MediumWidgetContent() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val cacheManager = WidgetCacheManager(prefs)

        val totalBalance = cacheManager.getWidgetBalance()
        val updateTime = cacheManager.getWidgetUpdateTime()
        val accounts = cacheManager.getWidgetAccounts()

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E2329))
                    .padding(12.dp)
                    .clickable(actionRunCallback<OpenAppCallback>())
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 标题栏
                    HeaderRow(updateTime)

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // 总余额
                    TotalBalanceSection(totalBalance)

                    Spacer(modifier = GlanceModifier.height(12.dp))

                    // 各账号余额列表
                    AccountListSection(accounts)
                }
            }
        }
    }

    @Composable
    private fun HeaderRow(updateTime: Long) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AI 余额监控",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFCD535)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // 刷新按钮
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(Color(0x33FCD535))
                    .cornerRadius(14.dp)
                    .clickable(actionRunCallback<RefreshCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFCD535)),
                        fontSize = 14.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        // 更新时间
        Text(
            text = formatUpdateTime(updateTime),
            style = TextStyle(
                color = ColorProvider(Color(0xFF707A8A)),
                fontSize = 10.sp
            )
        )
    }

    @Composable
    private fun TotalBalanceSection(balance: Double) {
        val color = getBalanceColor(balance)

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "¥",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF707A8A)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = formatBalance(balance),
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun AccountListSection(accounts: List<WidgetCacheManager.WidgetAccount>) {
        if (accounts.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无账号数据",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF707A8A)),
                        fontSize = 11.sp
                    )
                )
            }
            return
        }

        // 显示最多4个账号（4x2 空间限制）
        val displayAccounts = accounts.take(4)

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            displayAccounts.forEach { account ->
                AccountRow(account)
                if (account != displayAccounts.last()) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            }

            // 如果有更多账号
            if (accounts.size > 4) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "还有 ${accounts.size - 4} 个账号...",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF707A8A)),
                        fontSize = 9.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun AccountRow(account: WidgetCacheManager.WidgetAccount) {
        val platformColor = getPlatformColor(account.platform)
        val balanceColor = when {
            account.balance <= 0 -> Color(0xFFF6465D)
            account.balance < 10 -> Color(0xFFF6465D)
            account.balance < 30 -> Color(0xFFF59E0B)
            else -> Color(0xFF0ECB81)
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(Color(0x1AFFFFFF))
                .cornerRadius(6.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 平台颜色指示器
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(platformColor)
                    .cornerRadius(4.dp),
                content = {}
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            // 账号名称
            Text(
                text = truncateName(account.name),
                style = TextStyle(
                    color = ColorProvider(Color(0xFFEAECEF)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // 余额
            Text(
                text = formatAccountBalance(account),
                style = TextStyle(
                    color = ColorProvider(balanceColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetMediumReceiver::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    setPackage(context.packageName)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

// ============================================================
// 4x3 大小组件 - 总余额 + 健康度 + 账号网格 + 趋势
// ============================================================
class BalanceWidgetLarge : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            LargeWidgetContent()
        }
    }

    @Composable
    private fun LargeWidgetContent() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val cacheManager = WidgetCacheManager(prefs)

        val totalBalance = cacheManager.getWidgetBalance()
        val updateTime = cacheManager.getWidgetUpdateTime()
        val accounts = cacheManager.getWidgetAccounts()

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E2329))
                    .padding(12.dp)
                    .clickable(actionRunCallback<OpenAppCallback>())
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 顶部：标题 + 更新时间 + 刷新按钮
                    LargeHeaderRow(updateTime)

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    // 总余额 + 健康度
                    LargeBalanceSection(totalBalance, accounts)

                    Spacer(modifier = GlanceModifier.height(12.dp))

                    // 2x2 账号网格
                    AccountGridSection(accounts)

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // 底部趋势提示
                    TrendSection(totalBalance)
                }
            }
        }
    }

    @Composable
    private fun LargeHeaderRow(updateTime: Long) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AI 余额监控",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFCD535)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            Text(
                text = formatUpdateTime(updateTime),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF707A8A)),
                    fontSize = 10.sp
                )
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            // 刷新按钮
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(Color(0x33FCD535))
                    .cornerRadius(14.dp)
                    .clickable(actionRunCallback<RefreshCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFCD535)),
                        fontSize = 14.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun LargeBalanceSection(totalBalance: Double, accounts: List<WidgetCacheManager.WidgetAccount>) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：总余额
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "总余额",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF707A8A)),
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF707A8A)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = formatBalance(totalBalance),
                        style = TextStyle(
                            color = ColorProvider(getBalanceColor(totalBalance)),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // 右侧：健康度指示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "健康度",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF707A8A)),
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Box(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .background(getHealthColor(totalBalance))
                        .cornerRadius(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getHealthEmoji(totalBalance),
                        style = TextStyle(
                            fontSize = 20.sp
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun AccountGridSection(accounts: List<WidgetCacheManager.WidgetAccount>) {
        if (accounts.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无账号数据",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF707A8A)),
                        fontSize = 12.sp
                    )
                )
            }
            return
        }

        // 显示最多4个账号（2x2 网格）
        val displayAccounts = accounts.take(4)

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // 第一行
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                if (displayAccounts.isNotEmpty()) {
                    AccountGridItem(displayAccounts[0], modifier = GlanceModifier.defaultWeight())
                    if (displayAccounts.size > 1) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        AccountGridItem(displayAccounts[1], modifier = GlanceModifier.defaultWeight())
                    } else {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 第二行
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                if (displayAccounts.size > 2) {
                    AccountGridItem(displayAccounts[2], modifier = GlanceModifier.defaultWeight())
                    if (displayAccounts.size > 3) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        AccountGridItem(displayAccounts[3], modifier = GlanceModifier.defaultWeight())
                    } else {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                } else {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
        }

        // 如果有更多账号
        if (accounts.size > 4) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "还有 ${accounts.size - 4} 个账号...",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF707A8A)),
                    fontSize = 9.sp
                )
            )
        }
    }

    @Composable
    private fun AccountGridItem(account: WidgetCacheManager.WidgetAccount, modifier: GlanceModifier = GlanceModifier) {
        val platformColor = getPlatformColor(account.platform)
        val balanceColor = when {
            account.balance <= 0 -> Color(0xFFF6465D)
            account.balance < 10 -> Color(0xFFF6465D)
            account.balance < 30 -> Color(0xFFF59E0B)
            else -> Color(0xFF0ECB81)
        }

        Column(
            modifier = modifier
                .background(Color(0x1AFFFFFF))
                .cornerRadius(8.dp)
                .padding(8.dp)
        ) {
            // 平台名称 + 颜色指示
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(6.dp)
                        .background(platformColor)
                        .cornerRadius(3.dp),
                    content = {}
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = truncateName(account.name, 8),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFEAECEF)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 余额
            Text(
                text = formatAccountBalance(account),
                style = TextStyle(
                    color = ColorProvider(balanceColor),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 进度条（简化版）
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0x33FFFFFF))
                    .cornerRadius(1.5.dp)
            ) {
                val progress = when {
                    account.balance <= 0 -> 0f
                    account.balance >= 100 -> 100
                    else -> account.balance.toInt()
                }
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(balanceColor)
                        .cornerRadius(1.5.dp),
                    content = {}
                )
            }
        }
    }

    @Composable
    private fun TrendSection(totalBalance: Double) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(Color(0x1AFFFFFF))
                .cornerRadius(6.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "7天趋势",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF707A8A)),
                    fontSize = 10.sp
                )
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            // 简化趋势指示
            val trendColor = if (totalBalance >= 50) Color(0xFF0ECB81) else Color(0xFFF6465D)
            val trendText = if (totalBalance >= 50) "↑ 稳定" else "↓ 偏低"

            Text(
                text = trendText,
                style = TextStyle(
                    color = ColorProvider(trendColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetLargeReceiver::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    setPackage(context.packageName)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

// ============================================================
// 原有 Widget（保留兼容性）
// ============================================================
class BalanceWidgetProvider : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val cacheManager = WidgetCacheManager(prefs)

        val totalBalance = cacheManager.getWidgetBalance()
        val updateTime = cacheManager.getWidgetUpdateTime()
        val accounts = cacheManager.getWidgetAccounts()

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E2329))
                    .padding(12.dp)
                    .clickable(actionRunCallback<OpenAppCallback>())
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 标题栏
                    HeaderRow(updateTime)

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // 总余额
                    TotalBalanceSection(totalBalance)

                    Spacer(modifier = GlanceModifier.height(12.dp))

                    // 各账号余额列表
                    AccountListSection(accounts)
                }
            }
        }
    }

    @Composable
    private fun HeaderRow(updateTime: Long) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AI 余额监控",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFCD535)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // 刷新按钮
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(Color(0x33FCD535))
                    .cornerRadius(14.dp)
                    .clickable(actionRunCallback<RefreshCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFCD535)),
                        fontSize = 14.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        // 更新时间
        Text(
            text = formatUpdateTime(updateTime),
            style = TextStyle(
                color = ColorProvider(Color(0xFF707A8A)),
                fontSize = 10.sp
            )
        )
    }

    @Composable
    private fun TotalBalanceSection(balance: Double) {
        val color = when {
            balance <= 0 -> Color(0xFFF6465D)
            balance < 20 -> Color(0xFFF6465D)
            balance < 50 -> Color(0xFFF59E0B)
            else -> Color(0xFF0ECB81)
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "¥",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF707A8A)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = formatBalance(balance),
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun AccountListSection(accounts: List<WidgetCacheManager.WidgetAccount>) {
        if (accounts.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无账号数据",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF707A8A)),
                        fontSize = 11.sp
                    )
                )
            }
            return
        }

        // 显示最多4个账号（4x2 空间限制）
        val displayAccounts = accounts.take(4)

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            displayAccounts.forEach { account ->
                AccountRow(account)
                if (account != displayAccounts.last()) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            }

            // 如果有更多账号
            if (accounts.size > 4) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "还有 ${accounts.size - 4} 个账号...",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF707A8A)),
                        fontSize = 9.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun AccountRow(account: WidgetCacheManager.WidgetAccount) {
        val platformColor = getPlatformColor(account.platform)
        val balanceColor = when {
            account.balance <= 0 -> Color(0xFFF6465D)
            account.balance < 10 -> Color(0xFFF6465D)
            account.balance < 30 -> Color(0xFFF59E0B)
            else -> Color(0xFF0ECB81)
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(Color(0x1AFFFFFF))
                .cornerRadius(6.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 平台颜色指示器
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(platformColor)
                    .cornerRadius(4.dp),
                content = {}
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            // 账号名称
            Text(
                text = truncateName(account.name),
                style = TextStyle(
                    color = ColorProvider(Color(0xFFEAECEF)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // 余额
            Text(
                text = formatAccountBalance(account),
                style = TextStyle(
                    color = ColorProvider(balanceColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    companion object {
        /**
         * 更新所有 Widget 实例
         *
         * Glance widget 会在每次 recomposition 时自动读取 SharedPreferences，
         * 通过发送 APPWIDGET_UPDATE 广播触发系统重新渲染。
         */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetReceiver::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                // 发送 APPWIDGET_UPDATE 广播触发 Glance 重新渲染
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    setPackage(context.packageName)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

// ============================================================
// 通用辅助函数
// ============================================================

private fun formatBalance(balance: Double): String {
    return if (balance >= 10000) {
        "%.1f".format(balance / 1000) + "k"
    } else {
        "%.2f".format(balance)
    }
}

private fun formatAccountBalance(account: WidgetCacheManager.WidgetAccount): String {
    val symbol = if (account.currency == "CNY") "¥" else "$"
    return if (account.balance >= 10000) {
        "$symbol${"%.1f".format(account.balance / 1000)}k"
    } else {
        "$symbol${"%.2f".format(account.balance)}"
    }
}

private fun truncateName(name: String, maxLength: Int = 10): String {
    return if (name.length > maxLength) {
        name.take(maxLength) + "..."
    } else {
        name
    }
}

private fun formatUpdateTime(updateTime: Long): String {
    if (updateTime == 0L) return "等待首次刷新"

    val now = System.currentTimeMillis()
    val diff = now - updateTime
    val minutes = diff / 60000

    return when {
        minutes < 1 -> "刚刚更新"
        minutes < 60 -> "${minutes}分钟前更新"
        minutes < 1440 -> "${minutes / 60}小时前更新"
        else -> "超过1天前更新"
    }
}

private fun getBalanceColor(balance: Double): Color {
    return when {
        balance <= 0 -> Color(0xFFF6465D)
        balance < 20 -> Color(0xFFF6465D)
        balance < 50 -> Color(0xFFF59E0B)
        else -> Color(0xFF0ECB81)
    }
}

private fun getHealthColor(balance: Double): Color {
    return when {
        balance <= 0 -> Color(0xFFF6465D)
        balance < 20 -> Color(0xFFF6465D)
        balance < 50 -> Color(0xFFF59E0B)
        else -> Color(0xFF0ECB81)
    }
}

private fun getHealthEmoji(balance: Double): String {
    return when {
        balance <= 0 -> "!"
        balance < 20 -> "!"
        balance < 50 -> "~"
        else -> "✓"
    }
}

private fun getPlatformColor(platform: String): Color {
    return when (platform.lowercase()) {
        "openai" -> Color(0xFF10A37F)
        "deepseek" -> Color(0xFF4D6BFE)
        "openrouter" -> Color(0xFFE04E60)
        "kimi" -> Color(0xFF6236FF)
        else -> Color(0xFF707A8A)
    }
}

// ============================================================
// Widget Receivers
// ============================================================

/**
 * Glance Widget Receiver（原有，保留兼容性）
 */
class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidgetProvider()
}

/**
 * 2x1 小组件 Receiver
 */
class BalanceWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidgetSmall()
}

/**
 * 4x2 中小组件 Receiver
 */
class BalanceWidgetMediumReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidgetMedium()
}

/**
 * 4x3 大小组件 Receiver
 */
class BalanceWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidgetLarge()
}

/**
 * 打开 App 的回调
 */
class OpenAppCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}

/**
 * 刷新回调
 */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // 触发 BalanceWorker 立即执行
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<BalanceWorker>()
            .build()
        androidx.work.WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "balance_refresh_immediate",
                androidx.work.ExistingWorkPolicy.KEEP,
                workRequest
            )
    }
}