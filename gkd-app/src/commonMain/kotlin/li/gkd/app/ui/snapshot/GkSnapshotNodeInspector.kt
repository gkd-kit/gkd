package li.gkd.app.ui.snapshot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import li.gkd.app.model.NodeInfo
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotNodeRow
import li.gkd.app.snapshot.SnapshotNodeTree
import li.gkd.app.state.Loadable
import li.gkd.app.ui.component.GkDialog
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkSizedIconButton
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.component.LocalEditorWindowInsets
import li.gkd.app.ui.component.gkPageBottomSpace
import li.gkd.app.util.copyText
import org.jetbrains.compose.resources.stringResource

private val NodeIndent = 8.dp
private val NodeRowPadding = 4.dp
private val NodeBranchSize = 24.dp
private val NodeLeadingWidth = NodeRowPadding + NodeBranchSize
private val NodeLabelRevealWidth = 96.dp
private const val MinExpandedSheetFraction = .3f
private val NodeDialogButtonSize = 32.dp
private val NodeDialogIconSize = 18.dp

private data class SnapshotOutlineSegment(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

private fun mergeSnapshotOutline(nodes: List<NodeInfo>): List<SnapshotOutlineSegment> {
    val horizontal = mutableMapOf<Int, MutableList<IntRange>>()
    val vertical = mutableMapOf<Int, MutableList<IntRange>>()
    for (node in nodes) {
        val attr = node.attr
        if (!attr.hasArea) continue
        val x = attr.left..attr.right
        val y = attr.top..attr.bottom
        horizontal.getOrPut(y.first) { mutableListOf() }.add(x)
        horizontal.getOrPut(y.last) { mutableListOf() }.add(x)
        vertical.getOrPut(x.first) { mutableListOf() }.add(y)
        vertical.getOrPut(x.last) { mutableListOf() }.add(y)
    }
    return buildList {
        fun merge(groups: Map<Int, MutableList<IntRange>>, horizontalEdge: Boolean) {
            for ((coordinate, ranges) in groups) {
                ranges.sortBy { it.first }
                var start = ranges.first().first
                var end = ranges.first().last
                fun emit() {
                    add(
                        if (horizontalEdge)
                            SnapshotOutlineSegment(start, coordinate, end, coordinate)
                        else SnapshotOutlineSegment(coordinate, start, coordinate, end),
                    )
                }
                for (i in 1 until ranges.size) {
                    val range = ranges[i]
                    if (range.first > end) {
                        emit()
                        start = range.first
                        end = range.last
                    } else {
                        end = maxOf(end, range.last)
                    }
                }
                emit()
            }
        }
        merge(horizontal, horizontalEdge = true)
        merge(vertical, horizontalEdge = false)
    }
}

@Composable
fun GkSnapshotNodeOutline(tree: SnapshotNodeTree, selectedId: Int?, bounds: Rect) {
    val highlight = MaterialTheme.colorScheme.primary
    val snapshot = tree.snapshot
    if (snapshot.screenWidth <= 0 || snapshot.screenHeight <= 0 || bounds.isEmpty) return
    val sx = bounds.width / snapshot.screenWidth
    val sy = bounds.height / snapshot.screenHeight
    val segments = remember(snapshot) { mergeSnapshotOutline(snapshot.nodes) }
    val outline =
        remember(segments, bounds, sx, sy) {
            Path().apply {
                for (segment in segments) {
                    moveTo(bounds.left + segment.x1 * sx, bounds.top + segment.y1 * sy)
                    lineTo(bounds.left + segment.x2 * sx, bounds.top + segment.y2 * sy)
                }
            }
        }
    val selectedBounds = selectedId?.let(tree.nodes::get)?.attr?.takeIf { it.hasArea }
    val center = selectedBounds?.let { attr ->
        if (
            attr.left >= 0 &&
                attr.top >= 0 &&
                attr.right <= snapshot.screenWidth &&
                attr.bottom <= snapshot.screenHeight
        ) {
            Offset(
                attr.left + (attr.right - attr.left) / 2f,
                attr.top + (attr.bottom - attr.top) / 2f,
            )
        } else null
    }
    Canvas(Modifier.fillMaxSize()) {
        val visibleImage = bounds.intersect(Rect(Offset.Zero, size))
        if (visibleImage.isEmpty) return@Canvas
        clipRect(visibleImage.left, visibleImage.top, visibleImage.right, visibleImage.bottom) {
            drawPath(outline, Color.Cyan.copy(alpha = .35f), style = Stroke(width = 1.dp.toPx()))
            selectedBounds?.let { a ->
                val rect =
                    Rect(
                        bounds.left + a.left * sx,
                        bounds.top + a.top * sy,
                        bounds.left + a.right * sx,
                        bounds.top + a.bottom * sy,
                    )
                if (rect.overlaps(visibleImage)) {
                    drawRect(highlight.copy(alpha = .16f), rect.topLeft, rect.size)
                    drawRect(
                        highlight,
                        rect.topLeft,
                        rect.size,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            if (center != null) {
                val x = bounds.left + center.x * sx
                val y = bounds.top + center.y * sy
                val crosshair =
                    Path().apply {
                        moveTo(bounds.left, y)
                        lineTo(bounds.right, y)
                        moveTo(x, bounds.top)
                        lineTo(x, bounds.bottom)
                    }
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
                drawPath(
                    crosshair,
                    Color.Black,
                    style = Stroke(width = 3.dp.toPx(), pathEffect = dash),
                )
                drawPath(
                    crosshair,
                    Color.White,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                )
            }
        }
    }
}

@Composable
fun GkSnapshotOverlapDialog(
    tree: SnapshotNodeTree,
    candidates: List<Int>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    GkDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            Res.string.snapshot_node_overlap,
                            candidates.size.toString(),
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    GkSizedIconButton(
                        imageVector = GkIcons.Close,
                        size = NodeDialogButtonSize,
                        iconSize = NodeDialogIconSize,
                        contentDescription = stringResource(Res.string.action_close),
                        onClick = onDismiss,
                    )
                }
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(candidates, key = { it }) { id ->
                        val node = tree.nodes.getValue(id)
                        val attr = node.attr
                        val selected = id == selectedId
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .selectable(selected = selected, onClick = { onSelect(id) })
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                                Row(Modifier.fillMaxWidth()) {
                                    SnapshotNodeRowLabel(
                                        node,
                                        selected,
                                        showNodeId = false,
                                    )
                                }
                            }
                            Text(
                                stringResource(
                                    Res.string.snapshot_node_overlap_details,
                                    node.id.toString(),
                                    "[${attr.left}, ${attr.top}, ${attr.right}, ${attr.bottom}]",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color =
                                    if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

class SnapshotNodeLocateRequest(val id: Int)

@Composable
fun GkSnapshotNodeTreeSheet(
    visible: Boolean,
    state: Loadable<SnapshotNodeTree>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onShowProperties: (Int) -> Unit,
    locateRequest: SnapshotNodeLocateRequest?,
    onHide: () -> Unit,
    onRetry: () -> Unit,
) {
    var fraction by remember { mutableFloatStateOf(.52f) }
    val visibility = remember { MutableTransitionState(false) }.apply { targetState = visible }
    val treeUiState = state.value?.let { rememberSnapshotNodeTreeState(it, locateRequest, visible) }
    val sheetInsets = LocalEditorWindowInsets.current ?: WindowInsets.safeDrawing
    val bottomInsets = sheetInsets.only(WindowInsetsSides.Bottom)
    val frameInsets = sheetInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(frameInsets).clipToBounds(),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it },
            exit = slideOutVertically(tween(250)) { it },
        ) {
            var dragFraction by remember { mutableFloatStateOf(fraction) }
            var dragging by remember { mutableStateOf(false) }
            LaunchedEffect(visible) {
                if (visible) dragFraction = fraction
            }
            val density = LocalDensity.current
            val bottomPadding = bottomInsets.asPaddingValues().calculateBottomPadding()
            val availableHeight = (maxHeight - bottomPadding).coerceAtLeast(0.dp)
            val availablePx = with(density) { availableHeight.toPx() }
            val hideVelocityPxPerSecond = with(density) { 900.dp.toPx() }
            val headerHeight = 24.dp
            val maxSheetHeight = availableHeight.coerceAtLeast(headerHeight)
            val heightFraction = if (dragging || !visible) dragFraction else fraction
            val sheetHeight by
                animateDpAsState(
                    targetValue =
                        (availableHeight * heightFraction).coerceIn(headerHeight, maxSheetHeight),
                    animationSpec = if (dragging) snap() else tween(250),
                )
            val dragState = rememberDraggableState { delta ->
                dragFraction =
                    (dragFraction - delta / availablePx).coerceIn(MinExpandedSheetFraction, 1f)
            }
            val headerDrag =
                Modifier.draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        dragFraction = fraction
                        dragging = true
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        if (
                            velocity >= hideVelocityPxPerSecond ||
                                dragFraction <= MinExpandedSheetFraction
                        ) {
                            onHide()
                        } else {
                            fraction = dragFraction
                        }
                    },
                )
            Surface(
                Modifier.fillMaxWidth(),
                shape = BottomSheetDefaults.ExpandedShape,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = .88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.windowInsetsPadding(bottomInsets)) {
                    Box(
                        Modifier.fillMaxWidth().then(headerDrag).height(headerHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(width = 32.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                    }
                    Column(Modifier.height(sheetHeight - headerHeight)) {
                        when (state) {
                            Loadable.Loading ->
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            is Loadable.Failure ->
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        state.cause.message
                                            ?: stringResource(Res.string.snapshot_node_load_failed),
                                    )
                                    TextButton(onClick = onRetry) {
                                        Text(stringResource(Res.string.snapshot_node_retry))
                                    }
                                }
                            is Loadable.Ready ->
                                if (treeUiState != null)
                                    key(state.value.snapshot.id) {
                                        NodeTreeContent(
                                            state.value,
                                            selectedId,
                                            onSelect,
                                            onShowProperties,
                                            treeUiState,
                                        )
                                    }
                        }
                    }
                }
            }
        }
    }
}

