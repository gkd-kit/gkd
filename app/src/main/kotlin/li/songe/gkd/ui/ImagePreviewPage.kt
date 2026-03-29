package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
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
import kotlin.math.abs
import kotlin.math.max
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
    var showBars by remember { mutableStateOf(true) }
    var userScrollEnabled by remember { mutableStateOf(true) }

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
            .background(Color.Black)
            .fillMaxSize()
    ) {
        val showUri = uri ?: if (uris.size == 1) uris.first() else null
        val state = rememberPagerState { uris.size }

        LaunchedEffect(state.currentPage) {
            userScrollEnabled = true
        }

        if (showUri != null) {
            UriImage(
                uri = showUri,
                onToggleBars = { showBars = !showBars },
                onScaleChange = { userScrollEnabled = it <= 1f }
            )
        } else if (uris.isNotEmpty()) {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                userScrollEnabled = userScrollEnabled,
                pageContent = { index ->
                    UriImage(
                        uri = uris[index],
                        onToggleBars = { showBars = !showBars },
                        onScaleChange = { scale ->
                            if (index == state.currentPage) {
                                userScrollEnabled = scale <= 1f
                            }
                        }
                    )
                }
            )
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
                        if (title != null) {
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
                        val currentUri = showUri ?: uris.getOrNull(state.currentPage)
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

                if (uris.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${state.currentPage + 1} / ${uris.size}",
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

// 图片预览一些功能demo
@Composable
private fun UriImage(
    uri: String,
    onToggleBars: () -> Unit = {},
    onScaleChange: (Float) -> Unit = {}
) {
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val stateVal = state) {
            AsyncImagePainter.State.Empty -> {}
            is AsyncImagePainter.State.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(uri) { detectTapGestures(onTap = { onToggleBars() }) },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                }
            }

            is AsyncImagePainter.State.Success -> {
                val scope = rememberCoroutineScope()
                val scale = remember(uri) { Animatable(1f) }
                val offsetX = remember(uri) { Animatable(0f) }
                val offsetY = remember(uri) { Animatable(0f) }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidth = constraints.maxWidth.toFloat()
                    val containerHeight = constraints.maxHeight.toFloat()

                    val intrinsicSize = painter.intrinsicSize
                    val imageHeight = remember(intrinsicSize, containerWidth) {
                        if (intrinsicSize != Size.Unspecified && intrinsicSize.width > 0) {
                            containerWidth * (intrinsicSize.height / intrinsicSize.width)
                        } else {
                            containerHeight
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(uri) {
                                detectTapGestures(
                                    onTap = { onToggleBars() },
                                    onDoubleTap = { tapOffset ->
                                        scope.launch {
                                            // 双指缩放控制
                                            if (scale.value > 1f) {
                                                launch { scale.animateTo(1f) }
                                                launch { offsetX.animateTo(0f) }
                                                launch { offsetY.animateTo(0f) }
                                                onScaleChange(1f)
                                            } else {
                                                // 双指缩放以两个手指开合你看到的上下手指中心点为中心的偏移量缩放控制计算，防止在中间缩放图片
                                                val targetScale = 3f
                                                val center = Offset(containerWidth / 2, containerHeight / 2)
                                                var targetOffsetX = (center.x - tapOffset.x) * (targetScale - 1)
                                                var targetOffsetY = (center.y - tapOffset.y) * (targetScale - 1)

                                                val maxOffsetX = max(0f, (containerWidth * targetScale - containerWidth) / 2f)
                                                val maxOffsetY = max(0f, (imageHeight * targetScale - containerHeight) / 2f)
                                                targetOffsetX = targetOffsetX.coerceIn(-maxOffsetX, maxOffsetX)
                                                targetOffsetY = targetOffsetY.coerceIn(-maxOffsetY, maxOffsetY)

                                                launch { scale.animateTo(targetScale) }
                                                launch { offsetX.animateTo(targetOffsetX) }
                                                launch { offsetY.animateTo(targetOffsetY) }
                                                onScaleChange(targetScale)
                                            }
                                        }
                                    }
                                )
                            }
                            .pointerInput(uri, containerWidth, containerHeight, imageHeight) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()

                                        if (zoomChange != 1f) {
                                            val targetScale = (scale.value * zoomChange).coerceIn(1f, 5f)
                                            scope.launch {
                                                scale.snapTo(targetScale)
                                            }
                                            onScaleChange(targetScale)
                                            event.changes.forEach { it.consume() }
                                        // 处理平移手势拖动
                                        } else if (scale.value > 1f) {
                                            val currentScale = scale.value
                                            val maxOffsetX = max(0f, (containerWidth * currentScale - containerWidth) / 2f)
                                            val maxOffsetY = max(0f, (imageHeight * currentScale - containerHeight) / 2f)

                                            val atLeftEdge = offsetX.value >= maxOffsetX
                                            val atRightEdge = offsetX.value <= -maxOffsetX

                                            val isHorizontalPan = abs(panChange.x) > abs(panChange.y)
                                            val isScrollingOut = (panChange.x > 0 && atLeftEdge) || (panChange.x < 0 && atRightEdge)

                                            if (isHorizontalPan && isScrollingOut) {
                                                // 让 HorizontalPager 处理
                                            } else {
                                                scope.launch {
                                                    offsetX.snapTo((offsetX.value + panChange.x).coerceIn(-maxOffsetX, maxOffsetX))
                                                    offsetY.snapTo((offsetY.value + panChange.y).coerceIn(-maxOffsetY, maxOffsetY))
                                                }
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // 渲染图片
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale.value,
                                    scaleY = scale.value,
                                    translationX = offsetX.value,
                                    translationY = offsetY.value
                                )
                                .fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }

            is AsyncImagePainter.State.Error -> {
                val reload = throttle { painter.restart() }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(uri) { detectTapGestures(onTap = { onToggleBars() }) },
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
