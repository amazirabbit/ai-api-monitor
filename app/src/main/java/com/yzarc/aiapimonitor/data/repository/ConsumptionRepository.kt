package com.yzarc.aiapimonitor.data.repository

import android.content.SharedPreferences
import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.data.db.SnapshotDao
import com.yzarc.aiapimonitor.model.BalanceSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 消费统计 Repository
 * 负责计算各种维度的消费数据（累计、月度、每日等）
 *
 * 数据源：仅来自 BalanceSnapshot 表（快照连续正差值和）
 */
class ConsumptionRepository(
    private val db: AppDatabase,
    private val prefs: SharedPreferences
) {
    private val snapshotDao: SnapshotDao = db.snapshotDao()
    private val accountRepo = AccountRepository(db)

    // ========================================================================
    // 初始基数（SharedPreferences，String 存储保精度）
    // 记录使用本 App 之前已花费的金额，与快照差值共同构成累计消费
    // ========================================================================

    fun getBaseConsumption(): Double {
        // 兼容旧数据（之前用 putFloat 存的）
        return try {
            prefs.getString("base_consumption", "0.0")?.toDoubleOrNull() ?: 0.0
        } catch (_: ClassCastException) {
            val old = prefs.getFloat("base_consumption", 0f).toDouble()
            // 迁移到新格式
            prefs.edit().putString("base_consumption", old.toString()).apply()
            old
        } catch (_: Exception) {
            0.0
        }
    }

    fun setBaseConsumption(value: Double) {
        prefs.edit().putString("base_consumption", value.toString()).apply()
    }

    // ========================================================================
    // 消费统计——仅由快照差值计算（Single Source of Truth）
    // ========================================================================

    /**
     * 计算正差值之和（快照必须已按 recorded_at ASC 排序）
     * 例：[100, 95, 93, 90] → (100-95)+(95-93)+(93-90) = 10
     */
    private fun sumPositiveDiffs(snaps: List<BalanceSnapshot>): Double {
        if (snaps.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until snaps.size) {
            val drop = snaps[i - 1].totalBalance - snaps[i].totalBalance
            if (drop > 0) total += drop
        }
        return total
    }

    /** 累计消费 = 初始基数 + 全部快照正差值和 */
    suspend fun getTotalConsumption(): Double {
        var total = getBaseConsumption()
        for (acct in accountRepo.getAccounts()) {
            total += sumPositiveDiffs(snapshotDao.getAll(acct.id))
        }
        return total
    }

    /** 本月消费 */
    suspend fun getMonthConsumption(): Double {
        val now = LocalDate.now()
        return getMonthConsumption(now.year, now.monthValue)
    }

    suspend fun getMonthConsumption(year: Int, month: Int): Double {
        val ym = YearMonth.of(year, month)
        val monthStart = ym.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val since = monthStart - 86400000L // 提前 1 天做基线
        return getConsumptionSince(since, monthStart)
    }

    /** 单个账号本月消费 */
    suspend fun getAccountMonthConsumption(accountId: Int): Double {
        val monthStart = LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val since = monthStart - 86400000L
        val snaps = snapshotDao.getSince(accountId, since)
        if (snaps.size < 2) return 0.0
        val startIdx = snaps.indexOfFirst { it.recordedAt >= monthStart }
        if (startIdx < 0) return 0.0
        val working = if (startIdx == 0) snaps else snaps.subList(startIdx - 1, snaps.size)
        return sumPositiveDiffs(working)
    }

    /** 今日消费 */
    suspend fun getTodayConsumption(): Double {
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val since = todayStart - 86400000L
        return getConsumptionSince(since, todayStart)
    }

    /** 各账号本月消费（Map<accountId, consumption>） */
    suspend fun getMonthConsumptionByAccount(): Map<Int, Double> {
        val monthStart = LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val since = monthStart - 86400000L
        val accounts = accountRepo.getAccounts()
        val result = mutableMapOf<Int, Double>()
        for (acct in accounts) {
            val snaps = snapshotDao.getSince(acct.id, since)
            if (snaps.size < 2) continue
            val startIdx = snaps.indexOfFirst { it.recordedAt >= monthStart }
            if (startIdx < 0) continue
            val working = if (startIdx == 0) snaps else snaps.subList(startIdx - 1, snaps.size)
            val consumed = sumPositiveDiffs(working)
            if (consumed > 0) result[acct.id] = consumed
        }
        return result
    }

    /**
     * 通用分段消费计算
     * @param dataSince 取数据的起始时间（含基线）
     * @param consumptionSince 实际统计起始时间
     */
    private suspend fun getConsumptionSince(dataSince: Long, consumptionSince: Long): Double {
        var total = 0.0
        for (acct in accountRepo.getAccounts()) {
            val snaps = snapshotDao.getSince(acct.id, dataSince)
            if (snaps.size < 2) continue
            val startIdx = snaps.indexOfFirst { it.recordedAt >= consumptionSince }
            if (startIdx < 0) continue
            val working = if (startIdx == 0) snaps else snaps.subList(startIdx - 1, snaps.size)
            total += sumPositiveDiffs(working)
        }
        return total
    }
}