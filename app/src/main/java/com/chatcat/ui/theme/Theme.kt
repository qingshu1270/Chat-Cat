package com.chatcat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 当前是否深色（跟随应用内主题设置，而不是系统） */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** 色值取自 ref/chat-demo/src/app/theme.css 的 token */
object Tokens {
    val Blue500 = Color(0xFF3964FE)
    val Blue450 = Color(0xFF5686FE)
    val Blue400 = Color(0xFF679EFE)
    val Blue50 = Color(0xFFEDF3FE)
    val BubbleLight = Color(0xFFEDF0FA)
    val PillBg = Color(0xFFEEF0FE)
    val PillText = Color(0xFF4F5F9E)
    val GrayText = Color(0xFF8C8C8C)
    val Placeholder = Color(0xFF9E9E9E)
    val Border = Color(0xFFECECEC)

    val BgLight = Color(0xFFFFFFFF)
    val BgLightSecondary = Color(0xFFF9FAFB)
    val BgDark = Color(0xFF151517)
    val BubbleDark = Color(0xFF2C2C2E)

    val TextLight = Color(0xFF1A1A1A)
    val TextDark = Color(0xFFF5F5F5)
    val TextSecondaryLight = Color(0xFF6B6B6B)
    val TextSecondaryDark = Color(0xFFA8A8A8)
    val BorderLight = Color(0x14000000)
    val BorderDark = Color(0x1FFFFFFF)
}

private val LightScheme = lightColorScheme(
    primary = Tokens.Blue500,
    onPrimary = Color.White,
    background = Tokens.BgLight,
    onBackground = Tokens.TextLight,
    surface = Tokens.BgLight,
    onSurface = Tokens.TextLight,
    surfaceVariant = Tokens.BgLightSecondary,
    onSurfaceVariant = Tokens.TextSecondaryLight,
    outline = Tokens.BorderLight,
)

private val DarkScheme = darkColorScheme(
    primary = Tokens.Blue450,
    onPrimary = Color.White,
    background = Tokens.BgDark,
    onBackground = Tokens.TextDark,
    surface = Tokens.BgDark,
    onSurface = Tokens.TextDark,
    surfaceVariant = Tokens.BubbleDark,
    onSurfaceVariant = Tokens.TextSecondaryDark,
    outline = Tokens.BorderDark,
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun ChatCatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scaledDensity = remember(density, fontScale) {
        Density(density.density, density.fontScale * fontScale)
    }
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            // 整体偏圆角的形状基线：按钮 / 卡片 / 弹窗 / 输入框统一收口
            shapes = AppShapes,
            content = content,
        )
    }
}
