package com.yzarc.aiapimonitor.data.repository

import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.data.db.SnapshotDao
import com.yzarc.aiapimonitor.model.BalanceSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ChartRepository(private val db: AppDatabase) {
    private val snapshotDao: SnapshotDao = db.snapshotDao()
    private val balanceDao = db.balanceDao()
    private val accountRepo = AccountRepository(db)

    data class ChartPoint(val label: String, val value: Double)
    data class PlatformStat(val name: String, val platform: String, val balance: Double)
    data class MonthlySummary(val total: Double, val accounts: Map<String, Double>)

    private suspend fun getTrend(days: Int): List<ChartPoint> {
        val now = LocalDate.now()
        val since = now.minusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val allSnaps = snapshotDao.getAllSince(since)
        val points = mutableListOf<ChartPoint>()
        for (i in days - 1 downTo 0) {
            val day = now.minusDays(i.toLong())
            val dayEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            val snaps = allSnaps.filter { it.recordedAt <= dayEnd }
            val latestByAccount = snaps.groupBy { it.accountId }.mapValues { it.value.last() }.values
            val total = latestByAccount.sumOf { it.totalBalance }
            points.add(ChartPoint("\${day.monthValue}/\${day.dayOfMonth}", maxOf(0.0, total)))
        }
        return points
    }
    suspend fun get7DayTrend() = getTrend(7)
    suspend fun get30DayTrend() = getTrend(30)

    private suspend fun getAccountTrend(accountId: Int, days: Int): List<ChartPoint> {
        val now = LocalDate.now()
        val since = now.minusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val allSnaps = snapshotDao.getSince(accountId, since)
        val points = mutableListOf<ChartPoint>()
        for (i in days - 1 downTo 0) {
            val day = now.minusDays(i.toLong())
            val dayEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            val snaps = allSnaps.filter { it.recordedAt <= dayEnd }
            val balance = if (snaps.isNotEmpty()) snaps.last().totalBalance else 0.0
            points.add(ChartPoint("\${day.monthValue}/\${day.dayOfMonth}", maxOf(0.0, balance)))
        }
        return points
    }
    suspend fun getAccount7DayTrend(accountId: Int) = getAccountTrend(accountId, 7)
    suspend fun getAccount30DayTrend(accountId: Int) = getAccountTrend(accountId, 30)

    suspend fun get30DayConsumption(): List<ChartPoint> {
        val now = LocalDate.now()
        val since = now.minusDays(31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val allSnaps = snapshotDao.getAllSince(since)
        val accounts = accountRepo.getAccounts()
        val accountSnaps = accounts.associate { acct -> acct.id to allSnaps.filter { it.accountId == acct.id }.sortedBy { it.recordedAt } }
        val result = mutableListOf<ChartPoint>()
        for (i in 29 downTo 0) {
            val day = now.minusDays(i.toLong())
            val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            var dayTotal = 0.0
            for ((acctId, snaps) in accountSnaps) {
                val daySnaps = snaps.filter { it.recordedAt in dayStart..dayEnd }
                if (daySnaps.size >= 2) { val drop = daySnaps.first().totalBalance - daySnaps.last().totalBalance; if (drop > 0) dayTotal += drop }
                else if (daySnaps.size == 1) { val prevSnap = snaps.lastOrNull { it.recordedAt < dayStart }; if (prevSnap != null) { val drop = prevSnap.totalBalance - daySnaps.last().totalBalance; if (drop > 0) dayTotal += drop } }
            }
            result.add(ChartPoint("\${day.monthValue}/\${day.dayOfMonth}", dayTotal))
        }
        return result
    }
    suspend fun get7DayConsumption(): List<ChartPoint> = get30DayConsumption().takeLast(7)

    suspend fun getAccount30DayConsumption(accountId: Int): List<ChartPoint> {
        val now = LocalDate.now()
        val since = now.minusDays(31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val snaps = snapshotDao.getSince(accountId, since).sortedBy { it.recordedAt }
        val result = mutableListOf<ChartPoint>()
        for (i in 29 downTo 0) {
            val day = now.minusDays(i.toLong())
            val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            val daySnaps = snaps.filter { it.recordedAt in dayStart..dayEnd }
            val drop = if (daySnaps.size >= 2) maxOf(0.0, daySnaps.first().totalBalance - daySnaps.last().totalBalance)
            else if (daySnaps.size == 1) { val prev = snaps.lastOrNull { it.recordedAt < dayStart }; if (prev != null) maxOf(0.0, prev.totalBalance - daySnaps.last().totalBalance) else 0.0 } else 0.0
            result.add(ChartPoint("\${day.monthValue}/\${day.dayOfMonth}", drop))
        }
        return result
    }

    suspend fun getMonthlyConsumption(year: Int, month: Int): MonthlySummary {
        val ym = YearMonth.of(year, month)
        val monthStart = ym.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthEnd = ym.atEndOfMonth().atTime(23, 59, 59, 999000000).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val since = monthStart - 259200000L
        val accounts = accountRepo.getAccounts()
        val accountTotals = mutableMapOf<String, Double>()
        var grandTotal = 0.0
        for (acct in accounts) {
            val snaps = snapshotDao.getSince(acct.id, since).filter { it.recordedAt <= monthEnd }
            if (snaps.size < 2) continue
            val startIdx = snaps.indexOfFirst { it.recordedAt >= monthStart }
            if (startIdx < 0) continue
            val working = if (startIdx == 0) snaps else snaps.subList(startIdx - 1, snaps.size)
            val consumed = sumPositiveDiffs(working)
            if (consumed > 0) { accountTotals[acct.displayName] = consumed; grandTotal += consumed }
        }
        return MonthlySummary(grandTotal, accountTotals)
    }

    suspend fun getPlatformBreakdown(): List<PlatformStat> {
        val balances = balanceDao.getAll()
        val accounts = accountRepo.getAccounts()
        return accounts.mapNotNull { account -> val balance = balances.find { it.accountId == account.id }?.totalBalance ?: return@mapNotNull null; PlatformStat(account.displayName, account.platform, balance) }.sortedByDescending { it.balance }
    }

    private fun sumPositiveDiffs(snaps: List<BalanceSnapshot>): Double {
        if (snaps.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until snaps.size) { val drop = snaps[i - 1].totalBalance - snaps[i].totalBalance; if (drop > 0) total += drop }
        return total
    }
}
