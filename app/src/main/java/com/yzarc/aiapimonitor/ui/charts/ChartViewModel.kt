package com.yzarc.aiapimonitor.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yzarc.aiapimonitor.data.repository.BalanceRepository
import com.yzarc.aiapimonitor.data.repository.MonthlySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class AccountConsumptionSummary(
    val displayName: String,
    val platform: String,
    val monthlyUsage: Double,
    val daysLeft: String
)

data class ChartUiState(
    val totalConsumption: Double = 0.0,
    val baseConsumption: Double = 0.0,
    val accountCount: Int = 0,
    val accountSummaries: List<AccountConsumptionSummary> = emptyList(),
    val monthData: MonthlySummary? = null,
    val monthLoading: Boolean = false,
    val loading: Boolean = true
)

@HiltViewModel
class ChartViewModel @Inject constructor(private val repo: BalanceRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            withContext(Dispatchers.IO) {
                val totalConsumption = repo.getTotalConsumption()
                val accountCount = repo.accountCount()
                val accounts = repo.getAccounts()

                // 各账号本月消费：通过快照差值计算
                val monthByAcct = repo.getMonthConsumptionByAccount()
                val summaries = accounts.map { acct ->
                    AccountConsumptionSummary(
                        displayName = acct.displayName,
                        platform = acct.platform,
                        monthlyUsage = monthByAcct[acct.id] ?: 0.0,
                        daysLeft = repo.estimateDaysLeft(acct.id)
                    )
                }

                _state.value = _state.value.copy(
                    totalConsumption = totalConsumption,
                    baseConsumption = repo.getBaseConsumption(),
                    accountCount = accountCount,
                    accountSummaries = summaries,
                    loading = false
                )
            }
        }
    }

    fun loadMonthData(year: Int, month: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(monthLoading = true)
            val summary = withContext(Dispatchers.IO) {
                repo.getMonthlyConsumption(year, month)
            }
            _state.value = _state.value.copy(monthData = summary, monthLoading = false)
        }
    }
}