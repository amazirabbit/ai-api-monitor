package com.yzarc.aiapimonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yzarc.aiapimonitor.data.api.BalanceFetcher
import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.data.repository.BalanceRepository
import com.yzarc.aiapimonitor.ui.account.AccountListScreen
import com.yzarc.aiapimonitor.ui.account.AddAccountScreen
import com.yzarc.aiapimonitor.ui.charts.ChartScreen
import com.yzarc.aiapimonitor.ui.charts.ChartViewModel
import com.yzarc.aiapimonitor.ui.home.HomeScreen
import com.yzarc.aiapimonitor.ui.home.HomeViewModel
import com.yzarc.aiapimonitor.ui.settings.SettingsScreen
import com.yzarc.aiapimonitor.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: BalanceRepository
    @Inject lateinit var database: AppDatabase
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户已决定, 无论结果都继续 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        setContent {
            AppTheme {
                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = hiltViewModel()

                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onAddAccount = { navController.navigate("account_list") },
                            onSettings = { navController.navigate("settings") },
                            onCharts = { navController.navigate("charts") }
                        )
                    }

                    composable("add_account") {
                        val scope = rememberCoroutineScope()
                        AddAccountScreen(
                            onSave = { account ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.saveAccount(account)
                                    }
                                    Toast.makeText(this@MainActivity, "添加成功, 正在获取余额...", Toast.LENGTH_SHORT).show()
                                    homeViewModel.refreshAll()
                                    navController.popBackStack()
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("edit_account/{accountId}") { backStackEntry ->
                        val scope = rememberCoroutineScope()
                        val accountId = backStackEntry.arguments?.getString("accountId")?.toIntOrNull() ?: return@composable
                        var account by remember { mutableStateOf<com.yzarc.aiapimonitor.model.ApiAccount?>(null) }
                        var loading by remember { mutableStateOf(true) }
                        LaunchedEffect(accountId) {
                            account = withContext(Dispatchers.IO) { repository.getAccount(accountId) }
                            loading = false
                        }
                        if (loading) return@composable
                        if (account == null) {
                            navController.popBackStack(); return@composable
                        }
                        AddAccountScreen(
                            existingAccount = account,
                            onSave = { updated ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { repository.updateAccount(updated) }
                                    Toast.makeText(this@MainActivity, "已更新", Toast.LENGTH_SHORT).show()
                                    homeViewModel.loadAll()
                                    navController.popBackStack()
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("account_list") {
                        val scope = rememberCoroutineScope()
                        val accounts by produceState<List<com.yzarc.aiapimonitor.model.ApiAccount>>(
                            initialValue = emptyList()
                        ) {
                            value = withContext(Dispatchers.IO) { repository.getAccounts() }
                        }
                        AccountListScreen(
                            accounts = accounts,
                            onAdd = { navController.navigate("add_account") },
                            onEdit = { navController.navigate("edit_account/${it.id}") },
                            onDelete = { account ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { repository.deleteAccount(account) }
                                    homeViewModel.loadAll()
                                    navController.popBackStack()
                                }
                            },
                            onCheckKey = { account ->
                                scope.launch {
                                    val (valid, errorType) = withContext(Dispatchers.IO) {
                                        BalanceFetcher().checkKey(account)
                                    }
                                    val status = when {
                                        valid -> 1
                                        errorType == "auth" -> 2
                                        errorType == "network" -> 3
                                        else -> 4
                                    }
                                    withContext(Dispatchers.IO) {
                                        database.accountDao().updateKeyStatus(account.id, status, errorType)
                                    }
                                    homeViewModel.loadAll()
                                    val msg = when (status) {
                                        1 -> "Key 有效 ✓"
                                        2 -> "Key 无效: 认证失败"
                                        3 -> "网络异常: 请检查网络"
                                        4 -> "解析异常: 响应格式错误"
                                        else -> "未知状态"
                                    }
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        val scope = rememberCoroutineScope()
                        SettingsScreen(
                            baseConsumption = repository.getBaseConsumption(),
                            onSaveBaseConsumption = { v ->
                                repository.setBaseConsumption(v)
                                homeViewModel.loadAll()
                                Toast.makeText(this@MainActivity, "已保存", Toast.LENGTH_SHORT).show()
                            },
                            onBack = { navController.popBackStack() },
                            onDeleteAll = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.getAccounts().forEach { repository.deleteAccount(it) }
                                    }
                                    homeViewModel.loadAll()
                                    Toast.makeText(this@MainActivity, "数据已清除", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    composable("charts") {
                        val chartViewModel: ChartViewModel = hiltViewModel()
                        ChartScreen(
                            viewModel = chartViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}