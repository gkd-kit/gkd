package li.songe.gkd.ui.theme

import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowInsetsControllerCompat
import li.songe.gkd.util.LocalMainViewModel

val LightColorScheme = lightColorScheme()
val DarkColorScheme = darkColorScheme()
val supportDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    // https://developer.android.com/jetpack/compose/designsystems/material3?hl=zh-cn
    val context = LocalActivity.current!!
    val mainVm = LocalMainViewModel.current
    val enableDarkTheme by mainVm.enableDarkThemeFlow.collectAsState()
    val enableDynamicColor by mainVm.enableDynamicColorFlow.collectAsState()
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
    }
    MaterialTheme(
        colorScheme = colorScheme.animation(),
        content = content,
    )
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
