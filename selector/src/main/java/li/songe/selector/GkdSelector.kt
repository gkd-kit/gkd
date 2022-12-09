package li.songe.selector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.parser.Transform
import li.songe.selector.wrapper.PropertySelectorWrapper

data class GkdSelector(val wrapper: PropertySelectorWrapper) {

    override fun toString() = wrapper.toString()

    fun collect(nodeInfo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val trackList: MutableList<AccessibilityNodeInfo?> = mutableListOf()
        nodeInfo.traverseAll {
            val match = wrapper.match(it, trackList)
            if (!match) {
                trackList.clear()
            }
            return@traverseAll match
        }
        return trackList.findLast { it != null }
    }


    fun click(nodeInfo: AccessibilityNodeInfo, service: AccessibilityService) = when {
        nodeInfo.isClickable -> {
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            "self"
        }
        else -> {
            val react = Rect()
            nodeInfo.getBoundsInScreen(react)
            val x = react.left + 50f / 100f * (react.right - react.left)
            val y = react.top + 50f / 100f * (react.bottom - react.top)
            val gestureDescription = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x, y)
            gestureDescription.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            service.dispatchGesture(gestureDescription.build(), null, null)
            "(50%, 50%)"
        }

    }

    companion object {
        val gkdSelectorParser = Transform.gkdSelectorParser
    }
}
