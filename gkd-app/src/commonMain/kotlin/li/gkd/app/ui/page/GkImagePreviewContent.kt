package li.gkd.app.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.navigation.ImagePreviewItem
import li.gkd.app.ui.navigation.ImagePreviewRoute
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.zoomable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
private fun PreviewBaseTitle(title: String) {
    val style =
        MaterialTheme.typography.titleLarge.copy(
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
    systemBars: @Composable (Boolean) -> Unit = {},
    route: ImagePreviewRoute,
    onBack: () -> Unit,
    imageVersion: Int = 0,
    pagerState: PagerState? = null,
    titleContent: (@Composable (Int) -> Unit)? = null,
    actionContent: @Composable RowScope.(String?, Int) -> Unit = { _, _ -> },
    onImageTap: ((String, Offset) -> Unit)? = null,
    imageOverlay: (@Composable (String, Rect) -> Unit)? = null,
    fullscreen: Boolean = false,
    pagingEnabled: Boolean = true,
) {
    val context = LocalPlatformContext.current
    var showBars by remember { mutableStateOf(true) }
    val barsLocked by rememberUpdatedState(fullscreen)
    val barsVisible = showBars && !fullscreen
    val onToggleBars = { if (!barsLocked) showBars = !showBars }
    val topBarLayer = rememberGraphicsLayer()

    val previewItems = route.items
    val previewUris = remember(previewItems) { previewItems.map { it.uri } }
    val singleItem = previewItems.singleOrNull()
    val activePagerState =
        pagerState
            ?: rememberPagerState(
                pageCount = { previewItems.size.coerceAtLeast(1) },
            )
    val currentPage = activePagerState.currentPage
    val imageContent: @Composable (ImagePreviewItem) -> Unit = { item ->
        key(item.uri, imageVersion) {
            UriImage(
                imageLoader = imageLoader,
                uri = item.uri,
                version = imageVersion,
                onToggleBars = onToggleBars,
                onImageTap = onImageTap,
                imageOverlay = imageOverlay,
            )
        }
    }

    systemBars(barsVisible)

    // 规则组示例图会连续横滑，但预取并发限制在 2，避免与首图显示请求抢带宽。
    LaunchedEffect(previewUris) {
        if (previewUris.size <= 1) return@LaunchedEffect
        previewUris.drop(1).filter(::isNetworkUrl).chunked(2).forEach { uriBatch ->
            uriBatch
                .map { preloadUri ->
                    async {
                        imageLoader.execute(
                            buildPreviewImageRequest(
                                context = context,
                                uri = preloadUri,
                            ),
                        )
                    }
                }
                .awaitAll()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.background(Color.Black).fillMaxSize(),
    ) {
        when {
            singleItem != null -> imageContent(singleItem)

            previewItems.isNotEmpty() -> {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = activePagerState,
                    userScrollEnabled = pagingEnabled,
                    pageContent = { index -> imageContent(previewItems[index]) },
                )
            }
        }

        AnimatedVisibility(
            visible = barsVisible,
            enter = fadeIn(),
            exit =
                if (fullscreen) slideOutVertically(tween(250)) { -it } + fadeOut(tween(250))
                else fadeOut(),
            modifier = Modifier.zIndex(1f).fillMaxWidth(),
        ) {
            if (fullscreen) {
                // Keep the last visual frame, but remove the bar's hit targets and semantics.
                val height = with(LocalDensity.current) { topBarLayer.size.height.toDp() }
                Canvas(Modifier.fillMaxWidth().height(height)) { drawLayer(topBarLayer) }
            } else
                Column(
                    Modifier.drawWithContent {
                        topBarLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(topBarLayer)
                    },
                ) {
                    val currentPreviewItem = singleItem ?: previewItems.getOrNull(currentPage)
                    val currentUri = currentPreviewItem?.uri
                    GkTopAppBar(
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)),
                        navigationIcon = {
                            GkIconButton(
                                imageVector = GkIcons.ArrowBack,
                                onClick = onBack,
                                colors =
                                    IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            )
                        },
                        title = {
                            if (titleContent != null) {
                                titleContent(currentPage)
                            } else {
                                val baseTitle = route.title?.takeIf { it.isNotBlank() }
                                val itemTitle =
                                    currentPreviewItem?.let(::buildPreviewSubtitle)?.takeIf {
                                        it.isNotBlank() && it != baseTitle
                                    }
                                when {
                                    baseTitle != null && itemTitle != null -> {
                                        Column {
                                            PreviewBaseTitle(baseTitle)
                                            Text(
                                                text = itemTitle,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.MiddleEllipsis,
                                                style =
                                                    MaterialTheme.typography.titleSmall.copy(
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        fontWeight = FontWeight.Normal,
                                                    ),
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
                                            style =
                                                MaterialTheme.typography.titleLarge.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                ),
                                        )
                                    }
                                }
                            }
                        },
                        actions = { actionContent(currentUri, currentPage) },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                navigationIconContentColor = Color.White,
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White,
                            ),
                    )
                }
        }
        if (!fullscreen && previewItems.size > 1) {
            AnimatedVisibility(
                visible = barsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier.align(Alignment.BottomCenter).zIndex(1f).offset(y = -(maxHeight / 4)),
            ) {
                Text(
                    text =
                        stringResource(
                            Res.string.progress_fraction,
                            currentPage + 1,
                            previewItems.size,
                        ),
                    modifier =
                        Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }
    }
}

