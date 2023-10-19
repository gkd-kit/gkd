package li.songe.gkd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow

val LightColorScheme = lightColorScheme()
val DarkColorScheme = darkColorScheme()

@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val enableDarkTheme by storeFlow.map(scope) { s -> s.enableDarkTheme }.collectAsState()
    val colorScheme = if (enableDarkTheme ?: useDarkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme, content = content
    )
}