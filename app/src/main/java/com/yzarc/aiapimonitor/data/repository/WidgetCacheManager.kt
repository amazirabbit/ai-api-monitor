package com.yzarc.aiapimonitor.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WidgetCacheManager(private val prefs: SharedPreferences) {
    private val gson = Gson()
    data class WidgetAccount(val id: Int, val name: String, val platform: String, val balance: Double, val currency: String = "USD")

    fun getWidgetBalance(): Double { return try { prefs.getString("widget_balance", "0.0")?.toDoubleOrNull() ?: 0.0 } catch (_: ClassCastException) { prefs.getFloat("widget_balance", 0f).toDouble() } }
    fun getWidgetUpdateTime(): Long = prefs.getLong("widget_update_time", 0L)
    fun getWidgetAccounts(): List<WidgetAccount> { val json = prefs.getString("widget_accounts", null) ?: return emptyList(); return try { val type = object : TypeToken<List<WidgetAccount>>() {}.type; gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() } }
    fun saveWidgetData(balance: Double, accounts: List<WidgetAccount> = emptyList()) { prefs.edit().putString("widget_balance", balance.toString()).putLong("widget_update_time", System.currentTimeMillis()).apply(); if (accounts.isNotEmpty()) { prefs.edit().putString("widget_accounts", gson.toJson(accounts)).apply() } }
    fun saveWidgetBalance(balance: Double) { prefs.edit().putString("widget_balance", balance.toString()).putLong("widget_update_time", System.currentTimeMillis()).apply() }
    fun clearWidgetData() { prefs.edit().remove("widget_balance").remove("widget_update_time").remove("widget_accounts").apply() }
}
