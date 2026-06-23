package com.yzarc.aiapimonitor.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "balance_snapshots")
data class BalanceSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "account_id") val accountId: Int,
    @ColumnInfo(name = "total_balance") val totalBalance: Double,
    @ColumnInfo(name = "available_balance", defaultValue = "0.0") val availableBalance: Double = 0.0,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long = System.currentTimeMillis()
)
