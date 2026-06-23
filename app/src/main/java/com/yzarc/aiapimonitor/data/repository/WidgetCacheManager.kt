package com.yzarc.aiapimonitor.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Widget 缓存管理器
 * 负责桌面小组件数据的读写（SharedPreferences）
 *
 * 存储格式：
 * - widget_balance: String (总余额)
 * - widget_update_time: Long (更新时间戳)
 * - widget_accounts: String (JSON 数组，包含各账号余额信息)
 */
class WidgetCacheManager(private val prefs: SharedPreferences) {

    private val gson = Gson()

    /**
     * 账号余额数据（用于 Widget 展示）
     */
    data class WidgetAccount(
        val id: Int,
        val name: String,
        val platform: String,
        val balance: Double,
        val currency: String = "USD"
    )

    fun getWidgetBalance(): Double {
        return try {
            prefs.getString("widget_balance", "0.0")?.toDoubleOrNull() ?: 0.0
        } catch (_: ClassCastException) {
            val old = prefs.getFloat("widget_balance", 0f).toDouble()
            prefs.edit().putString("widget_balance", old.toString()).apply()
            old
        }
    }

    fun getWidgetUpdateTime(): Long = prefs.getLong("widget_update_time", 0L)

    /**
     * 获取各账号余额列表
     */
    fun getWidgetAccounts(): List<WidgetAccount> {
        val json = prefs.getString("widget_accounts", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WidgetAccount>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存 Widget 数据（总余额 + 各账号余额）
     */
    fun saveWidgetData(balance: Double, accounts: List<WidgetAccount> = emptyList()) {
        prefs.edit()
            .putString("widget_balance", balance.toString())
            .putLong("widget_update_time", System.currentTimeMillis())
            .apply()

        if (accounts.isNotEmpty()) {
            prefs.edit()
                .putString("widget_accounts", gson.toJson(accounts))
                .apply()
        }
    }

    /**
     * 仅保存总余额（向后兼容）
     */
    fun saveWidgetBalance(balance: Double) {
        prefs.edit()
            .putString("widget_balance", balance.toString())
            .putLong("widget_update_time", System.currentTimeMillis())
            .apply()
    }

    /**
     * 清除 Widget 缓存
     */
    fun clearWidgetData() {
        prefs.edit()
            .remove("widget_balance")
            .remove("widget_update_time")
            .remove("widget_accounts")
            .apply()
    }
}