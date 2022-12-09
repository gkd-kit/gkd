package li.songe.selector.wrapper

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.selector.PropertySelector

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

    fun match(
        nodeInfo: AccessibilityNodeInfo,
        trackList: MutableList<AccessibilityNodeInfo?>
    ): Boolean {
        if (propertySelector.name != "*" || propertySelector.name.isNotEmpty()) {
            val className = nodeInfo.className.toString()
            if (!((className.endsWith(propertySelector.name) &&
                        className[className.length - propertySelector.name.length - 1] == '.')
                        || className == propertySelector.name
                        )
            ) {
                return false
            }
        }
        propertySelector.expressionList.forEach { expression ->
            if (!expression.matchNodeInfo(nodeInfo)) {
                return false
            }
        }
//        属性匹配单元 完全匹配 之后

        if (propertySelector.match || trackList.isEmpty()) {
            trackList.add(nodeInfo)
        } else {
            trackList.add(null)
        }
        if (to == null) {
            return true
        }
        return to.match(nodeInfo, trackList)
    }


}
