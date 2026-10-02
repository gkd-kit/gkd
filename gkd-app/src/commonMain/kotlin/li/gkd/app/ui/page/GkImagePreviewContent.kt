package li.gkd.app.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.Decoder
import coil3.fetch.Fetcher
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.crossfade
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.image_decoding
import li.gkd.app.resources.image_downloading
import li.gkd.app.resources.image_load_failed_retry
import li.gkd.app.resources.image_reading
import li.gkd.app.resources.image_requesting
import li.gkd.app.resources.progress_fraction
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.navigation.ImagePreviewItem
import li.gkd.app.ui.navigation.ImagePreviewRoute
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
private fun PreviewBaseTitle(title: String) {
    val style = MaterialTheme.typography.titleLarge.copy(
        color = Color.White,
        fontWeight = FontWeight.Medium,
    )
    Text(
        text = title,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.MiddleEllipsis,
        style = style,
    )
}

@Composable
fun GkImagePreviewContent(
    imageLoader: ImageLoader,
    systemBars: @Composable (Boolean) -> Unit = {}, route: ImagePreviewRoute,
    onBack: () -> Unit,
    imageVersion: Int = 0,
    pagerState: PagerState? = null,
    titleContent: (@Composable (Int) -> Unit)? = null,
    actionContent: @Composable RowScope.(String?, Int) -> Unit = { _, _ -> },
) {
    val context = LocalPlatformContext.current
    var showBars by remember { mutableStateOf(true) }

    val previewItems = route.items
    val previewUris = remember(previewItems) { previewItems.map { it.uri } }
    val singleItem = previewItems.singleOrNull()
    val localPagerState = rememberPagerState(pageCount = { previewItems.size.coerceAtLeast(1) })
    val activePagerState = pagerState ?: localPagerState
    val currentPage = activePagerState.currentPage

    systemBars(showBars)

    // 规则组示例图会连续横滑，但预取并发限制在 2，避免与首图显示请求抢带宽。
    LaunchedEffect(previewUris) {
        if (previewUris.size <= 1) return@LaunchedEffect
        previewUris
            .drop(1)
            .filter(::isNetworkUrl)
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

    BoxWithConstraints(
        modifier = Modifier
            .background(Color.Black)
            .fillMaxSize()
    ) {
        when {
            singleItem != null -> {
                key(singleItem.uri, imageVersion) {
                    UriImage(
                        imageLoader = imageLoader,
                        uri = singleItem.uri,
                        version = imageVersion,
                        onToggleBars = { showBars = !showBars },
                    )
                }
            }

            previewItems.isNotEmpty() -> {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = activePagerState,
                    pageContent = { index ->
                        key(previewItems[index].uri, imageVersion) {
                            UriImage(
                                imageLoader = imageLoader,
                                uri = previewItems[index].uri,
                                version = imageVersion,
                                onToggleBars = { showBars = !showBars },
                            )
                        }
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
                    singleItem ?: previewItems.getOrNull(currentPage)
                val currentUri = currentPreviewItem?.uri
                GkTopAppBar(
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)),
                    navigationIcon = {
                        GkIconButton(
                            imageVector = GkIcons.ArrowBack,
                            onClick = onBack,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        )
                    },
                    title = {
                        if (titleContent != null) {
                            titleContent(currentPage)
                        } else {
                            val baseTitle = route.title?.takeIf { it.isNotBlank() }
                            val itemTitle = currentPreviewItem?.let(::buildPreviewSubtitle)
                                ?.takeIf { it.isNotBlank() && it != baseTitle }
                            when {
                                baseTitle != null && itemTitle != null -> {
                                    Column {
                                        PreviewBaseTitle(baseTitle)
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
                                    PreviewBaseTitle(baseTitle)
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
                        }
                    },
                    actions = { actionContent(currentUri, currentPage) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        }
        if (previewItems.size > 1) {
            AnimatedVisibility(
                visible = showBars,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f)
                    .offset(y = -(maxHeight / 4)),
            ) {
                Text(
                    text = stringResource(
                        Res.string.progress_fraction,
                        currentPage + 1,
                        previewItems.size
                    ),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                )
            }
        }
    }
}

@Composable
private fun UriImage(
    imageLoader: ImageLoader, uri: String,
    version: Int,
    onToggleBars: () -> Unit = {},
) {
    val context = LocalPlatformContext.current

    val isNetworkImage = remember(uri) { isNetworkUrl(uri) }
    val phaseResourceFlow = remember(uri, version) { MutableStateFlow<StringResource?>(null) }
    val phaseResource by phaseResourceFlow.collectAsStateWithLifecycle()
    val phaseText = phaseResource?.let { stringResource(it) }

    // 手势层切至 Telephoto，loading / error 还是使用 AsyncImagePainter.State 统一驱动。
    val model = remember(uri, version) {
        buildPreviewImageRequest(
            context = context,
            uri = uri,
            listener = object : EventListener() {
                override fun onStart(request: ImageRequest) {
                    phaseResourceFlow.value = Res.string.image_requesting
                }

                override fun fetchStart(
                    request: ImageRequest,
                    fetcher: Fetcher,
                    options: Options,
                ) {
                    phaseResourceFlow.value =
                        if (isNetworkImage) Res.string.image_downloading else Res.string.image_reading
                }

                override fun decodeStart(
                    request: ImageRequest,
                    decoder: Decoder,
                    options: Options,
                ) {
                    phaseResourceFlow.value = Res.string.image_decoding
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    phaseResourceFlow.value = null
                }

                override fun onError(request: ImageRequest, result: ErrorResult) {
                    phaseResourceFlow.value = null
                }

                override fun onCancel(request: ImageRequest) {
                    phaseResourceFlow.value = null
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
                key(uri, version) {
                    ZoomableImageContent(
                        uri = uri,
                        painter = painter,
                        onToggleBars = onToggleBars,
                    )
                }
            }

            is AsyncImagePainter.State.Error -> {
                val reload = { painter.restart() }
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
                        text = stringResource(Res.string.image_load_failed_retry),
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
            contentScale = ContentScale.Inside,
            alignment = Alignment.Center,
        )
    }
}

private fun buildPreviewImageRequest(
    context: PlatformContext,
    uri: String,
    listener: EventListener? = null,
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(uri)
        .crossfade(DefaultDurationMillis)
        .listener(listener)
        .run {
            if (isNetworkUrl(uri)) {
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

private fun isNetworkUrl(value: String) =
    value.startsWith("http://", true) || value.startsWith("https://", true)
