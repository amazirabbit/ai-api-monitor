package com.yzarc.aiapimonitor.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.yzarc.aiapimonitor.data.api.AuthException
import com.yzarc.aiapimonitor.data.api.BalanceFetcher
import com.yzarc.aiapimonitor.data.api.NetworkException
import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.data.db.SnapshotDao
import com.yzarc.aiapimonitor.model.ApiAccount
import com.yzarc.aiapimonitor.model.BalanceInfo
import com.yzarc.aiapimonitor.model.BalanceSnapshot
import com.yzarc.aiapimonitor.model.TotalBalance
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

typealias ChartPoint = ChartRepository.ChartPoint
typealias PlatformStat = ChartRepository.PlatformStat
typealias MonthlySummary = ChartRepository.MonthlySummary

class BalanceRepository(private val db: AppDatabase, private val prefs: SharedPreferences) {
    private val fetcher = BalanceFetcher()
    private val balanceDao = db.balanceDao()
    internal val snapshotDao: SnapshotDao = db.snapshotDao()
    private val accountRepo = AccountRepository(db)
    val consumptionRepo = ConsumptionRepository(db, prefs)
    val chartRepo = ChartRepository(db)
    val widgetCacheManager = WidgetCacheManager(prefs)

    fun getWidgetBalance(): Double = widgetCacheManager.getWidgetBalance()
    fun getWidgetUpdateTime(): Long = widgetCacheManager.getWidgetUpdateTime()
    fun getBaseConsumption(): Double = consumptionRepo.getBaseConsumption()
    fun setBaseConsumption(value: Double) = consumptionRepo.setBaseConsumption(value)
    suspend fun getTotalConsumption(): Double = consumptionRepo.getTotalConsumption()
    suspend fun getMonthConsumption(): Double = consumptionRepo.getMonthConsumption()
    suspend fun getMonthConsumption(year: Int, month: Int): Double = consumptionRepo.getMonthConsumption(year, month)
    suspend fun getAccountMonthConsumption(accountId: Int): Double = consumptionRepo.getAccountMonthConsumption(accountId)
    suspend fun getTodayConsumption(): Double = consumptionRepo.getTodayConsumption()
    suspend fun getMonthConsumptionByAccount(): Map<Int, Double> = consumptionRepo.getMonthConsumptionByAccount()
    suspend fun get7DayTrend(): List<ChartPoint> = chartRepo.get7DayTrend()
    suspend fun get30DayTrend(): List<ChartPoint> = chartRepo.get30DayTrend()
    suspend fun getAccount7DayTrend(accountId: Int): List<ChartPoint> = chartRepo.getAccount7DayTrend(accountId)
    suspend fun getAccount30DayTrend(accountId: Int): List<ChartPoint> = chartRepo.getAccount30DayTrend(accountId)
    suspend fun get30DayConsumption(): List<ChartPoint> = chartRepo.get30DayConsumption()
    suspend fun get7DayConsumption(): List<ChartPoint> = chartRepo.get7DayConsumption()
    suspend fun getAccount30DayConsumption(accountId: Int): List<ChartPoint> = chartRepo.getAccount30DayConsumption(accountId)
    suspend fun getMonthlyConsumption(year: Int, month: Int): MonthlySummary = chartRepo.getMonthlyConsumption(year, month)
    suspend fun getPlatformBreakdown(): List<PlatformStat> = chartRepo.getPlatformBreakdown()
    fun observeAllSnapshots(): Flow<List<BalanceSnapshot>> = snapshotDao.observeAll()

    suspend fun getAccounts(): List<ApiAccount> {
        val accounts = accountRepo.getAccounts()
        val allBalances = balanceDao.getAll()
        return accounts.sortedBy { account -> allBalances.find { it.accountId == account.id }?.totalBalance ?: Double.MAX_VALUE }
    }
    suspend fun getAccount(id: Int) = accountRepo.getAccount(id)
    suspend fun saveAccount(account: ApiAccount) = accountRepo.save(account)
    suspend fun updateAccount(account: ApiAccount) = accountRepo.update(account)
    suspend fun deleteAccount(account: ApiAccount) { balanceDao.deleteByAccountId(account.id); snapshotDao.deleteByAccountId(account.id); accountRepo.delete(account) }
    suspend fun accountCount() = accountRepo.count()

    suspend fun refreshBalance(account: ApiAccount): BalanceInfo {
        val result = fetcher.fetch(account)
        accountRepo.updateKeyStatus(account.id, 1)
        snapshotDao.insert(BalanceSnapshot(accountId = account.id, totalBalance = result.totalBalance, availableBalance = result.totalBalance, recordedAt = result.updatedAt))
        val existing = balanceDao.getByAccountId(account.id)
        if (existing != null) { balanceDao.update(account.id, result.totalBalance, result.updatedAt) }
        else { balanceDao.upsert(BalanceInfo(accountId = account.id, totalBalance = result.totalBalance, currency = result.currency, updatedAt = result.updatedAt)) }
        Log.d("BalanceRepo", "snapshot saved: balance=\${result.totalBalance}")
        return balanceDao.getByAccountId(account.id) ?: BalanceInfo(accountId = account.id, totalBalance = result.totalBalance)
    }

    suspend fun refreshAllBalances(): List<BalanceInfo> {
        val accounts = accountRepo.getAccounts().filter { it.isActive }
        val results = mutableListOf<BalanceInfo>()
        coroutineScope {
            accounts.map { account -> async {
                try { val info = refreshBalance(account); synchronized(results) { results.add(info) } }
                catch (e: AuthException) { accountRepo.updateKeyStatus(account.id, 2); balanceDao.getByAccountId(account.id)?.let { synchronized(results) { results.add(it) } } }
                catch (e: NetworkException) { accountRepo.updateKeyStatus(account.id, 3); balanceDao.getByAccountId(account.id)?.let { synchronized(results) { results.add(it) } } }
                catch (e: Exception) { accountRepo.updateKeyStatus(account.id, 3); balanceDao.getByAccountId(account.id)?.let { synchronized(results) { results.add(it) } } }
            } }.forEach { it.await() }
        }
        val total = results.sumOf { it.totalBalance }
        widgetCacheManager.saveWidgetData(total)
        cleanOldSnapshots(180)
        return results
    }

    suspend fun getTotalBalance(): TotalBalance { val balances = balanceDao.getAll(); return TotalBalance(totalCny = balances.sumOf { it.totalBalance }, balances = balances) }

    suspend fun estimateDaysLeft(accountId: Int): String {
        val balance = balanceDao.getByAccountId(accountId) ?: return "未知"
        if (balance.totalBalance <= 0) return "已耗尽"
        val monthlyUsage = getAccountMonthConsumption(accountId)
        val now = java.time.LocalDate.now()
        val dailyAvg = if (now.dayOfMonth > 1 && monthlyUsage > 0) monthlyUsage / (now.dayOfMonth - 1) else 0.0
        if (dailyAvg <= 0) return "> 30天"
        val days = (balance.totalBalance / dailyAvg).toInt()
        return when { days <= 0 -> "< 1天"; days > 365 -> "> 1年"; else -> "约 days 天" }
    }

    suspend fun cleanOldSnapshots(daysToKeep: Int = 180) { val cutoff = System.currentTimeMillis() - daysToKeep * 24L * 3600 * 1000; snapshotDao.deleteOlderThan(cutoff); Log.d("BalanceRepo", "cleaned old snapshots") }
}
