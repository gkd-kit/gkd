package li.songe.gkd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import li.songe.gkd.MainActivity
import li.songe.gkd.util.updateToastStyle

val LightColorScheme = lightColorScheme()
val DarkColorScheme = darkColorScheme()
val supportDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    // https://developer.android.com/jetpack/compose/designsystems/material3?hl=zh-cn
    val context = LocalContext.current as MainActivity
    val enableDarkTheme by context.mainVm.enableDarkThemeFlow.collectAsState()
    val enableDynamicColor by context.mainVm.enableDynamicColorFlow.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = enableDarkTheme ?: systemInDarkTheme
    val colorScheme = when {
        supportDynamicColor && enableDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        supportDynamicColor && enableDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // https://github.com/gkd-kit/gkd/pull/421
    LaunchedEffect(darkTheme) {
        WindowInsetsControllerCompat(context.window, context.window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
        }
        updateToastStyle(darkTheme)
    }
    MaterialTheme(
        colorScheme = colorScheme, content = content
    )
}