package li.gkd.app.ui

import li.gkd.app.MainViewModel

import android.webkit.URLUtil
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import coil3.EventListener
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.Decoder
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.imageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.crossfade
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.app
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.TimeUtils.throttle
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar

@Serializable
data class ImagePreviewItem(
    val uri: String,
    val title: String? = null,
    val titles: List<String> = emptyList(),
)

@Serializable
data class ImagePreviewRoute(
    val title: String? = null,
    val uri: String? = null,
    val uris: List<String> = emptyList(),
    val items: List<ImagePreviewItem> = emptyList(),
) : NavKey

private val imageLoader by lazy {
    ImageLoader.Builder(app)
        .diskCache {
            DiskCache.Builder()
                .directory(FolderUtils.coilCacheDir.toOkioPath())
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

@Composable
fun ImagePreviewPage(route: ImagePreviewRoute) {
    val mainVm = MainViewModel.requireCurrent()
    val context = LocalActivity.current as MainActivity
    var showBars by remember { mutableStateOf(true) }

    // Routes simultaneously support old uri/uris and new items; the preview page uniformly handles them as image items internally.
    val previewItems = remember(route) {
        when {
            route.items.isNotEmpty() -> route.items
            route.uris.isNotEmpty() -> route.uris.map { ImagePreviewItem(it) }
            route.uri != null -> listOf(ImagePreviewItem(uri = route.uri))
            else -> emptyList()
        }
    }
    val previewUris = remember(previewItems) { previewItems.map { it.uri } }
    val singleItem = previewItems.singleOrNull()
    val pagerState = rememberPagerState(pageCount = { previewItems.size.coerceAtLeast(1) })

    val controller = remember {
        WindowCompat.getInsetsController(context.window, context.window.decorView)
    }
    DisposableEffect(null) {
        val oldBehavior = controller.systemBarsBehavior
        val oldLight = controller.isAppearanceLightStatusBars
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        onDispose {
            controller.systemBarsBehavior = oldBehavior
            controller.isAppearanceLightStatusBars = oldLight
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    LaunchedEffect(showBars) {
        if (showBars) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    // Rule group example images will swipe continuously horizontally, but prefetch concurrency is limited to 2 to avoid competing for bandwidth with the first image display request.
    LaunchedEffect(previewUris) {
        if (previewUris.size <= 1) return@LaunchedEffect
        previewUris
            .drop(1)
            .filter(URLUtil::isNetworkUrl)
            .chunked(2)
            .forEach { uriBatch ->
                uriBatch.map { preloadUri ->
                    async {
                        imageLoader.execute(
                            buildPreviewImageRequest(
                                context = context,
                                uri = preloadUri,
                            )
                        )
                    }
                }.awaitAll()
            }
    }

    Box(
        modifier = Modifier
            .background(Color.Black)
            .fillMaxSize()
    ) {
        when {
            singleItem != null -> {
                UriImage(
                    uri = singleItem.uri,
                    onToggleBars = { showBars = !showBars },
                )
            }

            previewItems.isNotEmpty() -> {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState,
                    pageContent = { index ->
                        UriImage(
                            uri = previewItems[index].uri,
                            onToggleBars = { showBars = !showBars },
                        )
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .zIndex(1f)
                .fillMaxWidth()
        ) {
            Column {
                val currentPreviewItem =
                    singleItem ?: previewItems.getOrNull(pagerState.currentPage)
                val currentUri = currentPreviewItem?.uri
                GkTopAppBar(
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)),
                    navigationIcon = {
                        GkIconButton(
                            imageVector = GkIcons.ArrowBack,
                            onClick = { mainVm.popPage() },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        )
                    },
                    title = {
                        val baseTitle = route.title?.takeIf { it.isNotBlank() }
                        val itemTitle = currentPreviewItem
                            ?.let(::buildPreviewSubtitle)
                            ?.takeIf { it.isNotBlank() && it != baseTitle }
                        when {
                            baseTitle != null && itemTitle != null -> {
                                Column {
                                    Text(
                                        text = baseTitle,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.MiddleEllipsis,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Text(
                                        text = itemTitle,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.MiddleEllipsis,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Normal
                                        )
                                    )
                                }
                            }

                            baseTitle != null -> {
                                Text(
                                    text = baseTitle,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.MiddleEllipsis,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            itemTitle != null -> {
                                Text(
                                    text = itemTitle,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.MiddleEllipsis,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentUri != null && URLUtil.isNetworkUrl(currentUri)) {
                            GkIconButton(
                                imageVector = GkIcons.OpenInNew,
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

                if (previewItems.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = UiStrings.progress_fraction(pagerState.currentPage + 1, previewItems.size),
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
    val imageLoader = context.imageLoader
    val isNetworkImage = remember(uri) { URLUtil.isNetworkUrl(uri) }
    val phaseTextFlow = remember(uri) { MutableStateFlow<String?>(null) }
    val phaseText by phaseTextFlow.collectAsStateWithLifecycle()

    // When the gesture layer switches to Telephoto, loading/error still uses AsyncImagePainter.State for unified drive.
    val model = remember(uri) {
        buildPreviewImageRequest(
            context = context,
            uri = uri,
            listener = object : EventListener() {
                override fun onStart(request: ImageRequest) {
                    phaseTextFlow.value = UiStrings.image_requesting
                }

                override fun fetchStart(
                    request: ImageRequest,
                    fetcher: Fetcher,
                    options: Options,
                ) {
                    phaseTextFlow.value = if (isNetworkImage) UiStrings.image_downloading else UiStrings.image_reading
                }

                override fun decodeStart(
                    request: ImageRequest,
                    decoder: Decoder,
                    options: Options,
                ) {
                    phaseTextFlow.value = UiStrings.image_decoding
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    phaseTextFlow.value = null
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    phaseTextFlow.value = null
                }

                override fun onCancel(request: ImageRequest) {
                    phaseTextFlow.value = null
                }
            }
        )
    }
    val painter = rememberAsyncImagePainter(
        model = model,
        imageLoader = imageLoader,
    )
    val state by painter.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val stateVal = state) {
            AsyncImagePainter.State.Empty -> Unit

            is AsyncImagePainter.State.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(uri) {
                            detectTapGestures(onTap = { onToggleBars() })
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    phaseText?.let { text ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = text,
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
                        text = UiStrings.image_load_failed_retry,
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
    // Each pager page independently holds a ZoomableState to avoid reusing zoom position after page flip.
    val zoomableState = rememberZoomableState()
    val intrinsicSize = painter.intrinsicSize

    // The drawing area of Image() and the actual image content boundary are not always exactly the same.
    // After telling Telephoto the content position, edge detection and gesture coordination with the pager will be more stable.
    LaunchedEffect(uri, intrinsicSize) {
        if (intrinsicSize != Size.Unspecified && intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
            zoomableState.setContentLocation(
                ZoomableContentLocation.scaledInsideAndCenterAligned(intrinsicSize)
            )
        }
    }

    // Restrict dark canvas background in successful image state to prevent unnecessary global black background from not following the theme
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
            contentScale = ContentScale.Inside,
            alignment = Alignment.Center,
        )
    }
}

private fun buildPreviewImageRequest(
    context: android.content.Context,
    uri: String,
    listener: EventListener? = null,
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(uri)
        .crossfade(DefaultDurationMillis)
        .listener(listener)
        .run {
            if (URLUtil.isNetworkUrl(uri)) {
                this
            } else {
                diskCachePolicy(CachePolicy.DISABLED)
                    .memoryCachePolicy(CachePolicy.DISABLED)
            }
        }
        .build()
}

private fun buildPreviewSubtitle(item: ImagePreviewItem): String? {
    val titles = buildList {
        item.title?.takeIf { it.isNotBlank() }?.let(::add)
        item.titles
            .mapNotNull { it.takeIf(String::isNotBlank) }
            .forEach(::add)
    }.distinct()
    return titles.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}
