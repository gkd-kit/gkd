package li.songe.gkd.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.BuildConfig
import li.songe.gkd.R
import li.songe.gkd.appScope
import li.songe.gkd.ui.component.PageScaffold
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.REPOSITORY_URL
import li.songe.gkd.util.launchTry

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AboutPage() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    PageScaffold(topBar = {
        TopAppBar(navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = { Text(text = stringResource(R.string.about)) }, actions = {})
    }, content = { contentPadding ->
        Column(
            Modifier
                .padding(contentPadding)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row {
                Text(text = stringResource(R.string.opensource_address))
                Text(text = REPOSITORY_URL,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        appScope.launchTry {
                            // ActivityNotFoundException
                            // https://bugly.qq.com/v2/crash-reporting/crashes/d0ce46b353/117002?pid=1
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)
                                )
                            )
                        }
                    })
            }
            Text(text = stringResource(R.string.version_code) + BuildConfig.VERSION_CODE)
            Text(text = stringResource(R.string.version_name) + BuildConfig.VERSION_NAME)
            Text(text = stringResource(R.string.build_date) + BuildConfig.BUILD_DATE)
            Text(text = stringResource(R.string.build_type) + BuildConfig.BUILD_TYPE)
        }
    })

}