package li.gkd.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.app
import li.gkd.app.util.AndroidTarget

fun ComponentActivity.installThemedSplashScreen() {
    val defaultTheme = packageManager.getActivityInfo(componentName, 0).themeResource

    fun themeId(darkTheme: Boolean?): Int = when (darkTheme) {
        false -> app.splashScreenLightTheme
        true -> app.splashScreenNightTheme
        // Zero clears the system override and restores the manifest theme.
        null -> 0
    }

    val settings = SettingsRepository.settings
    val initialTheme = themeId(settings.value.enableDarkTheme)
    // Apply the same resources to the compatibility splash and its handoff to the Activity.
    setTheme(initialTheme.takeUnless { it == 0 } ?: defaultTheme)
    installSplashScreen()

    if (AndroidTarget.S) {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Project one settings snapshot into the system's persisted launch theme.
            // Keep collecting while stopped; settings may also change through a restore.
            settings.map { it.enableDarkTheme }
                .distinctUntilChanged()
                .collect { darkTheme ->
                    splashScreen.setSplashScreenTheme(themeId(darkTheme))
                }
        }
    }
}
