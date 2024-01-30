package li.songe.gkd.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.BuildConfig
import li.songe.gkd.appScope
import li.songe.gkd.util.GIT_COMMIT_URL
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.REPOSITORY_URL
import li.songe.gkd.util.format
import li.songe.gkd.util.launchTry

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AboutPage() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = { Text(text = "关于") }, actions = {})
    }, content = { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
        ) {
            Column(modifier = Modifier
                .clickable {
                    appScope.launchTry {
                        // ActivityNotFoundException
                        // https://bugly.qq.com/v2/crash-reporting/crashes/d0ce46b353/117002?pid=1
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)
                            )
                        )
                    }
                }
                .fillMaxWidth()
                .padding(10.dp)) {
                Text(
                    text = "开源地址", fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = REPOSITORY_URL,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = "版本代码", fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = BuildConfig.VERSION_CODE.toString(),
                    fontSize = 14.sp,
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = "版本名称", fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = BuildConfig.VERSION_NAME,
                    fontSize = 14.sp,
                )
            }
            @Suppress("SENSELESS_COMPARISON") if (GIT_COMMIT_URL != null && BuildConfig.GIT_COMMIT_ID != null) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .clickable {
                            appScope.launchTry {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW, Uri.parse(GIT_COMMIT_URL)
                                    )
                                )
                            }
                        }
                        .fillMaxWidth()
                        .padding(10.dp)) {
                    Text(
                        text = "代码记录", fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = BuildConfig.GIT_COMMIT_ID,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = "构建时间", fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = BuildConfig.BUILD_TIME.format("yyyy-MM-dd HH:mm:ss ZZ"),
                    fontSize = 14.sp,
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = "构建类型", fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = BuildConfig.BUILD_TYPE,
                    fontSize = 14.sp,
                )
            }
        }
    })

}