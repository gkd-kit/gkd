package li.songe.node_selector.wrapper

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.node_selector.getDepth
import li.songe.node_selector.getIndex
import li.songe.node_selector.selector.PropertySelector

data class PropertySelectorWrapper(
    private val propertySelector: PropertySelector,
    val to: CombinatorSelectorWrapper? = null
) {
    override fun toString(): String {
        return (if (to != null) {
            to.toString() + "\u0020"
        } else {
            ""
        }) + propertySelector.toString()
    }

    fun match(nodeInfo: AccessibilityNodeInfo): Boolean {
        val className = nodeInfo.className.toString()
        if (!((className.endsWith(propertySelector.name) && className[className.length - propertySelector.name.length - 1] == '.')
                    || className == propertySelector.name
                    )
        ) {
            return false
        }

        val index by lazy { nodeInfo.getIndex() }
        val childCount by lazy { nodeInfo.childCount }
        val depth by lazy { nodeInfo.getDepth() }
        val id by lazy { nodeInfo.viewIdResourceName?.toString() }
        val text by lazy { nodeInfo.text?.toString() }
//        val text = nodeInfo.text?.toString() ?: ""
        val hintText by lazy { nodeInfo.hintText?.toString() }
        val contentDescription by lazy { nodeInfo.contentDescription?.toString() }
        val isPassword by lazy { nodeInfo.isPassword }
//        val rect = Rect()
//        nodeInfo.getBoundsInScreen(rect)
        val isChecked by lazy { nodeInfo.isChecked }

        propertySelector.expressionList.forEach { expression ->
            val nodeValue: Any? = when (expression.name) {
                "index" -> index
                "childCount" -> childCount
                "depth" -> depth
                "id" -> id
                "text" -> text
                "text.length" -> text?.length
                "hintText" -> hintText
                "hintText.length" -> hintText?.length
                "contentDescription" -> contentDescription
                "contentDescription.length" -> contentDescription?.length
                "isPassword" -> isPassword
                "isChecked" -> isChecked
                else -> return false
            }
            if (!expression.compare(nodeValue)) {
                return false
            }
        }
        if (to == null) {
            return true
        }
        return to.match(nodeInfo)
    }


}