private class SnapshotNodeTreeState(tree: SnapshotNodeTree) {
    val listState = LazyListState()
    val horizontalState = ScrollState(0)
    var expanded by mutableStateOf(tree.roots.map { it.id }.toSet())
    var rows by mutableStateOf<List<SnapshotNodeRow>>(emptyList())
    var contentWidthPx by mutableIntStateOf(0)
    var locateTarget by mutableStateOf<Int?>(null)
}

@Composable
private fun rememberSnapshotNodeTreeState(
    tree: SnapshotNodeTree,
    locateRequest: SnapshotNodeLocateRequest?,
    visible: Boolean,
): SnapshotNodeTreeState {
    val state = remember(tree) { SnapshotNodeTreeState(tree) }
    val density = LocalDensity.current
    LaunchedEffect(tree, locateRequest) {
        state.locateTarget = null
        if (!visible || locateRequest == null) return@LaunchedEffect
        val id = locateRequest.id
        val ancestors = withContext(Dispatchers.Default) { tree.ancestors(id) }
        state.expanded += ancestors
        state.locateTarget = id
    }
    val expanded = state.expanded
    LaunchedEffect(tree, expanded) {
        val rows = withContext(Dispatchers.Default) { tree.visibleRows(expanded) }
        if (rows.size < state.rows.size) state.contentWidthPx = 0
        state.rows = rows
    }
    val target = state.locateTarget
    val rows = state.rows
    LaunchedEffect(tree, visible, rows, target) {
        if (!visible || target == null) return@LaunchedEffect
        val index = rows.indexOfFirst { it.id == target }
        if (index < 0) return@LaunchedEffect
        state.listState.scrollToItem(index)
        val indent = NodeIndent * rows[index].depth
        val labelStart = with(density) { (indent + NodeLeadingWidth).roundToPx() }
        val revealWidth = with(density) { NodeLabelRevealWidth.roundToPx() }
        val scroll = state.horizontalState
        val viewportEnd = scroll.value + scroll.viewportSize
        if (labelStart < scroll.value || labelStart + revealWidth > viewportEnd) {
            scroll.scrollTo(with(density) { indent.roundToPx() })
        }
        state.locateTarget = null
    }
    return state
}

