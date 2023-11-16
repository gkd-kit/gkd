package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

// From: https://github.com/llwdslal/WanAndroid

@Composable
fun PageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable (PaddingValues) -> Unit,
    statusBarColor: Color = Color.Transparent,
    navigationBarColor: Color = Color.Transparent,
    useDarkModeIcons:(() -> Boolean)? = null,
) {
    val systemUiController = rememberSystemUiController()
    val isUseDarkModeIcons = if (useDarkModeIcons != null){
        useDarkModeIcons()
    }else{
        shouldUseDarkModeIcons(statusBarColor = statusBarColor)
    }

    SideEffect {
        setSystemBarsColor(systemUiController, isUseDarkModeIcons, statusBarColor, navigationBarColor)
    }

    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        content = content
    )
}

@Composable
fun FullScreenScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable (PaddingValues) -> Unit,
) {
    val systemUiController = rememberSystemUiController()
    val isUseDarkModeIcons = shouldUseDarkModeIcons(statusBarColor = Color.Transparent)
    SideEffect {
        transparentSystemBars(systemUiController, isUseDarkModeIcons)
    }
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        content = content
    )
}