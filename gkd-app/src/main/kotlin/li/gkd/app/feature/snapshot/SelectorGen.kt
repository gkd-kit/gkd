package li.gkd.app.feature.snapshot

import li.gkd.app.data.NodeInfo
import li.gkd.app.data.RawSubscription.RawAppGroup
import li.gkd.app.data.RawSubscription.RawAppRule

/**
 * GMD(gkd-kit/inspect 网页审查工具) 核心算法的 Kotlin 原生移植。
 *
 * 移植自 inspect 前端两段代码, 语义与产出保持一致:
 * 1. generateSmartMatches —— 智能选择器生成(用于规则)
 * 2. getNodeSelectorText —— 节点路径选择器(用于复制定位)
 *
 * 自动识别有意排除 update 类关键词(更新/立即/查看等), 该类词在正常
 * 界面大量出现, 自动保存会误点正常按钮, 只保留低误伤类别。
 */
object SelectorGen {

    class NodeContext(
        val node: NodeInfo,
        val parent: NodeInfo?,
        val siblings: List<NodeInfo>,
        val ancestors: List<NodeInfo> = emptyList(),
    )

    data class Candidate(
        val label: String,
        val selector: String,
    )

    /** 构建 id→上下文 索引(含祖先链, 供路径回退使用); 返回 (contexts, 根节点id列表) */
    fun buildContexts(nodes: List<NodeInfo>): Pair<Map<Int, NodeContext>, List<Int>> {
        val byId = nodes.associateBy { it.id }
        val byPid = nodes.groupBy { it.pid }
        val contexts = mutableMapOf<Int, NodeContext>()
        nodes.forEach { n ->
            val parent = byId[n.pid]
            val siblings = byPid[n.pid]?.filter { it.id != n.id } ?: emptyList()
            val ancestors = mutableListOf<NodeInfo>()
            var p = parent
            while (p != null) {
                ancestors.add(p)
                p = byId[p.pid]
            }
            contexts[n.id] = NodeContext(n, parent, siblings, ancestors)
        }
        val roots = nodes.filter { it.pid == -1 || it.pid !in byId.keys }.map { it.id }
        return contexts to roots
    }

    // ==== 智能选择器生成 (generateSmartMatches 移植) ====

    private val skipRegex = Regex("跳过|skip", RegexOption.IGNORE_CASE)
    private val closeRegex = Regex("关闭|關閉|close|取消|✕|×|不再|残忍拒绝|忽略")
    private val knowRegex = Regex("知道了|我知道了|已知晓|已满|确定")
    private val dislikeRegex = Regex("不感兴趣|不再推荐|不喜欢")
    private val teenRegex = Regex("青少年|未成年|儿童")
    private val adRegex = Regex("广告|AD", RegexOption.IGNORE_CASE)

    private fun smallSize(width: Int, height: Int) =
        width > 8 && height > 8 && width < 120 && height < 120

    private fun isAppId(id: String?) =
        !id.isNullOrEmpty() && !id.contains("android:id/") && !id.contains("com.android")

    private fun shortName(name: String?): String =
        name?.substringAfterLast('.') ?: "View"

