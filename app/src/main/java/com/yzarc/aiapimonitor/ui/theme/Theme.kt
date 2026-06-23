package com.yzarc.aiapimonitor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================================
// Material You 暖色调主题 — 支持亮色/深色自动切换
// ============================================================================

// ——— 语义色（不随主题变化） ———
val SuccessGreen = Color(0xFF10B981)
val WarningYellow = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)
val NetworkOrange = Color(0xFFF97316)
val ParsePurple = Color(0xFFA855F7)

// ——— 平台品牌色（不随主题变化） ———
val OpenAiColor = Color(0xFF10A37F)
val DeepSeekColor = Color(0xFF4F46E5)
val OpenRouterColor = Color(0xFF6366F1)
val KimiColor = Color(0xFFE02040) // 月之暗面品牌红

// ——— 暖色调亮色方案（米白+暖棕，与展示页一致） ———
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF825500),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8D5C0),
    onPrimaryContainer = Color(0xFF3D2B14),
    secondary = Color(0xFF8B7A6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5EBE0),
    onSecondaryContainer = Color(0xFF2D2218),
    tertiary = Color(0xFF6B8F5C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4EABB),
    onTertiaryContainer = Color(0xFF102000),
    background = Color(0xFFFDF6ED),       // 暖米白背景
    onBackground = Color(0xFF2D2218),
    surface = Color(0xFFFFFBF5),          // 暖白卡片表面
    onSurface = Color(0xFF2D2218),
    surfaceVariant = Color(0xFFF0E6DA),   // 暖米色容器
    onSurfaceVariant = Color(0xFF5A4A3A),
    outline = Color(0xFF9C8F80),
    outlineVariant = Color(0xFFD3C4B4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// ——— 暖色调深色方案（暖灰+琥珀，与展示页一致） ———
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB951),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF654000),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFDDC3A5),
    onSecondary = Color(0xFF3E2D17),
    secondaryContainer = Color(0xFF56432B),
    onSecondaryContainer = Color(0xFFFADEBB),
    tertiary = Color(0xFFA5C98E),
    onTertiary = Color(0xFF21330E),
    tertiaryContainer = Color(0xFF384B28),
    onTertiaryContainer = Color(0xFFD4EABB),
    background = Color(0xFF1C1814),       // 暖深灰背景
    onBackground = Color(0xFFE6DCD3),
    surface = Color(0xFF241F1A),          // 暖灰卡片表面
    onSurface = Color(0xFFE6DCD3),
    surfaceVariant = Color(0xFF4A3F35),
    onSurfaceVariant = Color(0xFFCFC0B2),
    outline = Color(0xFF9C8F80),
    outlineVariant = Color(0xFF4A3F35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ——— 平台品牌色映射（非 Composable，纯色值） ———
fun platformColor(p: String): Color = when (p) {
    "openai" -> OpenAiColor
    "deepseek" -> DeepSeekColor
    "openrouter" -> OpenRouterColor
    "kimi" -> KimiColor
    else -> Color(0xFF825500)
}

// ——— Key 状态色映射（非 Composable，纯色值） ———
fun statusColor(keyStatus: Int): Color = when (keyStatus) {
    1 -> SuccessGreen
    2 -> DangerRed
    3 -> NetworkOrange
    4 -> ParsePurple
    else -> Color(0xFF817567)
}

fun statusBgColor(keyStatus: Int): Color = (when (keyStatus) {
    1 -> SuccessGreen
    2 -> DangerRed
    3 -> NetworkOrange
    4 -> ParsePurple
    else -> Color(0xFF817567)
}).copy(alpha = 0.15f)

// ——— 主题入口 ———
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // true=跟随壁纸; false=固定暖色调
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ 动态取色（Material You）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}