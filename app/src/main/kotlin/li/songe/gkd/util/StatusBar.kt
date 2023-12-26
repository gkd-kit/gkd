package li.songe.gkd.util

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun StatusBar() {
    val scope = rememberCoroutineScope()
    val enableDarkTheme by storeFlow.map(scope) { s -> s.enableDarkTheme }.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = enableDarkTheme ?: systemInDarkTheme
    val current = LocalContext.current
    LaunchedEffect(isDarkTheme) {
        (current as? Activity)?.window?.let { window ->
            val controllerCompat = WindowInsetsControllerCompat(window, window.decorView)
            controllerCompat.isAppearanceLightStatusBars = !isDarkTheme
        }
    }
}