@Composable
private fun ColumnScope.NodeTreeContent(
    tree: SnapshotNodeTree,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onShowProperties: (Int) -> Unit,
    state: SnapshotNodeTreeState,
) {
    val rows = state.rows
    val horizontalState = state.horizontalState
    val showPropertiesLabel = stringResource(Res.string.snapshot_node_properties)
    if (rows.isEmpty()) {
        if (tree.nodes.isEmpty()) {
            Text(stringResource(Res.string.snapshot_node_empty), Modifier.padding(8.dp))
        }
        return
    }
    val selectionMarkerColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        Modifier.weight(1f).fillMaxWidth().drawWithContent {
            drawContent()
            // Draw in the viewport so the marker stays at the edge while labels scroll sideways.
            val row =
                state.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == selectedId }
            if (row != null) {
                clipRect {
                    drawRect(
                        selectionMarkerColor,
                        topLeft = Offset(0f, row.offset.toFloat()),
                        size = Size(3.dp.toPx(), row.size.toFloat()),
                    )
                }
            }
        },
    ) {
        val density = LocalDensity.current
        val deepest = remember(rows) { rows.maxOfOrNull { it.depth } ?: 0 }
        val minTreeWidth =
            maxOf(maxWidth, NodeIndent * deepest + NodeLeadingWidth + NodeLabelRevealWidth)
        val minTreeWidthPx = with(density) { minTreeWidth.roundToPx() }
        val rowWidth = maxOf(minTreeWidth, with(density) { state.contentWidthPx.toDp() })
        val horizontalDrag = rememberDraggableState { delta ->
            horizontalState.dispatchRawDelta(delta)
        }
        LazyColumn(
            Modifier.fillMaxHeight()
                .draggable(
                    state = horizontalDrag,
                    orientation = Orientation.Horizontal,
                    enabled = horizontalState.maxValue > 0,
                    reverseDirection = LocalLayoutDirection.current == LayoutDirection.Ltr,
                )
                .horizontalScroll(horizontalState)
                .widthIn(min = minTreeWidth)
                .onSizeChanged { size ->
                    if (state.rows === rows) {
                        state.contentWidthPx = if (size.width > minTreeWidthPx) size.width else 0
                    }
                },
            state = state.listState,
        ) {
            items(rows, key = { it.id }, contentType = { "node" }) { row ->
                val node = tree.nodes.getValue(row.id)
                val selected = row.id == selectedId
                Row(
                    Modifier.widthIn(min = rowWidth)
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                        )
                        .semantics { this.selected = selected }
                        .combinedClickable(
                            onLongClickLabel = showPropertiesLabel,
                            onLongClick = { onShowProperties(row.id) },
                            onClick = { onSelect(row.id) },
                        )
                        .heightIn(min = 32.dp)
                        .padding(
                            start = NodeRowPadding + NodeIndent * row.depth,
                            end = NodeRowPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.id in tree.children) {
                        val isExpanded = row.id in state.expanded
                        val rotation by
                            animateFloatAsState(
                                targetValue = if (isExpanded) 0f else -90f,
                                animationSpec = tween(150),
                                label = "nodeBranchRotation",
                            )
                        GkSizedIconButton(
                            size = NodeBranchSize,
                            onClick = {
                                state.expanded =
                                    if (row.id in state.expanded) state.expanded - row.id
                                    else state.expanded + row.id
                            },
                        ) {
                            GkIcon(
                                GkIcons.ExpandMore,
                                contentDescription =
                                    stringResource(
                                        if (isExpanded) Res.string.snapshot_node_collapse
                                        else Res.string.snapshot_node_expand,
                                    ),
                                modifier = Modifier.size(16.dp).rotate(rotation),
                            )
                        }
                    } else Spacer(Modifier.width(NodeBranchSize))
                    SnapshotNodeRowLabel(node, selected)
                }
            }
            gkPageBottomSpace(height = GkPageBottomSpaceDefaults.CompactHeight)
        }
    }
}

