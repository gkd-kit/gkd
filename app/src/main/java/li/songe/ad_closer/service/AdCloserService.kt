package li.songe.ad_closer.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.*
import li.songe.ad_closer.data.Rule
import li.songe.ad_closer.util.MatchRule
import li.songe.ad_closer.util.findNodeInfo


/**
 * demo: https://juejin.cn/post/6844903589127651335
 */
class AdCloserService : AccessibilityService() {

//    private fun getActivityInfo(componentName: ComponentName): ActivityInfo? {
//        return try {
//            packageManager.getActivityInfo(componentName, 0)
//        } catch (e: NameNotFoundException) {
//            null
//        }
//    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
//    override fun onDestroy() {
//        super.onDestroy()
//    }

    override fun onCreate() {
        super.onCreate()
        LogUtils.d("onCreate")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            while (true) {
                if (!this.isActive) {
                    break
                }
                val window = rootInActiveWindow

//                val packageName = window?.packageName?.toString()
//                if (packageName != null && currentPackageName != packageName) {
//                    currentPackageName = packageName
//                    LogUtils.d(currentPackageName, currentActivityClassName)
//                }
                if (window != null && ruleListMap.containsKey(currentActivityClassName)) {
                    run loop@{
                        ruleListMap[currentActivityClassName]!!.forEachIndexed { _, rule ->
                            var nodeInfo = findNodeInfo(window, rule.matchUnit, listOf(0))
                            var level = 0
                            while (nodeInfo != null && !nodeInfo.isClickable) {
                                nodeInfo = nodeInfo.parent
                                level += 1
                            }
                            if (nodeInfo != null) {
                                nodeInfo.apply {
                                    LogUtils.dTag("click", level, rule.rawText)
                                    performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                }
                            } else {
                                LogUtils.dTag("click", "not isClickable", rule.rawText)
                            }
                            return@loop

                        }
                    }
                }
                delay(200)
            }
        }
    }


    private val ruleListMap by lazy {
        val h = HashMap<String, MutableList<MatchRule>>()
        Rule.defaultRuleList.forEach {
            val key = it.className
            if (!h.containsKey(key)) {
                h[key] = mutableListOf()
            }
            try {
                h[key]?.add(MatchRule.parse(it.selector))
            } catch (e: Exception) {
                LogUtils.d(e)
            }
        }
        return@lazy h
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
//                LogUtils.d(rootInActiveWindow?.packageName, event.packageName, event.className)
//                if (event.packageName == rootInActiveWindow?.packageName && event.className != null && event.className.startsWith(
//                        event.packageName
//                    )
//                ) {
//
//                }
                val className = event.className.toString()
//                val packageName = event.packageName.toString()
//                在桌面和应用之间来回切换, 大概率导致识别失败
                if (!className.startsWith("android.") && !className.startsWith("androidx.")) {
//                    className.startsWith(packageName)
                    val rootPackageName = rootInActiveWindow?.packageName?.toString() ?: ""
                    if ((className == "com.miui.home.launcher.Launcher" && rootPackageName != "com.miui.home")) {
//                        上滑手势, 导致 活动名 不属于包名
                    } else {
                        currentActivityClassName = className
                    }
                }
            }
            else -> {
            }
        }
//        val componentName =
//            ComponentName(
//                event.packageName?.toString() ?: "",
//                event.className?.toString() ?: ""
//            )
//        val activityInfo = getActivityInfo(componentName)
//        if (activityInfo != null) {
//            val newClassName = event.className.toString()
//            if (currentActivityClassName != newClassName) {
//                currentActivityClassName = newClassName
////                LogUtils.dTag("newClassName", newClassName, rootInActiveWindow?.packageName)
//            }
//        }
//        when (event.eventType) {
//            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
//            }
//            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
//            }
//            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
//
//            }
//            else -> {
//            }
//        }
    }

    private var currentActivityClassName = ""
        set(value) {
            if (field != value) {
                field = value
                val packageName = rootInActiveWindow?.packageName
                LogUtils.dTag(
                    "updateClassName",
                    field,
                    packageName,
                )
            }
        }

    override fun onInterrupt() {
        scope.cancel()
//        val invok = {a: Int, b: Int->a+b}

    }


}
//typealias Test = (a: Int, b: Int) -> Int
//typealias Test = (a: Int, ) -> Int

//fun invok(a: Int, b: Int = 0){
//
//}