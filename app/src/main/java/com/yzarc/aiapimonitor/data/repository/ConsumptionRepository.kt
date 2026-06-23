package com.yzarc.aiapimonitor.data.repository

import android.content.SharedPreferences
import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.data.db.SnapshotDao
import com.yzarc.aiapimonitor.model.BalanceSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ConsumptionRepository(private val db: AppDatabase, private val prefs: SharedPreferences) {
    private val snapshotDao: SnapshotDao = db.snapshotDao()
    private val accountRepo = AccountRepository(db)

    fun getBaseConsumption(): Double { return try { prefs.getString("base_consumption", "0.0")?.toDoubleOrNull() ?: 0.0 } catch (_: ClassCastException) { prefs.getFloat("base_consumption", 0f).toDouble() } catch (_: Exception) { 0.0 } }
    fun setBaseConsumption(value: Double) { prefs.edit().putString("base_consumption", value.toString()).apply() }

    private fun sumPositiveDiffs(snaps: List<BalanceSnapshot>): Double {
        if (snaps.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until snaps.size) { val drop = snaps[i - 1].totalBalance - snaps[i].totalBalance; if (drop > 0) total += drop }
        return total
    }

    suspend fun getTotalConsumption(): Double { var total = getBaseConsumption(); for (acct in accountRepo.getAccounts()) { total += sumPositiveDiffs(snapshotDao.getAll(acct.id)) }; return total }
    suspend fun getMonthConsumption(): Double { val now = LocalDate.now(); return getMonthConsumption(now.year, now.monthValue) }
    suspend fun getMonthConsumption(year: Int, month: Int): Double { val ym = YearMonth.of(year, month); val monthStart = ym.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); val since = monthStart - 86400000L; return getConsumptionSince(since, monthStart) }
    suspend fun getAccountMonthConsumption(accountId: Int): Double { val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); val since = monthStart - 86400000L; val snaps = snapshotDao.getSince(accountId, since); if (snaps.size < 2) return 0.0; val startIdx = snaps.indexOfFirst { it.recordedAt >= monthStart }; if (startIdx < 0) return 0.0; val working = if (startIdx == 0) snaps else snaps.subList(startIdx - 1, snaps.size); return sumPositiveDiffs(working) }
    suspend fun getTodayConsumption(): Double { val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); val since = todayStart - 86400000L; return getConsumptionSince(since, todayStart) }
    suspend fun getMonthConsumptionByAccount(): Map<Int, Double> { val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); val since = monthStart - 86400000L; val accounts = accountRepo.getAccounts(); val result = mutableMapOf<Int, Double>(); for (acct in accounts) { val snaps = snapshotDao.getSince(acct.id, since); if (snaps.size < 2) continue; val startIdx = snaps.indexOfFirst { it.recordedAt >= monthStart }; if (startIdx < 0) continue; val working = if (startIdx == 0) snaps else snaps.subList(startIdx - 1, snaps.size); val consumed = sumPositiveDiffs(working); if (consumed > 0) result[acct.id] = consumed }; return result }
    private suspend fun getConsumptionSince(dataSince: Long, consumptionSince: Long): Double { var total = 0.0; for (acct in accountRepo.getAccounts()) { val snaps = snapshotDao.getSince(acct.id, dataSince); if (snaps.size < 2) continue; val startIdx = snaps.indexOfFirst { it.recordedAt >= consumptionSince }; if (startIdx < 0) continue; val working = if (startIdx == 0) snaps else snaps.subList(startIdx - 1, snaps.size); total += sumPositiveDiffs(working) }; return total }
}
