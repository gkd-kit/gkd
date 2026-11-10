package li.gkd.app.snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import li.gkd.app.model.AttrInfo
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.DeviceInfo
import li.gkd.app.model.NodeInfo

class SnapshotNodeTreeTest {
    private fun node(id: Int, pid: Int, depth: Int, right: Int = 100) =
        NodeInfo(
            id,
            pid,
            null,
            null,
            AttrInfo(
                null,
                null,
                "android.view.View",
                null,
                null,
                false,
                false,
                false,
                null,
                false,
                false,
                true,
                0,
                0,
                right,
                100,
                right,
                100,
                0,
                0,
                depth,
            ),
        )

    private fun tree(nodes: List<NodeInfo>) =
        SnapshotNodeTree(
            ComplexSnapshot(
                1,
                "test",
                null,
                100,
                100,
                false,
                null,
                null,
                DeviceInfo("test", "test", "test", "test", 35, "15"),
                nodes,
            ),
        )

    @Test
    fun deepNonContiguousIdsExpandWithoutRecursionAndCollapseDescendants() {
        val nodes =
            (0 until 10_000).map { node(it * 3 + 7, if (it == 0) -1 else (it - 1) * 3 + 7, it) }
        val tree = tree(nodes)
        val path = tree.ancestors(nodes.last().id)
        assertEquals(9_999, path.size)
        assertEquals(10_000, tree.visibleRows(path).size)
        assertEquals(listOf(7, 10), tree.visibleRows(setOf(7)).map { it.id })
    }

    @Test
    fun hitSkipsAncestorsThatContainTheMatchedDescendant() {
        val tree = tree(listOf(node(7, -1, 0), node(20, 7, 1, 40), node(90, 20, 2, 40)))
        assertEquals(listOf(90), tree.hit(20f, 30f))
        assertEquals(listOf(7), tree.hit(60f, 30f))
        assertEquals(emptyList(), tree.hit(101f, 30f))
    }

    @Test
    fun hitSkipsLargerSiblingContainerButKeepsEqualBoundsSiblings() {
        val tree =
            tree(listOf(node(7, -1, 0), node(20, 7, 1, 80), node(90, 7, 1, 40), node(30, 7, 1, 40)))
        assertEquals(listOf(90, 30), tree.hit(20f, 30f))
    }

    @Test
    fun hitKeepsContainedNodesInSeparateBranchesAndSortsByArea() {
        val tree =
            tree(
                listOf(
                    node(7, -1, 0),
                    node(20, 7, 1),
                    node(90, 20, 2, 80),
                    node(30, 7, 1),
                    node(53, 30, 2, 40),
                ),
            )
        assertEquals(listOf(53, 90), tree.hit(20f, 30f))
    }

    @Test
    fun relationsUseImmediateSiblingsInTreeOrderAndExcludeOtherRoots() {
        val root = node(7, -1, 0)
        val before = node(90, 7, 1)
        val current = node(20, 7, 1)
        val child = node(53, 20, 2)
        val after = node(30, 7, 1)
        val otherRoot = node(5, -1, 0)
        val tree = tree(listOf(root, before, current, child, after, otherRoot))

        assertEquals(SnapshotNodeRelations(7, 90, 30, 53), tree.relations(current))
        assertEquals(SnapshotNodeRelations(7, null, 20, null), tree.relations(before))
        assertEquals(SnapshotNodeRelations(7, 20, null, null), tree.relations(after))
        assertEquals(SnapshotNodeRelations(null, null, null, 90), tree.relations(root))
        assertEquals(SnapshotNodeRelations(null, null, null, null), tree.relations(otherRoot))
    }
}
