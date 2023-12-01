package li.songe.gkd.ui

import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.imageLoader
import li.songe.gkd.util.subsIdToRawFlow


@RootNavGraph
@Destination
@Composable
fun GroupItemPage(subsInt: Long, appId: String, groupKey: Int) {
    val navController = LocalNavController.current
    val subsIdToRawState = subsIdToRawFlow.collectAsState()
    val appRaw = remember {
        subsIdToRawState.value[subsInt]?.apps?.first { a -> a.id == appId }
    }
    val group = remember {
        appRaw?.groups?.find { g -> g.key == groupKey }
    }
    val appInfoCache by appInfoCacheFlow.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = {
                Text(
                    text = ((appInfoCache[appId]?.name ?: appRaw?.name
                    ?: appId) + "/" + (group?.name ?: "未知规则"))
                )
            },
            actions = {},
            modifier = Modifier.zIndex(1f),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
            )
        )
        if (group != null) {
            val state = rememberPagerState { group.allExampleUrls.size }
            HorizontalPager(
                modifier = Modifier.fillMaxSize(), state = state
            ) { p ->
                val url = group.allExampleUrls.getOrNull(p)
                if (url != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(url)
                            .crossfade(DefaultDurationMillis).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        loading = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(50.dp))
                            }
                        },
                        error = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "加载失败", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        imageLoader = imageLoader
                    )
                }
            }
        }
    }
}