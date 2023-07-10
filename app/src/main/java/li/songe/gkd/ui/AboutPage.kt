package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import li.songe.gkd.BuildConfig
import li.songe.gkd.utils.SafeR

@RootNavGraph
@Destination
@Composable
fun AboutPage(navigator: DestinationsNavigator) {
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
    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = Color(0xfff8f9f9),
                navigationIcon = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = SafeR.ic_back),
                            contentDescription = null,
                            modifier = Modifier
                                .size(30.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = false),
                                ) {
                                    navigator.popBackStack()
                                }
                        )
                    }
                },
                title = { Text(text = "关于") }
            )
        },
        content = { contentPadding ->
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
        }
    )

}