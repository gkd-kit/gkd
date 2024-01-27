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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.imageLoader
import li.songe.gkd.util.subsIdToRawFlow


@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun GroupItemPage(subsInt: Long, groupKey: Int, appId: String? = null) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val subsIdToRaw by subsIdToRawFlow.collectAsState()
    val rawSubs = subsIdToRaw[subsInt]
    val rawApp = rawSubs?.apps?.first { a -> a.id == appId }
    val group = if (appId == null) {
        rawSubs?.globalGroups?.find { g -> g.key == groupKey }
    } else {
        rawApp?.groups?.find { g -> g.key == groupKey }
    }
    val allExampleUrls = when (group) {
        is RawSubscription.RawAppGroup -> group.allExampleUrls
        is RawSubscription.RawGlobalGroup -> group.allExampleUrls
        else -> emptyList()
    }
    val appInfoCache by appInfoCacheFlow.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = {
                when (group) {
                    is RawSubscription.RawAppGroup -> {
                        Text(
                            text = ((rawSubs?.name
                                ?: subsInt.toString()) + "/" + (appInfoCache[appId]?.name
                                ?: rawApp?.name
                                ?: appId) + "/" + (group.name))
                        )
                    }

                    is RawSubscription.RawGlobalGroup -> {
                        Text(
                            text = "${rawSubs?.name ?: subsInt}/${group.name}"
                        )
                    }

                    else -> {
                        Text(text = "未知规则")
                    }

                }
            },
            actions = {},
            modifier = Modifier.zIndex(1f),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
            )
        )
        if (group != null) {
            val state = rememberPagerState { allExampleUrls.size }
            HorizontalPager(
                modifier = Modifier.fillMaxSize(), state = state
            ) { p ->
                val url = allExampleUrls.getOrNull(p)
                if (url != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(url)
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