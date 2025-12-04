package li.songe.gkd.ui.style

import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.app
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.LocalDarkTheme
import li.songe.gkd.ui.share.LocalIsTalkbackEnabled
import li.songe.gkd.util.AndroidTarget

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme = darkColorScheme()

@Composable
fun AppTheme(
    invertedTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val enableDarkThemeFlow = remember {
        storeFlow.map { it.enableDarkTheme }.debounce(300).stateIn(
            scope, SharingStarted.Eagerly, storeFlow.value.enableDarkTheme
        )
    }
    val enableDynamicColorFlow = remember {
        storeFlow.map { it.enableDynamicColor }.debounce(300).stateIn(
            scope, SharingStarted.Eagerly, storeFlow.value.enableDynamicColor
        )
    }
    val enableDarkTheme by enableDarkThemeFlow.collectAsState()
    val enableDynamicColor by enableDynamicColorFlow.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = (enableDarkTheme ?: systemInDarkTheme).let {
        if (invertedTheme) !it else it
    }
    val colorScheme = when {
        AndroidTarget.S && enableDynamicColor && darkTheme -> dynamicDarkColorScheme(app)
        AndroidTarget.S && enableDynamicColor && !darkTheme -> dynamicLightColorScheme(app)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val activity = LocalActivity.current
    if (activity != null) {
        LaunchedEffect(darkTheme) {
            // https://github.com/gkd-kit/gkd/pull/421
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
            }
        }
        val bg = colorScheme.background.toArgb()
        LaunchedEffect(darkTheme, bg) {
            activity.window.decorView.setBackgroundColor(bg)
        }
    }

    val isTalkbackEnabledFlow = remember {
        MutableStateFlow(app.a11yManager.isTouchExplorationEnabled)
    }
    DisposableEffect(null) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener {
            isTalkbackEnabledFlow.value = it
        }
        app.a11yManager.addTouchExplorationStateChangeListener(listener)
        onDispose {
            app.a11yManager.removeTouchExplorationStateChangeListener(listener)
        }
    }
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalIsTalkbackEnabled provides isTalkbackEnabledFlow
    ) {
        MaterialTheme(
            colorScheme = colorScheme.animation(),
            content = content,
        )
    }
}

@Composable
private fun Color.animation() = animateColorAsState(
    targetValue = this,
    animationSpec = tween(durationMillis = 500),
    label = "animation"
).value

@Composable
private fun ColorScheme.animation(): ColorScheme {
    return copy(
        primary = primary.animation(),
        onPrimary = onPrimary.animation(),
        primaryContainer = primaryContainer.animation(),
        onPrimaryContainer = onPrimaryContainer.animation(),
        inversePrimary = inversePrimary.animation(),
        secondary = secondary.animation(),
        onSecondary = onSecondary.animation(),
        secondaryContainer = secondaryContainer.animation(),
        onSecondaryContainer = onSecondaryContainer.animation(),
        tertiary = tertiary.animation(),
        onTertiary = onTertiary.animation(),
        tertiaryContainer = tertiaryContainer.animation(),
        onTertiaryContainer = onTertiaryContainer.animation(),
        background = background.animation(),
        onBackground = onBackground.animation(),
        surface = surface.animation(),
        onSurface = onSurface.animation(),
        surfaceVariant = surfaceVariant.animation(),
        onSurfaceVariant = onSurfaceVariant.animation(),
        surfaceTint = surfaceTint.animation(),
        inverseSurface = inverseSurface.animation(),
        inverseOnSurface = inverseOnSurface.animation(),
        error = error.animation(),
        onError = onError.animation(),
        errorContainer = errorContainer.animation(),
        onErrorContainer = onErrorContainer.animation(),
        outline = outline.animation(),
        outlineVariant = outlineVariant.animation(),
        scrim = scrim.animation(),
        surfaceBright = surfaceBright.animation(),
        surfaceDim = surfaceDim.animation(),
        surfaceContainer = surfaceContainer.animation(),
        surfaceContainerHigh = surfaceContainerHigh.animation(),
        surfaceContainerHighest = surfaceContainerHighest.animation(),
        surfaceContainerLow = surfaceContainerLow.animation(),
        surfaceContainerLowest = surfaceContainerLowest.animation(),
    )
}
