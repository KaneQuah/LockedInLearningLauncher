package com.lockedinlearning.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ---------------------------------------------------------------------------
// Design tokens from UX doc
// ---------------------------------------------------------------------------
val Primary      = Color(0xFF4A6CF7)
val CorrectGreen = Color(0xFF22C55E)
val IncorrectRed = Color(0xFFEF4444)
val PenaltyOrange= Color(0xFFF97316)
val LockoutPurple= Color(0xFF8B5CF6)
val Background   = Color(0xFFFFFFFF)
val Surface      = Color(0xFFF5F5F5)
val TextPrimary  = Color(0xFF111827)
val TextSecondary= Color(0xFF6B7280)

private val LightColorScheme = lightColorScheme(
    primary        = Primary,
    background     = Background,
    surface        = Surface,
    onBackground   = TextPrimary,
    onSurface      = TextPrimary,
    error          = IncorrectRed,
)

private val DarkColorScheme = darkColorScheme(
    primary  = Primary,
)

@Composable
fun LockedInLearningTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
