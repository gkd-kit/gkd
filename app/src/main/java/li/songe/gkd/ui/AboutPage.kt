package li.songe.gkd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.BuildConfig
import li.songe.gkd.R
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AboutPage() {
    val navController = LocalNavController.current
    Scaffold(topBar = {
        TopAppBar(navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = { Text(text = stringResource(R.string.about))}, actions = {})
    }, content = { contentPadding ->
        Column(
            Modifier
                .padding(contentPadding)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 应用名称
            Text(text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium)
            // 应用介绍
            Text(text = stringResource(R.string.app_desc),
                style = MaterialTheme.typography.bodySmall)
            // 版本信息
            Text(text = stringResource(R.string.version_code) + BuildConfig.VERSION_CODE,
                style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.version_name) + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium)
            // 构建信息
            Text(text = stringResource(R.string.build_date) + BuildConfig.BUILD_DATE,
                style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.build_type) + BuildConfig.BUILD_TYPE,
                style = MaterialTheme.typography.bodyMedium)
        }
    })
}
