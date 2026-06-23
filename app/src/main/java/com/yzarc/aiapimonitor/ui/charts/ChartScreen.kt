package com.yzarc.aiapimonitor.ui.charts

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yzarc.aiapimonitor.model.ApiAccount
import com.yzarc.aiapimonitor.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(viewModel: ChartViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadAll() }

    val dayOfMonth = LocalDate.now().dayOfMonth
    val dailyAvg = if (dayOfMonth > 0 && state.totalConsumption > 0)
        state.totalConsumption / dayOfMonth else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消费总览", fontWeight = FontWeight.W600) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            // === 总消费大卡片 ===
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("累计消费（全部）",
                         color = MaterialTheme.colorScheme.outline,
                         style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("¥${"%.2f".format(state.totalConsumption)}",
                         color = MaterialTheme.colorScheme.primary,
                         fontWeight = FontWeight.Bold,
                         style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("含基数 ¥${"%.2f".format(state.baseConsumption)} · ${state.accountCount} 个账号",
                         color = MaterialTheme.colorScheme.outline,
                         style = MaterialTheme.typography.bodySmall)
                }
            }

            // === 统计行 ===
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                     modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ShoppingCart, "日均",
                             tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("日均", color = MaterialTheme.colorScheme.outline,
                             style = MaterialTheme.typography.bodySmall)
                        Text("¥${"%.1f".format(dailyAvg)}",
                             color = MaterialTheme.colorScheme.onSurface,
                             fontWeight = FontWeight.W600,
                             style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            // === 按月查询（可折叠）===
            var monthExpanded by remember { mutableStateOf(false) }
            var selYear by remember { mutableIntStateOf(LocalDate.now().year) }
            var selMonth by remember { mutableIntStateOf(LocalDate.now().monthValue) }

            LaunchedEffect(monthExpanded) {
                if (monthExpanded && state.monthData == null) {
                    viewModel.loadMonthData(selYear, selMonth)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { monthExpanded = !monthExpanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DateRange, "月份",
                         tint = MaterialTheme.colorScheme.primary,
                         modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("按月份查询", fontWeight = FontWeight.W600,
                         color = MaterialTheme.colorScheme.onSurface,
                         style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ExpandMore, if (monthExpanded) "收起" else "展开",
                         tint = MaterialTheme.colorScheme.outline,
                         modifier = Modifier.size(20.dp)
                             .rotate(if (monthExpanded) 180f else 0f))
                }

                AnimatedVisibility(visible = monthExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = {
                                if (selMonth == 1) { selYear--; selMonth = 12 }
                                else selMonth--
                                viewModel.loadMonthData(selYear, selMonth)
                            }) { Icon(Icons.Default.KeyboardArrowLeft, "上月") }
                            Spacer(Modifier.width(16.dp))
                            Text("${selYear}年${selMonth}月",
                                 fontWeight = FontWeight.W600,
                                 style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(16.dp))
                            IconButton(onClick = {
                                if (selMonth == 12) { selYear++; selMonth = 1 }
                                else selMonth++
                                viewModel.loadMonthData(selYear, selMonth)
                            }) { Icon(Icons.Default.KeyboardArrowRight, "下月") }
                        }

                        if (state.monthLoading) {
                            Box(Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            val md = state.monthData
                            if (md != null && md.total > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text("本月消费",
                                     color = MaterialTheme.colorScheme.outline,
                                     style = MaterialTheme.typography.bodySmall)
                                Text("¥${"%.2f".format(md.total)}",
                                     color = MaterialTheme.colorScheme.primary,
                                     fontWeight = FontWeight.Bold,
                                     style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(12.dp))
                                md.accounts.entries.forEach { (name, amount) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, color = MaterialTheme.colorScheme.onSurface,
                                             style = MaterialTheme.typography.bodyMedium,
                                             modifier = Modifier.weight(1f))
                                        Text("¥${"%.2f".format(amount)}",
                                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                                             style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else if (md != null) {
                                Spacer(Modifier.height(8.dp))
                                Text("该月暂无消费数据",
                                     color = MaterialTheme.colorScheme.outline,
                                     style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // === 各平台消费 ===
            val platformTotals = remember(state.accountSummaries) {
                state.accountSummaries.groupBy {
                    ApiAccount.platformNames[it.platform] ?: it.platform
                }.mapValues { (_, v) -> v.sumOf { it.monthlyUsage } }
            }
            if (platformTotals.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("各平台消费", fontWeight = FontWeight.W600,
                             color = MaterialTheme.colorScheme.onSurface,
                             style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        platformTotals.forEach { (platform, amount) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(platform, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                     style = MaterialTheme.typography.bodySmall,
                                     modifier = Modifier.weight(1f))
                                Text("¥${"%.2f".format(amount)}",
                                     color = MaterialTheme.colorScheme.onSurface,
                                     fontWeight = FontWeight.W600,
                                     style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // === 各账号明细 ===
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("各账号消费明细", fontWeight = FontWeight.W600,
                         color = MaterialTheme.colorScheme.onSurface,
                         style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    state.accountSummaries.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(s.displayName, color = MaterialTheme.colorScheme.onSurface,
                                 fontWeight = FontWeight.W500,
                                 style = MaterialTheme.typography.bodyMedium,
                                 modifier = Modifier.weight(1f))
                            Text("¥${"%.2f".format(s.monthlyUsage)}",
                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}