@Composable
private fun RowScope.SnapshotNodeRowLabel(
    node: NodeInfo,
    selected: Boolean = false,
    showNodeId: Boolean = true,
    showDetails: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val nameColor =
        when {
            !node.attr.visibleToUser -> colors.onSurfaceVariant
            selected -> colors.onSecondaryContainer
            else -> colors.onSurface
        }
    Text(
        text =
            buildAnnotatedString {
                if (showNodeId) {
                    withStyle(
                        SpanStyle(
                            color = colors.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                    ) {
                        append("#${node.id}")
                    }
                    append(' ')
                }
                withStyle(SpanStyle(color = nameColor)) {
                    append(
                        node.attr.name?.takeIf(String::isNotEmpty)?.substringAfterLast('.') ?: "🐔",
                    )
                }
                val vid = node.attr.vid
                if (showDetails && !vid.isNullOrEmpty()) {
                    append(' ')
                    withStyle(
                        SpanStyle(
                            color = colors.primary,
                            fontWeight = if (node.idQf == true) FontWeight.Bold else null,
                        ),
                    ) {
                        append(vid)
                    }
                }
            },
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
    if (!showDetails) return
    val text = node.attr.text?.takeIf { it.isNotEmpty() }
    val summary = text ?: node.attr.desc
    if (!summary.isNullOrEmpty()) {
        Text(
            text = " " + summary.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' '),
            modifier = Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (text != null && node.textQf == true) FontWeight.Bold else null,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GkSnapshotNodePropertiesDialog(
    tree: SnapshotNodeTree,
    node: NodeInfo,
    onSelect: (Int) -> Unit,
    onShowValue: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val relations = tree.relations(node)
    val attr = node.attr
    val propertyScrollState = rememberScrollState()
    // 保留复制时每行的分组，表格则逐项展示。
    val propertyLines =
        remember(node) {
            val idQuickFind = node.idQf == true
            val textQuickFind = node.textQf == true
            listOf(
                listOf(SnapshotNodeProperty("name", attr.name)),
                listOf(
                    SnapshotNodeProperty(
                        "id",
                        attr.id,
                        quickFind = idQuickFind && !attr.id.isNullOrEmpty(),
                    ),
                ),
                listOf(
                    SnapshotNodeProperty(
                        "vid",
                        attr.vid,
                        quickFind = idQuickFind && !attr.vid.isNullOrEmpty(),
                    ),
                ),
                listOf(
                    SnapshotNodeProperty(
                        "text",
                        attr.text,
                        quickFind = textQuickFind && !attr.text.isNullOrEmpty(),
                    ),
                ),
                listOf(SnapshotNodeProperty("desc", attr.desc)),
                listOf(
                    SnapshotNodeProperty("left", attr.left),
                    SnapshotNodeProperty("top", attr.top),
                    SnapshotNodeProperty("right", attr.right),
                    SnapshotNodeProperty("bottom", attr.bottom),
                ),
                listOf(
                    SnapshotNodeProperty("width", attr.width),
                    SnapshotNodeProperty("height", attr.height),
                ),
                listOf(
                    SnapshotNodeProperty("clickable", attr.clickable),
                    SnapshotNodeProperty("longClickable", attr.longClickable),
                ),
                listOf(
                    SnapshotNodeProperty("visibleToUser", attr.visibleToUser),
                ),
                listOf(
                    SnapshotNodeProperty("checkable", attr.checkable),
                    SnapshotNodeProperty("checked", attr.checked),
                ),
                listOf(
                    SnapshotNodeProperty("editable", attr.editable),
                    SnapshotNodeProperty("focusable", attr.focusable),
                ),
                listOf(
                    SnapshotNodeProperty("childCount", attr.childCount),
                    SnapshotNodeProperty("index", attr.index),
                    SnapshotNodeProperty("depth", attr.depth),
                ),
            )
        }
    GkDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.heightIn(max = 560.dp).padding(bottom = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionContainer(Modifier.weight(1f)) {
                        SnapshotNodeRowLabel(node = node, showDetails = false)
                    }
                    GkSizedIconButton(
                        imageVector = GkIcons.Close,
                        size = NodeDialogButtonSize,
                        iconSize = NodeDialogIconSize,
                        contentDescription = stringResource(Res.string.action_close),
                        onClick = onDismiss,
                    )
                }
                HorizontalDivider()
                SelectionContainer(Modifier.weight(1f, fill = false)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(propertyScrollState)) {
                        propertyLines.forEach { line ->
                            line.forEach { SnapshotNodePropertyRow(it, onShowValue) }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val cursors =
                        listOf(
                            Triple(
                                GkIcons.ArrowUpward,
                                Res.string.snapshot_node_parent,
                                relations.parentId,
                            ),
                            Triple(
                                GkIcons.ArrowBack,
                                Res.string.snapshot_node_before_sibling,
                                relations.previousSiblingId,
                            ),
                            Triple(
                                GkIcons.ArrowForward,
                                Res.string.snapshot_node_after_sibling,
                                relations.nextSiblingId,
                            ),
                            Triple(
                                GkIcons.ArrowDownward,
                                Res.string.snapshot_node_first_child,
                                relations.firstChildId,
                            ),
                        )
                    cursors.forEach { (icon, label, target) ->
                        GkSizedIconButton(
                            imageVector = icon,
                            size = NodeDialogButtonSize,
                            iconSize = NodeDialogIconSize,
                            contentDescription = stringResource(label),
                            enabled = target != null,
                            onClick = { target?.let(onSelect) },
                        )
                    }
                    GkSizedIconButton(
                        imageVector = GkIcons.ContentCopy,
                        size = NodeDialogButtonSize,
                        iconSize = NodeDialogIconSize,
                        contentDescription = stringResource(Res.string.snapshot_node_copy),
                        onClick = {
                            copyText(
                                buildString {
                                    append("#${node.id}  parent=${node.pid}")
                                    propertyLines.forEach { line ->
                                        append('\n')
                                        append(line.joinToString("  ") { "${it.name}=${it.value}" })
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

private data class SnapshotNodeProperty(
    val name: String,
    val value: Any?,
    val quickFind: Boolean = false,
)

private const val PropertyNameWeight = .4f

@Composable
private fun SnapshotNodePropertyRow(
    property: SnapshotNodeProperty,
    onShowValue: (String) -> Unit,
) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(PropertyNameWeight).padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                property.name,
                Modifier.weight(1f, fill = false),
                style = style,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (property.quickFind) {
                val hint = stringResource(Res.string.snapshot_node_property_quick_find)
                GkTooltipIconButtonBox(hint, showOnClick = true) {
                    GkIcon(
                        GkIcons.Check,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = hint,
                    )
                }
            }
        }
        val value = property.value
        val valueText = if (value is String) Json.encodeToString(value) else value.toString()
        var overflowed by remember(valueText) { mutableStateOf(false) }
        val openValue =
            if (overflowed) {
                Modifier.clickable(
                    onClickLabel = stringResource(Res.string.snapshot_node_show_full_value),
                    onClick = { onShowValue(value.toString()) },
                )
            } else Modifier
        Text(
            valueText,
            Modifier.weight(1f - PropertyNameWeight).then(openValue),
            style = style,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { overflowed = it.hasVisualOverflow },
            color =
                when {
                    overflowed -> MaterialTheme.colorScheme.primary
                    value == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
    }
}
