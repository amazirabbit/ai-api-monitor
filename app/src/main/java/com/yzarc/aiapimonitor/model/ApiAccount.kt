package com.yzarc.aiapimonitor.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class ApiAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val platform: String,
    val name: String,
    @ColumnInfo(name = "api_key") val apiKey: String = "",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    /**
     * 0=未检测, 1=正常, 2=Key无效, 3=网络异常, 4=解析异常
     */
    @ColumnInfo(name = "key_status") val keyStatus: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        val platforms = listOf("openai", "deepseek", "openrouter", "kimi")
        val platformNames = mapOf(
            "openai" to "OpenAI",
            "deepseek" to "DeepSeek",
            "openrouter" to "OpenRouter",
            "kimi" to "Kimi"
        )
    }
    val displayName: String get() = if (name.isNotBlank()) name
        else (platformNames[platform] ?: platform)
    val statusText: String get() = when (keyStatus) {
        1 -> "正常"
        2 -> "Key失效"
        3 -> "网络异常"
        4 -> "解析异常"
        else -> "未检测"
    }
}