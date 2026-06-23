package com.yzarc.aiapimonitor.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yzarc.aiapimonitor.data.repository.ChartPoint
import com.yzarc.aiapimonitor.model.ApiAccount
import com.yzarc.aiapimonitor.model.BalanceInfo
import com.yzarc.aiapimonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddAccount: () -> Unit,
    onSettings: () -> Unit,
    onCharts: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var chartExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { viewModel.loadAll() }

    // 刷新按钮旋转动画
    val refreshAngle by animateFloatAsState(
        targetValue = if (state.loading) 360f else 0f,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "rotate"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI API Monitor", fontWeight = FontWeight.W600) },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshAll() },
                        enabled = !state.loading
                    ) {
                        Icon(
                            Icons.Default.Refresh, "刷新",
                            modifier = Modifier.rotate(refreshAngle)
                        )
                    }
                    IconButton(onClick = onCharts) {
                        Icon(Icons.Default.BarChart, "图表")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAccount, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "管理账号", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TotalBalanceCard(
                    totalCny = state.totalBalance,
                    totalConsumed = state.totalConsumption,
                    monthlyConsumed = state.monthConsumption,
                    dailyAvg = state.dailyAvg
                )
            }

            // —— 可折叠趋势/消费折线图 ——
            item {
                TrendMiniCard(
                    accounts = state.accounts,
                    accountTrends7 = state.accountTrends7,
                    accountTrends30 = state.accountTrends30,
                    accountConsumptionTrends = state.accountConsumptionTrends,
                    trendData7 = state.trendData7,
                    trendData30 = state.trendData30,
                    consumptionData7 = state.consumptionData7,
                    consumptionData = state.consumptionData,
                    expanded = chartExpanded,
                    onToggle = { chartExpanded = !chartExpanded }
                )
            }

            if (state.loading && state.balances.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (state.error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            state.error ?: "", color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // —— 异常账号告警 ——
            if (state.abnormalAccounts.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, "警告",
                                 tint = MaterialTheme.colorScheme.onErrorContainer,
                                 modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("${state.abnormalAccounts.size} 个账号异常",
                                     color = MaterialTheme.colorScheme.onErrorContainer,
                                     fontWeight = FontWeight.W600,
                                     style = MaterialTheme.typography.bodyMedium)
                                state.abnormalAccounts.forEach { acct ->
                                    Text("${acct.displayName}: ${acct.statusText}",
                                         color = MaterialTheme.colorScheme.onErrorContainer,
                                         style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // 余额列表（已在 ViewModel 中按余额从低到高排序）
            items(state.balances, key = { "balance_${it.accountId}" }) { balance ->
                val account = state.accounts.find { it.id == balance.accountId }
                val monthlyUsage = state.monthConsumptionByAccount[balance.accountId] ?: 0.0
                val daysLeft = state.daysLeftByAccount[balance.accountId] ?: "未知"
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
                ) {
                    BalanceCard(
                        balance = balance,
                        accountName = account?.displayName ?: "未知",
                        platform = account?.platform ?: "",
                        monthlyUsage = monthlyUsage,
                        daysLeft = daysLeft,
                        keyStatus = account?.keyStatus ?: 0
                    )
                }
            }

            if (state.accounts.isEmpty() && !state.loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Api, "API",
                                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("暂无账号, 点击 + 添加", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun TotalBalanceCard(
    totalCny: Double,
    totalConsumed: Double,
    monthlyConsumed: Double = 0.0,
    dailyAvg: Double = 0.0
) {
    val pct = (totalCny / 200.0).coerceIn(0.0, 1.0).toFloat()
    val barColor = when {
        totalCny < 20 -> MaterialTheme.colorScheme.error
        totalCny < 50 -> WarningYellow
        else -> SuccessGreen
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("总余额", color = MaterialTheme.colorScheme.onSurfaceVariant,
                         style = MaterialTheme.typography.labelLarge)
                    Text("¥${"%.2f".format(totalCny)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("累计消费", color = MaterialTheme.colorScheme.outline,
                         style = MaterialTheme.typography.bodySmall)
                    Text("¥${"%.2f".format(totalConsumed)}",
                        color = WarningYellow, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("日均消费", color = MaterialTheme.colorScheme.outline,
                         style = MaterialTheme.typography.bodySmall)
                    Text("¥${"%.1f".format(dailyAvg)}",
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         fontWeight = FontWeight.W600,
                         style = MaterialTheme.typography.titleSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("月消费", color = MaterialTheme.colorScheme.outline,
                         style = MaterialTheme.typography.bodySmall)
                    Text("¥${"%.0f".format(monthlyConsumed)}",
                         color = WarningYellow,
                         fontWeight = FontWeight.W600,
                         style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
fun BalanceCard(
    balance: BalanceInfo,
    accountName: String,
    platform: String,
    monthlyUsage: Double,
    daysLeft: String,
    keyStatus: Int
) {
    val color = platformColor(platform)
    val healthColor = when {
        balance.isCritical -> MaterialTheme.colorScheme.error
        balance.isLow -> WarningYellow
        else -> color
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            accountName.take(2), color = color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = healthColor,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(accountName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W600)
                    }
                    Text(
                        when (keyStatus) {
                            1 -> "✓ 正常"
                            2 -> "✗ 失效"
                            3 -> "⚠ 网络异常"
                            4 -> "? 解析异常"
                            else -> "未检测"
                        },
                        color = statusColor(keyStatus),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        balance.format(),
                        color = when {
                            balance.isCritical -> MaterialTheme.colorScheme.error
                            balance.isLow -> WarningYellow
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Text(balance.currency, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("本月消费", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${"%.2f".format(monthlyUsage)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("可用天数", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                    Text(daysLeft, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("更新", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                    Text(balance.timeAgo, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ====================================================================
// 可折叠趋势迷你折线图
// ====================================================================

@Composable
private fun TrendMiniCard(
    accounts: List<ApiAccount>,
    accountTrends7: Map<Int, List<ChartPoint>>,
    accountTrends30: Map<Int, List<ChartPoint>>,
    accountConsumptionTrends: Map<Int, List<ChartPoint>>,
    trendData7: List<ChartPoint>,
    trendData30: List<ChartPoint>,
    consumptionData7: List<ChartPoint>,
    consumptionData: List<ChartPoint>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    var rangeDays by remember { mutableIntStateOf(7) }
    var modeTab by remember { mutableIntStateOf(0) }
    var tabIndex by remember { mutableIntStateOf(0) }
    var selAccountId by remember { mutableIntStateOf(accounts.firstOrNull()?.id ?: 0) }
    LaunchedEffect(accounts) {
        if (selAccountId == 0 || accounts.none { it.id == selAccountId })
            selAccountId = accounts.firstOrNull()?.id ?: 0
    }

    val isBalance = modeTab == 0
    val lineColor = if (isBalance) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.tertiary
    val trendData = if (rangeDays == 7) trendData7 else trendData30
    val accountTrends = if (rangeDays == 7) accountTrends7 else accountTrends30
    val showData = if (tabIndex == 0) {
        if (isBalance) trendData
        else if (rangeDays == 7) consumptionData7
        else consumptionData
    } else {
        val map = if (isBalance) accountTrends else accountConsumptionTrends
        map[selAccountId] ?: emptyList()
    }
    val showAccounts = accounts.filter {
        val map = if (isBalance) accountTrends else accountConsumptionTrends
        map.containsKey(it.id)
    }

    val fillColor = lineColor.copy(alpha = 0.08f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dotBg = MaterialTheme.colorScheme.surface

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.TrendingUp, "趋势",
                 tint = lineColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (tabIndex == 0) (if (isBalance) "余额趋势" else "消费趋势")
                else (accounts.find { it.id == selAccountId }?.displayName ?: "账号"),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.W600,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore, if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }

        AnimatedVisibility(visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()) {
            Column {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    listOf(7 to "7天", 30 to "30天").forEach { (days, label) ->
                        Text(label, color = if (rangeDays == days) lineColor else MaterialTheme.colorScheme.outline,
                             fontWeight = if (rangeDays == days) FontWeight.W600 else FontWeight.Normal,
                             style = MaterialTheme.typography.bodySmall,
                             modifier = Modifier.clickable { rangeDays = days }
                                 .padding(end = 16.dp, bottom = 4.dp))
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    listOf("余额", "消费").forEachIndexed { i, label ->
                        Text(label, color = if (modeTab == i) lineColor else MaterialTheme.colorScheme.outline,
                             fontWeight = if (modeTab == i) FontWeight.W600 else FontWeight.Normal,
                             style = MaterialTheme.typography.bodySmall,
                             modifier = Modifier.clickable { modeTab = i }
                                 .padding(end = 16.dp, bottom = 4.dp))
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    Text("汇总", color = if (tabIndex == 0) lineColor else MaterialTheme.colorScheme.outline,
                         fontWeight = if (tabIndex == 0) FontWeight.W600 else FontWeight.Normal,
                         style = MaterialTheme.typography.bodySmall,
                         modifier = Modifier.clickable { tabIndex = 0 }
                             .padding(end = 16.dp, bottom = 6.dp))
                    if (showAccounts.isNotEmpty()) {
                        Text("按账号", color = if (tabIndex == 1) lineColor else MaterialTheme.colorScheme.outline,
                             fontWeight = if (tabIndex == 1) FontWeight.W600 else FontWeight.Normal,
                             style = MaterialTheme.typography.bodySmall,
                             modifier = Modifier.clickable { tabIndex = 1 }
                                 .padding(bottom = 6.dp))
                    }
                }
                if (tabIndex == 1 && showAccounts.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                        showAccounts.forEach { acct ->
                            Surface(
                                color = if (acct.id == selAccountId) platformColor(acct.platform).copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.clickable { selAccountId = acct.id }
                                    .padding(end = 6.dp, bottom = 6.dp)) {
                                Text(acct.displayName.take(3),
                                     color = if (acct.id == selAccountId) platformColor(acct.platform)
                                             else MaterialTheme.colorScheme.onSurfaceVariant,
                                     style = MaterialTheme.typography.bodySmall,
                                     fontWeight = if (acct.id == selAccountId) FontWeight.W600 else FontWeight.Normal,
                                     modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
                val d = showData
                if (d.size >= 2) {
                    val vals = d.map { it.value }
                    Row(Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        MiniStat("峰值", "¥${"%.2f".format(vals.max())}", lineColor)
                        MiniStat("均值", "¥${"%.2f".format(vals.average())}",
                            MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                InteractiveMiniChart(
                    data = d,
                    lineColor = lineColor,
                    fillColor = fillColor,
                    gridColor = gridColor,
                    dotBg = dotBg
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.outline,
             style = MaterialTheme.typography.bodySmall)
        Text(value, color = color, fontWeight = FontWeight.W600,
             style = MaterialTheme.typography.titleSmall)
    }
}