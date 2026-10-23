package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.data.prefs.AppFontSize
import com.example.data.prefs.AppThemeMode

// 1. Dark Scheme (Pure AMOLED Indigo)
val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkAcc,
    onPrimary = Color(0xFF0E0E12),
    primaryContainer = Color(0xFF2E2750),
    onPrimaryContainer = Color(0xFFE4DCFF),
    secondary = DarkAcc,
    onSecondary = Color(0xFF0E0E12),
    background = DarkBg,
    onBackground = DarkTxt,
    surface = DarkSurf,
    onSurface = DarkTxt,
    surfaceVariant = DarkSurfVariant,
    onSurfaceVariant = DarkTxtMuted,
    outline = DarkBorder,
    outlineVariant = Color(0xFF21212A),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3E1616),
    onErrorContainer = Color(0xFFFFB4AB)
)

// 2. Light Scheme (Crisp modern violet)
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    background = Color(0xFFFBFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEDE8F5),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFCAC4D0),
    outlineVariant = Color(0xFFE2DFE8),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// 3. Carbon Scheme (True Black OLED Monochrome)
val CarbonColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFD4D4DF),
    onPrimary = Color(0xFF121216),
    primaryContainer = Color(0xFF282830),
    onPrimaryContainer = Color(0xFFF2F2F7),
    secondary = Color(0xFFA0A0AC),
    onSecondary = Color(0xFF121216),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEFEFEF),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFEFEFEF),
    surfaceVariant = Color(0xFF1C1C22),
    onSurfaceVariant = Color(0xFFA6A6B2),
    outline = Color(0xFF2C2C36),
    outlineVariant = Color(0xFF1C1C22),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3E1616),
    onErrorContainer = Color(0xFFFFB4AB)
)

// 4. Warm Scheme (Warm Espresso Amber)
val WarmColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF2C1600),
    primaryContainer = Color(0xFF4E2C00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFFFCC80),
    onSecondary = Color(0xFF2C1600),
    background = Color(0xFF171311),
    onBackground = Color(0xFFEFE8E1),
    surface = Color(0xFF201B18),
    onSurface = Color(0xFFEFE8E1),
    surfaceVariant = Color(0xFF2D2522),
    onSurfaceVariant = Color(0xFFB5A9A2),
    outline = Color(0xFF3F3530),
    outlineVariant = Color(0xFF29221F),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3E1616),
    onErrorContainer = Color(0xFFFFB4AB)
)

data class ThemePreviewColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color
)

fun getThemePreviewColors(mode: AppThemeMode, isDark: Boolean = true): ThemePreviewColors {
    return when (mode) {
        AppThemeMode.DYNAMIC -> {
            if (isDark) {
                ThemePreviewColors(Color(0xFF111318), Color(0xFF191C22), Color(0xFFE2E2E9), Color(0xFF8E9099), Color(0xFFA8C7FA))
            } else {
                ThemePreviewColors(Color(0xFFF8F9FF), Color(0xFFFFFFFF), Color(0xFF191C20), Color(0xFF73777F), Color(0xFF00639B))
            }
        }
        AppThemeMode.CARBON -> {
            ThemePreviewColors(Color(0xFF000000), Color(0xFF121214), Color(0xFFEFEFEF), Color(0xFFA6A6B2), Color(0xFFD4D4DF))
        }
        AppThemeMode.LIGHT -> {
            ThemePreviewColors(Color(0xFFFBFBFE), Color(0xFFFFFFFF), Color(0xFF1C1B1F), Color(0xFF79747E), Color(0xFF6750A4))
        }
        AppThemeMode.DARK -> {
            ThemePreviewColors(DarkBg, DarkSurf, DarkTxt, DarkTxtMuted, DarkAcc)
        }
        AppThemeMode.WARM -> {
            ThemePreviewColors(Color(0xFF171311), Color(0xFF201B18), Color(0xFFEFE8E1), Color(0xFFB5A9A2), Color(0xFFFFB74D))
        }
    }
}

@Composable
fun PromptChatTheme(
    themeMode: AppThemeMode = AppThemeMode.DYNAMIC,
    fontSize: AppFontSize = AppFontSize.MEDIUM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val colorScheme = when (themeMode) {
        AppThemeMode.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColorScheme else LightColorScheme
            }
        }
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.CARBON -> CarbonColorScheme
        AppThemeMode.WARM -> WarmColorScheme
    }

    val currentDensity = LocalDensity.current
    val adjustedDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * fontSize.scale
    )

    CompositionLocalProvider(LocalDensity provides adjustedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