    fun generateSelector(ctx: NodeContext): String {
        val a = ctx.node.attr
        val text = a.text?.trim().orEmpty()
        val desc = a.desc?.trim().orEmpty()
        val name = a.name ?: "View"
        val pAttr = ctx.parent?.attr
        val clickable = a.clickable
        val clickableParent = pAttr?.clickable == true
        val parentName = pAttr?.name?.let { shortName(it) } ?: "ViewGroup"
        val hasAdSibling =
            ctx.siblings.any { s -> s.attr.text?.let { adRegex.containsMatchIn(it) } == true }

        if (isAppId(a.id) && text.isNotEmpty()) return "[id=\"${a.id}\"][text=\"$text\"]"
        if (isAppId(a.id)) return "[id=\"${a.id}\"]"
        if (!a.vid.isNullOrEmpty() && a.vid.length > 2 && text.isNotEmpty()) {
            return "[vid=\"${a.vid}\"]${if (clickable) "[clickable=true]" else ""}[text=\"$text\"]"
        }
        if (!a.vid.isNullOrEmpty() && a.vid.length > 2) {
            return "[vid=\"${a.vid}\"][visibleToUser=true]"
        }

        val leaf = a.childCount == 0

        fun buildTextExact(t: String) = "[text=\"$t\"][visibleToUser=true]"
        fun buildShortExact(t: String) =
            "${if (leaf) "[childCount=0]" else ""}[text=\"$t\"]" +
                "[text.length<${maxOf(6, t.length + 1)}][visibleToUser=true]"
        fun buildSkip() =
            if (clickableParent) {
                "@$parentName[clickable=true] > [text*=\"跳过\"][text.length<10][visibleToUser=true]"
            } else {
                "[text*=\"跳过\"][text.length<10][visibleToUser=true]"
            }

        fun buildCloseSmallIcon(): String {
            var s = "@$name[childCount=0][text=null][desc=null][id=null]" +
                "[visibleToUser=true][width<120 && height<120]"
            if (hasAdSibling) s += " + * > [text*=\"广告\" || text=\"AD\"]"
            return s
        }

        if (skipRegex.containsMatchIn(text) || skipRegex.containsMatchIn(desc)) return buildSkip()
        if (closeRegex.containsMatchIn(text) || desc.contains("closeButton")) {
            if (text.isEmpty() && smallSize(a.width, a.height) && clickable) {
                return buildCloseSmallIcon()
            }
            if (text.isNotEmpty()) {
                return if (clickableParent) {
                    "@$parentName[clickable=true] > ${buildShortExact(text)}"
                } else {
                    buildShortExact(text)
                }
            }
        }
        if (knowRegex.containsMatchIn(text)) {
            return if (clickableParent) {
                "@$parentName[clickable=true] > ${buildShortExact(text)}"
            } else {
                buildShortExact(text)
            }
        }
        if (dislikeRegex.containsMatchIn(text)) {
            return if (clickableParent) {
                "@$parentName > [text=\"$text\"][visibleToUser=true]"
            } else {
                buildTextExact(text)
            }
        }
        if (teenRegex.containsMatchIn(text)) {
            return "[text*=\"青少年\" || text*=\"未成年\" || text*=\"儿童\"]" +
                "[text.length<15]${if (leaf) "[childCount=0]" else ""}[visibleToUser=true]"
        }
        if (text.isEmpty() && desc.isNotEmpty()) {
            if (desc.contains("closeButton")) return "@[desc=\"closeButton\"][visibleToUser=true]"
            return "[desc=\"$desc\"][visibleToUser=true]"
        }
        if (text.isEmpty() && desc.isEmpty() && clickable && smallSize(a.width, a.height)) {
            return buildCloseSmallIcon()
        }
        if (text.isNotEmpty()) {
            if (text.length <= 2) {
                return "${if (clickable) "[clickable=true]" else ""}" +
                    "${if (leaf) "[childCount=0]" else ""}[text=\"$text\"][visibleToUser=true]"
            }
            if (text.length <= 8) {
                return "${if (leaf) "[childCount=0]" else ""}[text=\"$text\"][visibleToUser=true]"
            }
            val prefix = text.take(4)
            return "[text*=\"$prefix\"][text.length>=${text.length - 2}]" +
                "[text.length<=${text.length + 2}][visibleToUser=true]"
        }
        // ==== 无 id/vid/text/desc 特征的节点: 祖先路径回退 ====
        // 向上找最近的有稳定 id/vid 的祖先作锚点, 用 <N 路径语法定位
        // (GKD 语义: A <3 B = B 是 A 的父, 且 A 是 B 的第3个子节点)
        return ancestorPathSelector(ctx) ?: ""
    }

    /**
     * 祖先路径选择器(祖父搜索):
     * 向上最多 5 层找稳定锚点(应用自定义 id / vid), 生成
     * `@Target[childCount=0] < ParentName <2 [vid="anchor"]` 形式的路径。
     * 找不到锚点返回 null。
     */
    private fun ancestorPathSelector(ctx: NodeContext): String? {
        val ancestors = ctx.ancestors
        if (ancestors.isEmpty()) return null
        val anchorIdx = ancestors.take(5).indexOfFirst {
            isAppId(it.attr.id) || (!it.attr.vid.isNullOrEmpty() && it.attr.vid.length > 2)
        }
        if (anchorIdx == -1) return null
        val anchor = ancestors[anchorIdx]
        val anchorSel = when {
            isAppId(anchor.attr.id) -> "[id=\"${anchor.attr.id}\"]"
            else -> "[vid=\"${anchor.attr.vid}\"]"
        }
        val leaf = ctx.node.attr.childCount == 0
        val targetDesc = "@${shortName(ctx.node.attr.name)}" +
            (if (leaf) "[childCount=0]" else "") +
            "[visibleToUser=true]"
        val parts = mutableListOf(targetDesc)
        for (i in 0..anchorIdx) {
            val upper = ancestors[i]
            val lower = if (i == 0) ctx.node else ancestors[i - 1]
            val childIdx = lower.attr.index + 1
            val op = if (childIdx == 1) "<" else "<$childIdx"
            val desc = if (i == anchorIdx) anchorSel else "@${shortName(upper.attr.name)}"
            parts.add("$op $desc")
        }
        return parts.joinToString(" ")
    }

