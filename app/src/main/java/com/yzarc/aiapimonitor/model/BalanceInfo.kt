package com.yzarc.aiapimonitor.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "balances")
data class BalanceInfo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "account_id") val accountId: Int,
    @ColumnInfo(name = "total_balance") val totalBalance: Double = 0.0,
    val currency: String = "USD",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    fun format(): String { val sym = if (currency == "CNY") "¥" else "$"; return "$sym${"%.2f".format(totalBalance)}" }
    val timeAgo: String get() { val ms = System.currentTimeMillis() - updatedAt; val sec = ms / 1000; val min = sec / 60; return when { sec < 10 -> "刚刚"; sec < 60 -> "${sec}秒前"; min < 60 -> "${min}分钟前"; min < 1440 -> "${min / 60}小时${min % 60}分钟前"; else -> "${min / 1440}天前" } }
    val updateTimeStr: String get() { val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()); return sdf.format(java.util.Date(updatedAt)) }
    val isLow: Boolean get() = totalBalance < 35
    val isWarning: Boolean get() = totalBalance in 35.0..70.0
    val isCritical: Boolean get() = totalBalance < 7
}

data class TotalBalance(val totalCny: Double, val balances: List<BalanceInfo>, val updatedAt: Long = System.currentTimeMillis()) { val formatted: String get() = "¥${"%.2f".format(totalCny)}" }
