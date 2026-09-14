package li.gkd.app.feature.snapshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import li.gkd.app.data.ComplexSnapshot
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.json
import kotlin.math.roundToInt

@Serializable
data class SnapshotInspectRoute(val snapshotId: Long) : NavKey

private data class InspectState(
    val snapshot: ComplexSnapshot,
    val bitmap: Bitmap,
    val contexts: Map<Int, SelectorGen.NodeContext>,
    val roots: List<Int>,
    val children: Map<Int, List<Int>>,
    val autoDetected: List<SelectorGen.Candidate>,
)

@Composable
fun SnapshotInspectPage(route: SnapshotInspectRoute) {
    val mainVm = LocalMainViewModel.current
    val scope = rememberCoroutineScope()
    var state by remember(route.snapshotId) { mutableStateOf<InspectState?>(null) }
    var loadError by remember(route.snapshotId) { mutableStateOf<String?>(null) }
    var selectedId by remember(route.snapshotId) { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val expanded = remember(route.snapshotId) { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(route.snapshotId) {
        withContext(Dispatchers.IO) {
            try {
                val text = SnapshotRepository.snapshotFile(route.snapshotId).readText()
                val snapshot = json.decodeFromString<ComplexSnapshot>(text)
                val bitmap = BitmapFactory.decodeFile(
                    SnapshotRepository.screenshotFile(route.snapshotId).absolutePath,
                ) ?: error("截图解码失败")
                val (contexts, roots) = SelectorGen.buildContexts(snapshot.nodes)
                val children = snapshot.nodes
                    .groupBy { it.pid }
                    .mapValues { (_, v) -> v.sortedBy { it.attr.index }.map { it.id } }
                val detected = SelectorGen.autoDetect(contexts)
                withContext(Dispatchers.Main) {
                    state = InspectState(snapshot, bitmap, contexts, roots, children, detected)
                    // 默认展开根节点, 便于浏览
                    roots.take(2).forEach { expanded[it] = true }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadError = e.message ?: e.toString()
                }
            }
        }
    }

    fun saveCandidates(candidates: List<SelectorGen.Candidate>) {
        val st = state ?: return
        if (candidates.isEmpty() || saving) return
        scope.launch {
            saving = true
            val result = runCatching {
                val appName = st.snapshot.appInfo?.name ?: st.snapshot.appId
                val group = SelectorGen.buildGroup(
                    appId = st.snapshot.appId,
                    appName = appName,
                    candidates = candidates,
                )
                SubscriptionRepository.saveRuleGroupToLocalStorage(
                    appId = st.snapshot.appId,
                    appName = appName,
                    group = group,
                )
            }
            saving = false
            result.fold(
                onSuccess = { key ->
                    toast("已保存到本地订阅(规则组key=$key), 立即生效")
                    mainVm.popPage()
                },
                onFailure = { e ->
                    toast("保存失败: ${e.message}")
                },
            )
        }
    }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                modifier = Modifier.fillMaxWidth(),
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popPage() },
                    )
                },
                title = {
                    val st = state
                    Text(
                        text = "快照审查 · ${st?.snapshot?.appInfo?.name ?: "加载中"}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
    ) { contentPadding ->
        val st = state
        val error = loadError
        when {
            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { Text("加载失败: $error") }

            st == null -> Box(
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(contentPadding),
            ) {
                // ===== 截图区(可点击选节点, 选中节点红框) =====
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.38f),
                ) {
                    val density = LocalDensity.current
                    val imageRatio = st.bitmap.width.toFloat() / st.bitmap.height
                    val boxRatio = maxWidth / maxHeight
                    val displayW: Dp
                    val displayH: Dp
                    if (imageRatio > boxRatio) {
                        displayW = maxWidth
                        displayH = maxWidth / imageRatio
                    } else {
                        displayH = maxHeight
                        displayW = maxHeight * imageRatio
                    }
                    val scaleX = with(density) { displayW.toPx() } / st.snapshot.screenWidth
                    val scaleY = with(density) { displayH.toPx() } / st.snapshot.screenHeight
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(displayW, displayH)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            .pointerInput(st) {
                                detectTapGestures { pos ->
                                    val sx = pos.x / scaleX
                                    val sy = pos.y / scaleY
                                    selectedId = findNodeAt(st, sx, sy)?.node?.id
                                }
                            },
                    ) {
                        Image(
                            bitmap = st.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                        val sel = selectedId?.let { st.contexts[it] }
                        if (sel != null) {
                            val a = sel.node.attr
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (a.left * scaleX).roundToInt(),
                                            (a.top * scaleY).roundToInt(),
                                        )
                                    }
                                    .size(
                                        with(density) { (a.width * scaleX).toDp() },
                                        with(density) { (a.height * scaleY).toDp() },
                                    )
                                    .background(Color.Red.copy(alpha = 0.18f))
                                    .border(2.dp, Color.Red),
                            )
                        }
                    }
                }

                // ===== 选中节点属性面板 =====
                val selectedCtx = selectedId?.let { st.contexts[it] }
                if (selectedCtx != null) {
                    val a = selectedCtx.node.attr
                    val selector = remember(selectedId) {
                        SelectorGen.generateSelector(selectedCtx)
                    }
                    val path = remember(selectedId) {
                        SelectorGen.nodePathSelector(selectedCtx, st.contexts)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "@${a.name?.substringAfterLast('.')}  (${a.width}×${a.height})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (!a.text.isNullOrEmpty()) {
                            Text("text: ${a.text}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!a.desc.isNullOrEmpty()) {
                            Text("desc: ${a.desc}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!a.id.isNullOrEmpty()) {
                            Text("id: ${a.id}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!a.vid.isNullOrEmpty()) {
                            Text("vid: ${a.vid}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "clickable=${a.clickable}  visible=${a.visibleToUser}  childCount=${a.childCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        if (selector.isNotEmpty()) {
                            Text("选择器:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                selector,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("节点路径:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            path,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { copyText(selector) }, enabled = selector.isNotEmpty()) {
                                Text("复制选择器")
                            }
                            TextButton(onClick = { copyText(path) }) {
                                Text("复制路径")
                            }
                        }
                        Button(
                            onClick = {
                                saveCandidates(listOf(SelectorGen.Candidate("手动", selector)))
                            },
                            enabled = selector.isNotEmpty() && !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (saving) "保存中..." else "保存此节点为规则")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    Text(
                        text = "点击截图或下方节点树选择节点\n绿色圆点=可点击节点",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                // ===== 节点树 =====
                val visibleNodes by remember(st) {
                    derivedStateOf {
                        buildList {
                            fun dfs(id: Int, depth: Int) {
                                add(id to depth)
                                if (expanded[id] == true) {
                                    st.children[id]?.forEach { dfs(it, depth + 1) }
                                }
                            }
                            st.roots.forEach { dfs(it, 0) }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxWidth(),
                ) {
                    items(visibleNodes, key = { it.first }) { (id, depth) ->
                        val ctx = st.contexts[id] ?: return@items
                        val a = ctx.node.attr
                        val hasChildren = st.children[id]?.isNotEmpty() == true
                        val isSelected = selectedId == id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable { selectedId = id }
                                .padding(
                                    start = (depth * 14).dp + 8.dp,
                                    top = 5.dp,
                                    bottom = 5.dp,
                                    end = 8.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (hasChildren) {
                                val isOpen = expanded[id] == true
                                androidx.compose.material3.Icon(
                                    imageVector = if (isOpen) {
                                        PerfIcon.ExpandLess
                                    } else {
                                        PerfIcon.ExpandMore
                                    },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) { expanded[id] = !(expanded[id] == true) },
                                )
                            } else {
                                Spacer(Modifier.width(18.dp))
                            }
                            Text(
                                text = buildString {
                                    append(a.name?.substringAfterLast('.') ?: "NULL")
                                    val t = a.text?.trim().orEmpty()
                                        .ifEmpty { a.desc?.trim().orEmpty() }
                                    if (t.isNotEmpty()) append("  \"$t\"")
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (a.clickable) {
                                Box(
                                    Modifier
                                        .padding(start = 4.dp)
                                        .size(6.dp)
                                        .background(Color(0xFF4CAF50), CircleShape),
                                )
                            }
                        }
                    }
                }

                // ===== 底部: 自动识别保存 =====
                if (st.autoDetected.isNotEmpty()) {
                    Button(
                        onClick = { saveCandidates(st.autoDetected) },
                        enabled = !saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("一键保存 ${st.autoDetected.size} 条识别结果(跳过/关闭/知道了...)")
                    }
                }
            }
        }
    }
}

private fun findNodeAt(
    state: InspectState,
    x: Float,
    y: Float,
): SelectorGen.NodeContext? {
    return state.contexts.values
        .filter { c ->
            val a = c.node.attr
            a.left <= x && x < a.right && a.top <= y && y < a.bottom
        }
        .minByOrNull { c -> c.node.attr.width.toFloat() * c.node.attr.height }
}
