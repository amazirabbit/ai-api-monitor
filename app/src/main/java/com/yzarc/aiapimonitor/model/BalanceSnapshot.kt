package com.yzarc.aiapimonitor.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 余额快照 — 唯一真实数据源（Single Source of Truth）
 *
 * 所有消费统计都来自本表的 Diff 计算：
 *   consumption = prev.totalBalance - current.totalBalance (当差值 > 0)
 *
 * 不再依赖 SharedPreferences 或 API 返回的消费字段。
 */
@Entity(tableName = "balance_snapshots")
data class BalanceSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "account_id") val accountId: Int,
    @ColumnInfo(name = "total_balance") val totalBalance: Double,
    @ColumnInfo(name = "available_balance", defaultValue = "0.0") val availableBalance: Double = 0.0,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long = System.currentTimeMillis()
)