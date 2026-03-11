package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.serialization.Serializable
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.coilCacheDir
import li.songe.gkd.util.throttle
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Serializable
data class ImagePreviewRoute(
    val title: String? = null,
    val uri: String? = null,
    val uris: List<String> = emptyList(),
) : NavKey

@Composable
fun ImagePreviewPage(route: ImagePreviewRoute) {
    val title = route.title
    val uri = route.uri
    val uris = route.uris
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    DisposableEffect(null) {
        val controller = WindowCompat.getInsetsController(context.window, context.window.decorView)
        val oldBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            controller.systemBarsBehavior = oldBehavior
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
    ) {
        val showUri = uri ?: if (uris.size == 1) uris.first() else null
        val state = rememberPagerState { uris.size }
        PerfTopAppBar(
            modifier = Modifier
                .zIndex(1f)
                .fillMaxWidth(),
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                    mainVm.popPage()
                })
            },
            title = {
                if (title != null) {
                    Text(
                        text = title,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.MiddleEllipsis,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.7f),
                                blurRadius = with(LocalDensity.current) { 2.dp.toPx() },
                                offset = with(LocalDensity.current) {
                                    Offset(1.dp.toPx(), 1.dp.toPx())
                                }
                            )
                        )
                    )
                }
            },
            actions = {
                val currentUri = showUri ?: uris.getOrNull(state.currentPage)
                if (currentUri != null && URLUtil.isNetworkUrl(currentUri)) {
                    PerfIconButton(imageVector = PerfIcon.OpenInNew, onClick = throttle(fn = {
                        mainVm.openUrl(currentUri)
                    }))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.1f)
            )
        )
        if (showUri != null) {
            UriImage(showUri)
        } else if (uris.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    pageContent = {
                        UriImage(uris[it])
                    }
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 150.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "${state.currentPage + 1}/${uris.size}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimary,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                blurRadius = with(LocalDensity.current) { 3.dp.toPx() }
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun UriImage(uri: String) {
    val context = LocalContext.current
    val model = remember(uri) {
        ImageRequest.Builder(context).data(uri)
            .crossfade(DefaultDurationMillis).run {
                if (URLUtil.isNetworkUrl(uri)) {
                    this
                } else {
                    diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED)
                }
            }
            .build().apply {
                imageLoader.enqueue(this)
            }
    }
    val painter = rememberAsyncImagePainter(model)
    val state by painter.state.collectAsState()
    val stateVal = state
    when (stateVal) {
        AsyncImagePainter.State.Empty -> {}
        is AsyncImagePainter.State.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }

        is AsyncImagePainter.State.Success -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.Center,
                )
            }
        }

        is AsyncImagePainter.State.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    modifier = Modifier.clickable(onClick = throttle { painter.restart() }),
                    text = "加载失败, 点击重试",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                stateVal.result.throwable.message?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private val imageLoader by lazy {
    ImageLoader.Builder(app)
        .diskCache {
            DiskCache.Builder()
                .directory(coilCacheDir.toOkioPath())
                .maxSizePercent(0.1)
                .build()
        }
        .components {
            if (AndroidTarget.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
            add(
                OkHttpNetworkFetcherFactory(
                    callFactory = {
                        OkHttpClient.Builder()
                            .connectTimeout(30.seconds.toJavaDuration())
                            .readTimeout(30.seconds.toJavaDuration())
                            .writeTimeout(30.seconds.toJavaDuration())
                            .build()
                    }
                ))
        }
        .build()
}
