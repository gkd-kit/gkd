package li.songe.gkd.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.BuildConfig

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AboutPage() {
    //    val systemUiController = rememberSystemUiController()
    //    val context = LocalContext.current as ComponentActivity
    //    DisposableEffect(systemUiController) {
    //        val oldVisible = systemUiController.isStatusBarVisible
    //        systemUiController.isStatusBarVisible = false
    //        WindowCompat.setDecorFitsSystemWindows(context.window, false)
    //        onDispose {
    //            systemUiController.isStatusBarVisible = oldVisible
    //            WindowCompat.setDecorFitsSystemWindows(context.window, true)
    //        }
    //    }
    val context = LocalContext.current
    val navController = LocalNavController.current
    Scaffold(topBar = {
        SimpleTopAppBar(onClickIcon = { navController.popBackStack() }, title = "关于")
    }, content = { contentPadding ->
        Column(
            Modifier
                .padding(contentPadding)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "版本代码: " + BuildConfig.VERSION_CODE)
            Text(text = "版本名称: " + BuildConfig.VERSION_NAME)
            Text(text = "构建时间: " + BuildConfig.BUILD_DATE)
            Text(text = "构建类型: " + BuildConfig.BUILD_TYPE)
        }
    })

}