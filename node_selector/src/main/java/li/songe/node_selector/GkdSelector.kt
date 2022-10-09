package li.songe.node_selector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.node_selector.parser.Tools
import li.songe.node_selector.selector.Position
import li.songe.node_selector.wrapper.PropertySelectorWrapper
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class GkdSelector(val wrapper: PropertySelectorWrapper, val position: Position?) {

    override fun toString() = wrapper.toString() + (position ?: "").toString()
    fun collect(nodeInfo: AccessibilityNodeInfo) = nodeInfo.traverseAll { wrapper.match(it) }
    fun collectParallel(nodeInfo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var resultNodeInfo: AccessibilityNodeInfo? = null
        nodeInfo.traverseAll { child ->
            taskList.add(
                executorService.submit<Unit> {
                    if (resultNodeInfo != null) {
                        return@submit
                    }
                    if (wrapper.match(child)) {
                        resultNodeInfo = child
                    }
                }
            )
            resultNodeInfo != null
        }
        if (resultNodeInfo != null) {
            return resultNodeInfo
        }
        taskList.forEach { task -> task.get() }
        taskList.clear()
        return resultNodeInfo
    }

    fun click(nodeInfo: AccessibilityNodeInfo, service: AccessibilityService) = when {
        position != null -> {
            val react = Rect()
            nodeInfo.getBoundsInScreen(react)
            val x = react.left + position.x.number.toFloat() / 100f * (react.right - react.left)
            val y = react.top + position.x.number.toFloat() / 100f * (react.bottom - react.top)
            val gestureDescription = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x, y)
            gestureDescription.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            service.dispatchGesture(gestureDescription.build(), null, null)
            position.toString()
        }
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
        val gkdSelectorParser = Tools.gkdSelectorParser

        private val executorService by lazy {
            Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
            )
        }

        private val taskList = mutableListOf<Future<Unit>>()

    }
}
