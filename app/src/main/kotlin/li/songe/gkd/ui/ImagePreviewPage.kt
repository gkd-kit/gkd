package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
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
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    var showBars by remember { mutableStateOf(true) }

    // 路由同时支持单图和多图，这里先统一成一个列表，后续 pager / 单图逻辑都共用它。
    val previewUris = remember(route.uri, route.uris) {
        route.uris.ifEmpty {
            route.uri?.let(::listOf) ?: emptyList()
        }
    }
    val singleUri = previewUris.singleOrNull()
    val pagerState = rememberPagerState(pageCount = { previewUris.size.coerceAtLeast(1) })

    // 这个页面需要接近相册页的沉浸式效果，因此进入时隐藏状态栏，离开时恢复原设置。
    DisposableEffect(Unit) {
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
        when {
            singleUri != null -> {
                UriImage(
                    uri = singleUri,
                    onToggleBars = { showBars = !showBars },
                )
            }

            previewUris.isNotEmpty() -> {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState,
                    pageContent = { index ->
                        UriImage(
                            uri = previewUris[index],
                            onToggleBars = { showBars = !showBars },
                        )
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .zIndex(1f)
                .fillMaxWidth()
        ) {
            Column {
                val currentUri = singleUri ?: previewUris.getOrNull(pagerState.currentPage)
                PerfTopAppBar(
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)),
                    navigationIcon = {
                        PerfIconButton(
                            imageVector = PerfIcon.ArrowBack,
                            onClick = { mainVm.popPage() },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        )
                    },
                    title = {
                        route.title?.let { title ->
                            Text(
                                text = title,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.MiddleEllipsis,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    },
                    actions = {
                        if (currentUri != null && URLUtil.isNetworkUrl(currentUri)) {
                            PerfIconButton(
                                imageVector = PerfIcon.OpenInNew,
                                onClick = throttle(fn = { mainVm.openUrl(currentUri) }),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )

                if (previewUris.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${previewUris.size}",
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UriImage(
    uri: String,
    onToggleBars: () -> Unit = {},
) {
    val context = LocalContext.current
    // 手势层切至Telephoto,loading,error还是使用AsyncImagePainter.State统一驱动
    val model = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(DefaultDurationMillis)
            .run {
                if (URLUtil.isNetworkUrl(uri)) {
                    this
                } else {
                    diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                }
            }
            .build()
            .apply {
                imageLoader.enqueue(this)
            }
    }
    val painter = rememberAsyncImagePainter(model)
    val state by painter.state.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val stateVal = state) {
            AsyncImagePainter.State.Empty -> Unit

            is AsyncImagePainter.State.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(uri) {
                            detectTapGestures(onTap = { onToggleBars() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                }
            }

            is AsyncImagePainter.State.Success -> {
                ZoomableImageContent(
                    uri = uri,
                    painter = painter,
                    onToggleBars = onToggleBars,
                )
            }

            is AsyncImagePainter.State.Error -> {
                val reload = throttle { painter.restart() }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(uri) {
                            detectTapGestures(onTap = { onToggleBars() })
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier = Modifier.pointerInput(uri) {
                            detectTapGestures(onTap = { reload() })
                        },
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
}

@Composable
private fun ZoomableImageContent(
    uri: String,
    painter: Painter,
    onToggleBars: () -> Unit,
) {
    // 每个 pager page 都独立持有一个 ZoomableState，避免翻页后复用缩放位置。
    val zoomableState = rememberZoomableState()
    val intrinsicSize = painter.intrinsicSize

    // Image() 的绘制区域和实际图片内容边界并不总是完全一致。
    // 把内容位置告诉 Telephoto 后，边缘检测和与 pager 的手势协同会更稳定。
    LaunchedEffect(uri, intrinsicSize) {
        if (intrinsicSize != Size.Unspecified && intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
            zoomableState.setContentLocation(
                ZoomableContentLocation.scaledInsideAndCenterAligned(intrinsicSize)
            )
        }
    }

    // 限制图片成功状态下的深色画布背景，防止非必要全局黑色背景不跟随主题
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(
                    state = zoomableState,
                    onClick = { onToggleBars() },
                ),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
        )
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
                )
            )
        }
        .build()
}
