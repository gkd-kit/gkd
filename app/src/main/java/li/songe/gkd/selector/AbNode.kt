package li.songe.gkd.selector

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector_core.Node

@JvmInline
value class AbNode(val value: AccessibilityNodeInfo) : Node {
    override val parent: Node?
        get() = value.parent?.let { AbNode(it) }
    override val children: Sequence<Node?>
        get() = sequence {
            repeat(value.childCount) { i ->
                val child = value.getChild(i)
                if (child != null) {
                    yield(AbNode(child))
                } else {
                    yield(null)
                }
            }
        }

    override fun getChild(offset: Int) = value.getChild(offset)?.let { AbNode(it) }

    override val name: CharSequence
        get() = value.className

    override fun attr(name: String): Any? = when (name) {
        "id" -> value.viewIdResourceName
        "name" -> value.className
        "text" -> value.text
        "textLen" -> value.text?.length
        "desc" -> value.contentDescription
        "descLen" -> value.contentDescription?.length
        "isClickable" -> value.isClickable
        "isChecked" -> value.isChecked
        "index" -> value.getIndex()
        else -> null
    }
}