    // ==== 节点路径选择器 (getNodeSelectorText 移植) ====

    fun nodePathSelector(ctx: NodeContext, contexts: Map<Int, NodeContext>): String {
        fun build(node: NodeInfo, isFirst: Boolean, lastIndex: Int): String {
            val parentCtx = contexts[node.pid]
            if (parentCtx == null) {
                return if (isFirst) "[parent=null]" else "<$lastIndex [parent=null]"
            }
            if (node.idQf == true) {
                val key = if (!node.attr.vid.isNullOrEmpty()) "vid" else "id"
                val value = node.attr.vid ?: node.attr.id ?: ""
                return if (isFirst) "[$key=\"$value\"]" else "<$lastIndex [$key=\"$value\"]"
            }
            val short = node.attr.name?.substringAfterLast('.') ?: "NULL"
            val op = if (lastIndex == 1) "<" else "<$lastIndex"
            val parent = build(parentCtx.node, false, node.attr.index + 1)
            return if (isFirst) "@$short $parent" else "$op $short $parent"
        }
        return build(ctx.node, true, 1)
    }

    // ==== 自动识别广告关闭节点 ====

    /**
     * 扫描全部节点, 找疑似「跳广告按钮」:
     * visibleToUser + 关键词命中(或小图标特征) + 自身或父级可点击 + 选择器去重
     */
    fun autoDetect(contexts: Map<Int, NodeContext>): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()
        for (ctx in contexts.values) {
            val a = ctx.node.attr
            if (!a.visibleToUser) continue
            val text = a.text?.trim().orEmpty()
            val desc = a.desc?.trim().orEmpty()
            val label = when {
                skipRegex.containsMatchIn(text) || skipRegex.containsMatchIn(desc) -> "跳过"
                closeRegex.containsMatchIn(text) || desc.contains("closeButton") -> "关闭"
                knowRegex.containsMatchIn(text) -> "知道了"
                dislikeRegex.containsMatchIn(text) -> "不感兴趣"
                teenRegex.containsMatchIn(text) -> "青少年模式"
                text.isEmpty() && desc.isEmpty() && a.clickable &&
                    smallSize(a.width, a.height) -> "关闭图标"
                else -> null
            } ?: continue
            if (!a.clickable && ctx.parent?.attr?.clickable != true) continue
            val selector = generateSelector(ctx)
            if (selector.length < 8 || !seen.add(selector)) continue
            result.add(Candidate(label, selector))
        }
        return result
    }

    // ==== 规则组构造 (GMD「复制规则组」模板移植) ====

    /**
     * 构造规则组。key=0 占位, 保存时由 SubscriptionRepository 按本地订阅
     * 中该应用已有 key 自动分配(max+1), 从根本上避免 key 冲突。
     */
    fun buildGroup(
        appId: String,
        appName: String?,
        candidates: List<Candidate>,
    ): RawAppGroup = RawAppGroup(
        key = 0,
        name = "[快照] $appName",
        desc = "由快照审查自动生成",
        enable = null,
        scopeKeys = null,
        actionCdKey = null,
        actionMaximumKey = null,
        actionCd = null,
        actionDelay = null,
        fastQuery = null,
        matchRoot = null,
        actionMaximum = 1,
        priorityTime = null,
        priorityActionMaximum = null,
        order = null,
        forcedTime = null,
        matchDelay = null,
        matchTime = 10000,
        resetMatch = "app",
        snapshotUrls = null,
        excludeSnapshotUrls = null,
        exampleUrls = null,
        activityIds = null,
        excludeActivityIds = null,
        rules = candidates.mapIndexed { index, c ->
            RawAppRule(
                key = index,
                name = c.label,
                preKeys = null,
                action = null,
                position = null,
                swipeArg = null,
                matches = listOf(c.selector),
                excludeMatches = null,
                excludeAllMatches = null,
                anyMatches = null,
                actionCdKey = null,
                actionMaximumKey = null,
                actionCd = null,
                actionDelay = null,
                fastQuery = true,
                matchRoot = null,
                actionMaximum = null,
                priorityTime = null,
                priorityActionMaximum = null,
                order = null,
                forcedTime = null,
                matchDelay = null,
                matchTime = null,
                resetMatch = null,
                snapshotUrls = null,
                excludeSnapshotUrls = null,
                exampleUrls = null,
                activityIds = null,
                excludeActivityIds = null,
                versionCode = null,
                versionName = null,
            )
        },
        versionCode = null,
        versionName = null,
        ignoreGlobalGroupMatch = null,
    )
}
