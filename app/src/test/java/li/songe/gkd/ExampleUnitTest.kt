package li.songe.gkd

import kotlinx.serialization.decodeFromString
import li.songe.gkd.debug.Snapshot
import li.songe.gkd.util.Singleton
import li.songe.selector_core.NodeExt
import li.songe.selector_core.Selector
import org.junit.Test
import java.io.File
import li.songe.gkd.debug.NodeSnapshot as ApiNode

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun check_selector() {
//        println(Selector.parse("X View >n Text > Button[a=1][b=false][c=null][d!=`hello`] + A - X < Z"))
//        println(Selector.parse("A[a=1][a!=3][a*=3][a!*=3][a^=null]"))
//        println(Selector.parse("@LinearLayout > TextView[id=`com.byted.pangle:id/tt_item_tv`][text=`不感兴趣`]"))

        val s1 =
            "TextView[text$=`的广告`] - Image[id=null]" to "D:/User/Downloads/edge/snapshot-1684652764082/snapshot.json"

        val selector = Selector.parse(s1.first)
        val nodes =
            Singleton.json.decodeFromString<Snapshot>(File(s1.second).readText()).nodes
                ?: emptyList()

        val simpleNodes = nodes.map { n ->
            SimpleNode(
                value = n
            )
        }
        simpleNodes.forEach { simpleNode ->
            simpleNode.parent = simpleNodes.getOrNull(simpleNode.value.pid)?.apply {
                children.add(simpleNode)
            }
        }
        val rootWrapper = simpleNodes.map { SimpleNodeWrapper(it) }[0]
        println(rootWrapper.querySelector(selector))
    }

    class SimpleNode(
        var parent: SimpleNode? = null,
        val children: MutableList<SimpleNode> = mutableListOf(),
        val value: ApiNode
    ) {
        override fun toString(): String {
            return value.toString()
        }
    }

    data class SimpleNodeWrapper(val value: SimpleNode) : NodeExt {

        override val parent: NodeExt?
            get() = value.parent?.let { SimpleNodeWrapper(it) }
        override val children: Sequence<NodeExt?>
            get() = sequence {
                value.children.forEach { yield(SimpleNodeWrapper(it)) }
            }
        override val name: CharSequence
            get() = value.value.attr?.name ?: ""

        override fun attr(name: String): Any? {
            val attr = value.value.attr
            return when (name) {
                "id" -> attr?.id
                "name" -> attr?.name
                "text" -> attr?.text
                "textLen" -> attr?.text?.length
                "desc" -> attr?.desc
                "descLen" -> attr?.desc?.length
                "isClickable" -> attr?.isClickable
                "isChecked" -> null
                "index" -> {
                    val children = value.parent?.children ?: return null
                    children.forEachIndexed { index, simpleNode ->
                        if (simpleNode as SimpleNodeWrapper == this) {
                            return index
                        }
                    }
                    return null
                }

                "_id" -> value.value.id
                "_pid" -> value.value.pid
                else -> null
            }
        }
    }
}