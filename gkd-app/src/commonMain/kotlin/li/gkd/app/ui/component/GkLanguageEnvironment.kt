package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

private val LocalResourceLocale = staticCompositionLocalOf { Locale.ROOT }

/**
 * Compose Resources reads the platform locale without observing it. A static local invalidates
 * resource consumers throughout this subtree while retaining navigation entries and UI state.
 */
@Composable
fun GkLanguageEnvironment(locale: Locale, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalResourceLocale provides locale, content = content)
}
