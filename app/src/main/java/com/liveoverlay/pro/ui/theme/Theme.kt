package com.liveoverlay.pro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val ProColorScheme = darkColorScheme(
    primary = BlueElectric,
    secondary = BlueGlow,
    tertiary = WhiteSoft,
    background = BgDark,
    surface = CardDark
)

@Composable
fun LiveOverlayProTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BgDark.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = ProColorScheme,
        content = content
    )
}
