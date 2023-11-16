package li.songe.gkd.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.accompanist.systemuicontroller.SystemUiController
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
fun TransparentSystemBars(bgColor: Color = Color.Transparent) {
    val systemUiController = rememberSystemUiController()
    val useDarkModeIcons = shouldUseDarkModeIcons(statusBarColor = bgColor)
    SideEffect {
        transparentSystemBars(systemUiController, useDarkModeIcons)
    }
}

@Composable
fun SetStatusBarColor(statusBarColor: Color) {
    val systemUiController = rememberSystemUiController()
    val useDarkModeIcons = shouldUseDarkModeIcons(statusBarColor = statusBarColor)
    SideEffect {
        setStatusBarColor(systemUiController, useDarkModeIcons, statusBarColor)
    }
}

@Composable
fun SetNavigationBarColor(navigationBarColor: Color) {
    val systemUiController = rememberSystemUiController()
    val useDarkModeIcons = shouldUseDarkModeIcons(statusBarColor = navigationBarColor)
    SideEffect {
        setNavigationBarColor(systemUiController, useDarkModeIcons, navigationBarColor)
    }
}

@Composable
fun SetSystemBarsColor(statusBarColor: Color, navigationBarColor: Color) {
    val systemUiController = rememberSystemUiController()
    val useDarkModeIcons = shouldUseDarkModeIcons(statusBarColor = statusBarColor)

    SideEffect {
        setSystemBarsColor(systemUiController, useDarkModeIcons, statusBarColor, navigationBarColor)
    }
}

@Composable
fun SetStatusBarIconDark(isDark:Boolean = true){
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.statusBarDarkContentEnabled = isDark
    }
}

fun transparentSystemBars(systemUiController: SystemUiController, useDarkModeIcons: Boolean) {
    systemUiController.setSystemBarsColor(
        color = Color.Transparent,
        darkIcons = useDarkModeIcons,
        isNavigationBarContrastEnforced = false,
    )
}


fun setStatusBarColor(
    systemUiController: SystemUiController,
    useDarkModeIcons: Boolean,
    statusBarColor: Color
) {
    systemUiController.setStatusBarColor(
        color = statusBarColor,
        darkIcons = useDarkModeIcons,
    )
}

fun setNavigationBarColor(
    systemUiController: SystemUiController,
    useDarkModeIcons: Boolean,
    navigationBarColor: Color
) {
    systemUiController.setNavigationBarColor(
        color = navigationBarColor,
        darkIcons = useDarkModeIcons,
        navigationBarContrastEnforced = false
    )
}


fun setSystemBarsColor(
    systemUiController: SystemUiController,
    useDarkModeIcons: Boolean,
    statusBarColor: Color,
    navigationBarColor: Color
) {

    systemUiController.setStatusBarColor(
        color = statusBarColor,
        darkIcons = useDarkModeIcons,
    )

    systemUiController.setNavigationBarColor(
        color = navigationBarColor,
        darkIcons = useDarkModeIcons,
        navigationBarContrastEnforced = false
    )
}

@Composable
fun shouldUseDarkModeIcons(statusBarColor: Color):Boolean{
    return if (statusBarColor == Color.Transparent){
        !isSystemInDarkTheme()
    }
    else
    {
        statusBarColor.luminance() >= 0.5
    }
}
