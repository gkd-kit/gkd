package li.gkd.app.snapshot

import li.gkd.app.model.AttrInfo
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.NodeInfo

data class SnapshotNodeRow(
    val id: Int,
    val depth: Int,
    val isLastSibling: Boolean = true,
    val continuationDepths: List<Int> = emptyList(),
)

data class SnapshotNodeRelations(
    val parentId: Int?,
    val previousSiblingId: Int?,
    val nextSiblingId: Int?,
    val firstChildId: Int?,
)

/** An index over one immutable snapshot. IDs need not equal array offsets. */
class SnapshotNodeTree(val snapshot: ComplexSnapshot) {
    val nodes = snapshot.nodes.associateBy { it.id }
    val children = snapshot.nodes.groupBy { it.pid }
    val roots = snapshot.nodes.filter { it.pid !in nodes }

    fun relations(node: NodeInfo): SnapshotNodeRelations {
        val parent = nodes[node.pid]
        val siblings = parent?.let { children[it.id] }.orEmpty()
        val index = siblings.indexOfFirst { it.id == node.id }
        return SnapshotNodeRelations(
            parentId = parent?.id,
            previousSiblingId = siblings.getOrNull(index - 1)?.id,
            nextSiblingId = siblings.getOrNull(index + 1)?.id,
            firstChildId = children[node.id]?.firstOrNull()?.id,
        )
    }

    fun visibleRows(expanded: Set<Int>): List<SnapshotNodeRow> = buildList {
        val stack = ArrayDeque<SnapshotNodeRow>()
        roots.asReversed().forEach { stack.addLast(SnapshotNodeRow(it.id, 0)) }
        while (stack.isNotEmpty()) {
            val row = stack.removeLast()
            add(row)
            if (row.id in expanded) {
                val continuationDepths =
                    if (row.depth > 0 && !row.isLastSibling) row.continuationDepths + row.depth
                    else row.continuationDepths
                val siblings = children[row.id].orEmpty()
                for (index in siblings.indices.reversed()) {
                    stack.addLast(
                        SnapshotNodeRow(
                            siblings[index].id,
                            row.depth + 1,
                            isLastSibling = index == siblings.lastIndex,
                            continuationDepths = continuationDepths,
                        ),
                    )
                }
            }
        }
    }

    fun ancestors(id: Int): Set<Int> = buildSet {
        var parent = nodes[id]?.pid
        while (parent != null && parent in nodes && add(parent)) {
            parent = nodes[parent]?.pid
        }
    }

    fun hit(x: Float, y: Float): List<Int> {
        val hits =
            snapshot.nodes.filter { n ->
                n.attr.let {
                    it.hasArea && x >= it.left && x <= it.right && y >= it.top && y <= it.bottom
                }
            }
        if (hits.size <= 1) return hits.map { it.id }

        val hitsById = hits.associateBy { it.id }
        val ancestorContainers = buildSet {
            for (hit in hits) {
                for (ancestorId in ancestors(hit.id)) {
                    if (hitsById[ancestorId]?.attr?.contains(hit.attr) == true) add(ancestorId)
                }
            }
        }
        val candidates = hits.filterNot { it.id in ancestorContainers }
        if (candidates.size <= 1) return candidates.map { it.id }

        val branchContainers = buildSet {
            for (inner in candidates) {
                val ancestors = ancestors(inner.id)
                for (outer in candidates) {
                    // The parent must be on the inner node's path, or be the virtual root.
                    if (
                        outer.id != inner.id &&
                            (outer.pid in ancestors || outer.pid !in nodes) &&
                            outer.attr.contains(inner.attr) &&
                            !inner.attr.contains(outer.attr)
                    ) {
                        add(outer.id)
                    }
                }
            }
        }
        return candidates
            .filterNot { it.id in branchContainers }
            .sortedBy { it.attr.width.toLong() * it.attr.height }
            .map { it.id }
    }
}

private fun AttrInfo.contains(inner: AttrInfo): Boolean =
    left <= inner.left && top <= inner.top && right >= inner.right && bottom >= inner.bottom
