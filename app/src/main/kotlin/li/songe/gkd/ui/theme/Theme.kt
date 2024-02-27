package li.songe.gkd.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateToastStyle

val LightColorScheme = lightColorScheme()
val DarkColorScheme = darkColorScheme()

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    // https://developer.android.com/jetpack/compose/designsystems/material3?hl=zh-cn
    val context = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val enableDarkTheme by storeFlow.map(scope) { s -> s.enableDarkTheme }.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = enableDarkTheme ?: systemInDarkTheme
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
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