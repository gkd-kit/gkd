package li.songe.selector.wrapper

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.selector.PropertySelector

data class PropertySelectorWrapper(
    private val propertySelector: PropertySelector,
    val to: CombinatorSelectorWrapper? = null,
) {
    override fun toString(): String {
        return (if (to != null) {
            to.toString() + "\u0020"
        } else {
            ""
        }) + propertySelector.toString()
    }

    fun match(
        nodeInfo: AccessibilityNodeInfo,
        trackNodes: MutableList<AccessibilityNodeInfo?> = mutableListOf(),
    ): List<AccessibilityNodeInfo?>? {

        if (propertySelector.needMatchName) {
            val className = nodeInfo.className.toString()
            if (!((className.endsWith(propertySelector.name) &&
                        className[className.length - propertySelector.name.length - 1] == '.')
                        || className == propertySelector.name
                        )
            ) {
                return null
            }
        }

//        属性匹配单元 完全匹配
        propertySelector.expressionList.forEach { expression ->
            if (!expression.matchNodeInfo(nodeInfo)) {
                return null
            }
        }

        if (propertySelector.match || trackNodes.isEmpty()) {
            trackNodes.add(nodeInfo)
        } else {
            trackNodes.add(null)
        }
        if (to == null) {
            return trackNodes
        }
        return to.match(nodeInfo, trackNodes)
    }


}
