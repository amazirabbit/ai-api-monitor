package com.yzarc.aiapimonitor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yzarc.aiapimonitor.data.repository.BalanceRepository
import com.yzarc.aiapimonitor.data.repository.ChartPoint
import com.yzarc.aiapimonitor.model.ApiAccount
import com.yzarc.aiapimonitor.model.BalanceInfo
import com.yzarc.aiapimonitor.model.TotalBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeState(
    val accounts: List<ApiAccount> = emptyList(),
    val balances: List<BalanceInfo> = emptyList(),
    val totalBalance: Double = 0.0,
    val totalConsumption: Double = 0.0,
    val monthConsumption: Double = 0.0,
    val monthConsumptionByAccount: Map<Int, Double> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val trendData7: List<ChartPoint> = emptyList(),
    val trendData30: List<ChartPoint> = emptyList(),
    val consumptionData: List<ChartPoint> = emptyList(),
    val consumptionData7: List<ChartPoint> = emptyList(),
    val accountTrends7: Map<Int, List<ChartPoint>> = emptyMap(),
    val accountTrends30: Map<Int, List<ChartPoint>> = emptyMap(),
    val accountConsumptionTrends: Map<Int, List<ChartPoint>> = emptyMap(),
    val daysLeftByAccount: Map<Int, String> = emptyMap()
) {
    val abnormalAccounts: List<ApiAccount> get() = accounts.filter { it.keyStatus != 1 }

    val dailyAvg: Double get() {
        val now = java.time.LocalDate.now()
        return if (now.dayOfMonth > 0 && totalConsumption > 0)
            totalConsumption / now.dayOfMonth else 0.0
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: BalanceRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var refreshing = false

    init {
        // 响应式监听：快照变化时自动重算
        viewModelScope.launch {
            repo.observeAllSnapshots().collect {
                if (!refreshing) loadAll() // 后台刷新完成时自动更新 UI
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val accounts = repo.getAccounts()
                val total = repo.getTotalBalance()
                val consumed = repo.getTotalConsumption()
                val monthConsumed = repo.getMonthConsumption()
                val monthByAcct = repo.getMonthConsumptionByAccount()

                val daysLeft = accounts.associate { acct ->
                    acct.id to withContext(Dispatchers.IO) { repo.estimateDaysLeft(acct.id) }
                }

                val trend7 = withContext(Dispatchers.IO) { repo.get7DayTrend() }
                val trend30 = withContext(Dispatchers.IO) { repo.get30DayTrend() }
                val consumption7 = withContext(Dispatchers.IO) { repo.get7DayConsumption() }
                val consumption30 = withContext(Dispatchers.IO) { repo.get30DayConsumption() }
                val acctrends7 = withContext(Dispatchers.IO) {
                    accounts.associate { it.id to repo.getAccount7DayTrend(it.id) }
                }
                val acctrends30 = withContext(Dispatchers.IO) {
                    accounts.associate { it.id to repo.getAccount30DayTrend(it.id) }
                }
                val accconsumption = withContext(Dispatchers.IO) {
                    accounts.associate { it.id to repo.getAccount30DayConsumption(it.id) }
                }

                _state.value = _state.value.copy(
                    accounts = accounts,
                    balances = total.balances,
                    totalBalance = total.totalCny,
                    totalConsumption = consumed,
                    monthConsumption = monthConsumed,
                    monthConsumptionByAccount = monthByAcct,
                    trendData7 = trend7,
                    trendData30 = trend30,
                    consumptionData = consumption30,
                    consumptionData7 = consumption7,
                    accountTrends7 = acctrends7,
                    accountTrends30 = acctrends30,
                    accountConsumptionTrends = accconsumption,
                    daysLeftByAccount = daysLeft,
                    loading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    fun refreshAll() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val balances = repo.refreshAllBalances()
                val accounts = repo.getAccounts()
                val total = repo.getTotalBalance()
                val consumed = repo.getTotalConsumption()
                val monthConsumed = repo.getMonthConsumption()
                val monthByAcct = repo.getMonthConsumptionByAccount()

                val daysLeft = accounts.associate { acct ->
                    acct.id to withContext(Dispatchers.IO) { repo.estimateDaysLeft(acct.id) }
                }

                val trend7 = withContext(Dispatchers.IO) { repo.get7DayTrend() }
                val trend30 = withContext(Dispatchers.IO) { repo.get30DayTrend() }
                val consumption7 = withContext(Dispatchers.IO) { repo.get7DayConsumption() }
                val consumption30 = withContext(Dispatchers.IO) { repo.get30DayConsumption() }
                val acctrends7 = withContext(Dispatchers.IO) {
                    accounts.associate { it.id to repo.getAccount7DayTrend(it.id) }
                }
                val acctrends30 = withContext(Dispatchers.IO) {
                    accounts.associate { it.id to repo.getAccount30DayTrend(it.id) }
                }
                val accconsumption = withContext(Dispatchers.IO) {
                    accounts.associate { it.id to repo.getAccount30DayConsumption(it.id) }
                }

                _state.value = _state.value.copy(
                    accounts = accounts,
                    balances = balances,
                    totalBalance = total.totalCny,
                    totalConsumption = consumed,
                    monthConsumption = monthConsumed,
                    monthConsumptionByAccount = monthByAcct,
                    trendData7 = trend7,
                    trendData30 = trend30,
                    consumptionData = consumption30,
                    consumptionData7 = consumption7,
                    accountTrends7 = acctrends7,
                    accountTrends30 = acctrends30,
                    accountConsumptionTrends = accconsumption,
                    daysLeftByAccount = daysLeft,
                    loading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "刷新失败: ${e.message}"
                )
            } finally {
                refreshing = false
            }
        }
    }
}