@Composable
private fun UriImage(
    imageLoader: ImageLoader,
    uri: String,
    version: Int,
    onToggleBars: () -> Unit = {},
    onImageTap: ((String, Offset) -> Unit)?,
    imageOverlay: (@Composable (String, Rect) -> Unit)?,
) {
    val context = LocalPlatformContext.current

    val isNetworkImage = remember(uri) { isNetworkUrl(uri) }
    val phaseResourceFlow = remember(uri, version) { MutableStateFlow<StringResource?>(null) }
    val phaseResource by phaseResourceFlow.collectAsStateWithLifecycle()
    val phaseText = phaseResource?.let { stringResource(it) }

    // 手势层切至 Telephoto，loading / error 还是使用 AsyncImagePainter.State 统一驱动。
    val model =
        remember(uri, version) {
            buildPreviewImageRequest(
                context = context,
                uri = uri,
                listener =
                    object : EventListener() {
                        override fun onStart(request: ImageRequest) {
                            phaseResourceFlow.value = Res.string.image_requesting
                        }

                        override fun fetchStart(
                            request: ImageRequest,
                            fetcher: Fetcher,
                            options: Options,
                        ) {
                            phaseResourceFlow.value =
                                if (isNetworkImage) Res.string.image_downloading
                                else Res.string.image_reading
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
                    },
            )
        }
    val painter =
        rememberAsyncImagePainter(
            model = model,
            imageLoader = imageLoader,
        )
    val state by painter.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (val stateVal = state) {
            AsyncImagePainter.State.Empty -> Unit

            is AsyncImagePainter.State.Loading -> {
                Column(
                    modifier =
                        Modifier.fillMaxSize().pointerInput(uri) {
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
                        onImageTap = onImageTap,
                        imageOverlay = imageOverlay,
                    )
                }
            }

            is AsyncImagePainter.State.Error -> {
                val reload = { painter.restart() }
                Column(
                    modifier =
                        Modifier.fillMaxSize().pointerInput(uri) {
                            detectTapGestures(onTap = { onToggleBars() })
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier =
                            Modifier.pointerInput(uri) {
                                detectTapGestures(onTap = { reload() })
                            },
                        text = stringResource(Res.string.image_load_failed_retry),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    stateVal.result.throwable.message?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTelephotoApi::class)
@Composable
private fun ZoomableImageContent(
    uri: String,
    painter: Painter,
    onToggleBars: () -> Unit,
    onImageTap: ((String, Offset) -> Unit)?,
    imageOverlay: (@Composable (String, Rect) -> Unit)?,
) {
    // 每个 pager page 都独立持有一个 ZoomableState，避免翻页后复用缩放位置。
    val zoomableState = rememberZoomableState()
    val intrinsicSize = painter.intrinsicSize
    // Telephoto may retain its original non-null click callback when a pager page is reused.
    val currentOnImageTap by rememberUpdatedState(onImageTap)
    var isInspectionTap by remember { mutableStateOf(true) }
    val inspectGestureModifier =
        if (onImageTap == null) Modifier
        else
            Modifier.pointerInput(Unit) {
                // With paging locked and no room to pan, a swipe may reach Telephoto's click
                // callback.
                awaitEachGesture {
                    val down =
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    isInspectionTap = true
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (
                            (finger.position - down.position).getDistance() >
                                viewConfiguration.touchSlop ||
                                event.changes.any { it.id != down.id && it.pressed }
                        ) {
                            isInspectionTap = false
                            break
                        }
                        if (!finger.pressed) break
                    }
                }
            }
    fun imageBounds() =
        with(zoomableState.coordinateSystem) {
            contentBounds(clipToViewport = false).rectIn(CoordinateSpace.Viewport)
        }

    // Image() 的绘制区域和实际图片内容边界并不总是完全一致。
    // 把内容位置告诉 Telephoto 后，边缘检测和与 pager 的手势协同会更稳定。
    LaunchedEffect(uri, intrinsicSize) {
        if (
            intrinsicSize != Size.Unspecified &&
                intrinsicSize.width > 0f &&
                intrinsicSize.height > 0f
        ) {
            zoomableState.setContentLocation(
                ZoomableContentLocation.scaledInsideAndCenterAligned(intrinsicSize),
            )
        }
    }

    // 限制图片成功状态下的深色画布背景，防止非必要全局黑色背景不跟随主题
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).then(inspectGestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier =
                Modifier.fillMaxSize()
                    .zoomable(
                        state = zoomableState,
                        onClick = { point ->
                            val inspectTap = currentOnImageTap
                            if (inspectTap == null) {
                                onToggleBars()
                            } else if (isInspectionTap) {
                                val bounds = imageBounds()
                                if (bounds.contains(point)) {
                                    inspectTap(
                                        uri,
                                        Offset(
                                            (point.x - bounds.left) / bounds.width,
                                            (point.y - bounds.top) / bounds.height,
                                        ),
                                    )
                                }
                            }
                        },
                    ),
            contentScale = ContentScale.Inside,
            alignment = Alignment.Center,
        )
        if (imageOverlay != null && zoomableState.contentTransformation.isSpecified) {
            imageOverlay(uri, imageBounds())
        }
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
                diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED)
            }
        }
        .build()
}

private fun buildPreviewSubtitle(item: ImagePreviewItem): String? {
    val titles = buildList {
        item.title?.takeIf { it.isNotBlank() }?.let(::add)
        item.titles.mapNotNull { it.takeIf(String::isNotBlank) }.forEach(::add)
    }
        .distinct()
    return titles.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}

private fun isNetworkUrl(value: String) =
    value.startsWith("http://", true) || value.startsWith("https://", true)
